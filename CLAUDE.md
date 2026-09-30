# VocabHelp

Personal Android app (single developer, not a Meesho service). The user saves obscure words they meet while reading, and a daily notification prompts a multiple-choice quiz scheduled with SM-2 spaced repetition.

> This repo's conventions override the Meesho org baseline: there is no `develop` branch (default branch is `main`), no CAC/ZK config, no Hilt, no mCache/mQ, and UCL is irrelevant here.

## Build & test

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # install on connected device/emulator
./gradlew testDebugUnitTest      # JVM unit tests (Sm2Test, DictionaryLookupTest, QuizQuestionFactoryTest)
./gradlew connectedDebugAndroidTest  # instrumented tests (placeholder only)
```

Single module `:app`. Package / applicationId: `com.example.vocabhelp`. minSdk 24, target/compileSdk 36, JVM 17. Dependency versions live in `gradle/libs.versions.toml` (pin exact versions; no `+`).

## Stack

Kotlin 2.0 · Jetpack Compose + Material3 · Navigation-Compose with type-safe `@Serializable` routes · Room (KSP) · Retrofit + kotlinx.serialization · WorkManager · DataStore Preferences · Coroutines/Flow.

**No DI framework.** Manual DI through `AppContainer` in `VocabHelpApp.kt`. ViewModels are built with the `appViewModel { container -> ... }` helper in `ui/common/ViewModelFactory.kt`. Workers reach the container through `applicationContext.appContainer()`.

## Architecture (package map)

```
VocabHelpApp.kt        Application + AppContainer (OkHttp, Retrofit, Room, repos, WorkScheduler)
MainActivity.kt        Single activity; turns the EXTRA_OPEN_QUIZ intent extra into a nav signal
data/db/               Room: WordEntity (table `words`), WordDao, Converters, VocabDatabase (v2)
data/remote/           WiktionaryApi (en.wiktionary.org REST definitions), DatamuseApi (definitions fallback + /sug autocomplete)
data/repo/             WordRepository (the only business-logic layer), DictionaryLookup (source fallback + HTML parsing), LookupOutcome/SaveOutcome
data/prefs/            SettingsRepository (DataStore: reminder enabled/hour/minute, notif prompt dismissed)
sm2/                   Sm2.kt: pure applySm2(state, quality). Schedule.kt: dueAtStartOfDay, millisUntilNext. Both unit-tested
quiz/                  QuizQuestionFactory: builds one card per due word (pure logic, injected Random + related-words fn)
work/                  WorkScheduler, DueReminderWorker (one-shot, re-chains itself daily), PendingLookupWorker (one-shot, needs network, retries on network failure)
notifications/         DueWordsNotifier (channel `vocab_due_words`, taps open the Quiz)
audio/                 PronunciationPlayer (plays a saved mp3 URL if present, else on-device TextToSpeech)
ui/{home,add,detail,quiz,settings}/   Screen + ViewModel per feature; ui/nav holds Routes + NavHost
```

Pattern: each screen has a `XxxViewModel` that exposes a single `StateFlow<XxxUiState>` and is collected with `collectAsStateWithLifecycle()`. Repository methods are `suspend` or return `Flow`.

## Domain model

- `WordEntity`: `word` (trimmed and lowercased, unique index), `meanings: List<MeaningEntry>` (stored as a **JSON TEXT column** by `Converters`), `status`, the SM-2 fields (`easeFactor`, `intervalDays`, `repetitions`, `nextReviewAt`, `lastReviewedAt`), plus optional `contextSentence`, `personalNote`, `phoneticAudioUrl`.
- `MeaningStatus`:
  - `RESOLVED`: the user picked one or more dictionary senses.
  - `MANUAL`: the user typed a single meaning.
  - `PENDING_LOOKUP`: saved with no meaning; `PendingLookupWorker` later fills it in with the **first** dictionary sense only.
- Pending words are excluded from quizzes, due counts and distractors.

## Key flows

1. **Search / add word** (Home is the entry point, and there is no + button):
   - The Home search bar shows saved words that match (the **Saved** section) plus Datamuse autocomplete suggestions for words not yet saved (the **Add a new word** section). The typed query itself is listed last.
   - Tapping a suggestion navigates to `Route.AddWord(word)`.
   - The keyboard's search action runs `HomeViewModel.submitQuery`: if the exact word is already saved it opens `WordDetail`, otherwise it opens `AddWord`.
   - `AddWordViewModel` auto-runs the lookup when it is given an initial word. The user multi-selects senses, or types a manual meaning on NotFound or NetworkError. `repo.save` works out the status, and a Pending result enqueues `PendingLookupWorker`.
2. **Dictionary lookup** (`DictionaryLookup`):
   - Wiktionary is tried first. Its HTML is stripped, nested `<ol>` sub-senses are cut, and only the `en` section is used.
   - If Wiktionary returns 404, returns nothing, or fails, the lookup falls back to Datamuse `words?sp=<word>&md=d`, and a Datamuse result is accepted only on an exact spelling match.
   - `NotFound` is returned only when **both** sources answered empty; otherwise the result is `NetworkError`.
   - An OkHttp interceptor sets a descriptive `User-Agent`, as the Wikimedia API policy asks.
   - dictionaryapi.dev was dropped in Sep 2026: its origin was timing out after about 20 s (Cloudflare 522).
3. **Quiz** (`QuizViewModel` + `quiz/QuizQuestionFactory`):
   - **Session:** up to 10 due words, shuffled and picked once at the start. Each next card is prefetched while the current one is being answered.
   - **Card types** (never the same type twice in a row):
     - `FLASHCARD`: reveal, then self-grade Forgot/Hard/Good/Easy, which maps to SM-2 quality 1/3/4/5.
     - `MEANING`: word → pick the definition.
     - `REVERSE`: definition → pick the word. The word and its stem are masked inside the definition.
     - `SYNONYM`: word → pick a synonym from Datamuse `ml=`. The synonym must be tagged with the sense's part of speech and must not share a 4-letter chunk with the target.
     - Multiple-choice grading: correct = 4 (recognition isn't perfect recall), wrong = 1.
   - **Decoys:**
     - Preferred source: Datamuse related words of the user's *other* saved words, filtered to the same part of speech as the tested sense.
     - Anything related to the target itself is excluded, because it could arguably be a correct answer.
     - When those run out, the builder falls back to the user's saved words, then to built-in fallback lists.
     - Offline, synonym cards are simply skipped.
   - **Definition clean-up:** `cleanDefinition` strips leading "(label)" tags and normalises capitalisation and the final full stop, so the correct answer doesn't stand out by style.
   - **Junk meanings:** definitions shorter than 12 characters get flashcard or synonym cards only.
   - **Timeout:** related-word lookups are capped at 2.5 s (`RELATED_TIMEOUT_MS`) and cached per session.
   - **Scheduling:** `nextReviewAt = dueAtStartOfDay(now, intervalDays)`, i.e. local midnight, so a word reviewed after the reminder is still due at the next day's reminder. New words are due from the start of today.
4. **Daily reminder** (`WorkScheduler.scheduleDailyReminder(h, m, replaceExisting)`):
   - It enqueues one-shot unique work `vocab_due_reminder_daily` with a delay until the next h:m. `DueReminderWorker` notifies if any words are due, then schedules the next day's run.
   - `SettingsViewModel` uses REPLACE. `VocabHelpApp.onCreate` uses KEEP, so launching the app doesn't disturb a pending reminder.
   - Every schedule call also cancels the legacy periodic work `vocab_due_reminder`. That 24h PeriodicWorkRequest stayed anchored to its first run's time of day and ignored setting changes.
   - To check on a device, read `no_backup/androidx.work.workdb` via `run-as`, or force a run with `adb shell cmd jobscheduler run -f com.example.vocabhelp <jobId>`. Tapping the notification opens `MainActivity` with `EXTRA_OPEN_QUIZ`, and `VocabNavHost` navigates to `Route.Quiz`. The default reminder time is 21:00, enabled.
5. **Home**: search with a 250 ms debounce (a SQL `LIKE` narrows the results, then `matchesQuery` keeps only real text matches; Datamuse suggestions are shown too), a due banner that re-queries every minute (`observeDueCount`), a notification-permission prompt card (API 33+), and long-press multi-select delete.

## Gotchas / things to know before changing code

- **The user's phone holds real saved words. Never wipe them.**
  - The destructive fallback has been removed and the schema is exported to `app/schemas/`.
  - Any `WordEntity` change must bump `version` and add a Room `Migration`. Without a migration, the app crashes on upgrade rather than wiping data.
  - Install with `adb install -r` (or `installDebug`) and never uninstall.
  - Back up the database first: the app is debuggable, so `adb exec-out run-as com.example.vocabhelp cat databases/vocab.db` works for `vocab.db`, `-wal` and `-shm`.
- `meanings` is a JSON blob. The SQL `LIKE` alone would also match JSON keys (`definition`, ...), which is why `WordRepository.observeAll` post-filters with `matchesQuery`.
- `WordDao.countDue(now)` binds `now` when the Flow is created. Use `WordRepository.observeDueCount()`, which re-binds it every minute.
- `WordRepository.resolveLookup`'s KDoc says the user "can re-pick on the detail screen later", but that UI does not exist. The detail screen can only set a manual meaning, and only for PENDING words.
- Only words saved in the dictionaryapi.dev era have `phoneticAudioUrl`. New words are always saved with a null audio URL and pronounced with text-to-speech. The manifest's `<queries>` entry for `TTS_SERVICE` is required on API 30+.
- **Testing the quiz on the real phone changes real SM-2 state.** Back up the database first, then restore it afterwards: `adb push` each file to `/data/local/tmp` and copy it into `databases/` with `run-as ... sh -c 'cat > ...'` while the app is force-stopped.
- The launcher icon is an adaptive vector: `drawable/ic_launcher_{foreground,background,monochrome}.xml`, an open book with a spark on indigo `#1E1B4B`. The legacy `mipmap-*/ic_launcher*.webp` files are rendered from `vocabhelp-mark.svg` (headless Chrome, then ImageMagick). ImageMagick's own SVG renderer drops strokes, so don't use it.
- The notification small icon is `drawable/ic_stat_notify`, the book-and-spark silhouette in white only.
- `AppContainer.workScheduler` and the `WorkScheduler(container)` created in `VocabHelpApp.onCreate` are two separate instances. This is harmless because both wrap `WorkManager.getInstance`.
- Strings are hardcoded in Kotlin rather than kept in `strings.xml`. Match this when editing existing screens.

## Conventions

- Match the existing style: trailing commas, 4-space indent, `private companion object` for constants, KDoc only where the intent is non-obvious.
- Keep business logic in `WordRepository` or in pure functions such as `sm2/`. ViewModels orchestrate; composables stay stateless where practical.
- Add JVM unit tests under `app/src/test` for pure logic (see `Sm2Test` for the style: backtick test names, JUnit4).
- Commits: short imperative subject (e.g. "Add push notification for reminders"). Commit or push only when asked.
