package com.example.vocabhelp.data.repo

import com.example.vocabhelp.data.db.MeaningEntry
import com.example.vocabhelp.data.db.MeaningStatus
import com.example.vocabhelp.data.db.WordDao
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.sm2.Sm2State
import com.example.vocabhelp.sm2.applySm2
import com.example.vocabhelp.sm2.dueAtStartOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class WordRepository(
    private val dao: WordDao,
    private val dictionary: DictionaryLookup,
) {
    /**
     * SQL narrows by `LIKE` over the word and the raw meanings JSON; the Kotlin
     * filter then drops rows that only matched JSON keys ("definition", ...).
     */
    fun observeAll(query: String): Flow<List<WordEntity>> =
        dao.observeAll(query).map { rows -> rows.filter { it.matchesQuery(query) } }

    /**
     * Due count that stays current while observed: re-queries every minute, so
     * words that fall due while the app is open (and a changed clock) show up.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDueCount(): Flow<Int> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(DUE_REFRESH_MS)
        }
    }.flatMapLatest { now -> dao.countDue(now) }.distinctUntilChanged()
    fun observeById(id: Long): Flow<WordEntity?> = dao.observeById(id)
    fun observePending(): Flow<List<WordEntity>> = dao.observePending()

    suspend fun getById(id: Long): WordEntity? = dao.getById(id)

    suspend fun findByWord(word: String): WordEntity? =
        dao.getByWord(word.trim().lowercase())

    /** Hits the dictionaries and returns ALL senses without writing to the DB. */
    suspend fun lookup(word: String): LookupOutcome = dictionary.lookup(word)

    /**
     * Autocomplete candidates for a partially typed word that are NOT already
     * saved; empty when offline.
     */
    suspend fun suggestNewWords(prefix: String): List<String> {
        val candidates = dictionary.suggest(prefix).map { it.trim().lowercase() }.distinct()
        if (candidates.isEmpty()) return emptyList()
        val saved = dao.getExistingWords(candidates).toSet()
        return candidates.filterNot { it in saved }
    }

    /**
     * Persists a new word. Status is inferred from inputs:
     *   selectedOptions non-empty   -> RESOLVED
     *   manualMeaning non-blank     -> MANUAL  (single-element meanings list)
     *   otherwise                   -> PENDING_LOOKUP (empty meanings list)
     */
    suspend fun save(
        word: String,
        selectedOptions: List<DefinitionOption>,
        audioUrl: String?,
        manualMeaning: String?,
        contextSentence: String?,
        personalNote: String?,
    ): SaveOutcome {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return SaveOutcome.Empty
        dao.getByWord(normalized)?.let { return SaveOutcome.Duplicate(it.id) }

        val meanings: List<MeaningEntry>
        val status: MeaningStatus
        when {
            selectedOptions.isNotEmpty() -> {
                meanings = selectedOptions.map { it.toMeaningEntry() }
                status = MeaningStatus.RESOLVED
            }
            !manualMeaning.isNullOrBlank() -> {
                meanings = listOf(MeaningEntry(definition = manualMeaning.trim()))
                status = MeaningStatus.MANUAL
            }
            else -> {
                meanings = emptyList()
                status = MeaningStatus.PENDING_LOOKUP
            }
        }

        val now = System.currentTimeMillis()
        val id = dao.insert(
            WordEntity(
                word = normalized,
                meanings = meanings,
                phoneticAudioUrl = audioUrl,
                contextSentence = contextSentence?.takeIf { it.isNotBlank() }?.trim(),
                personalNote = personalNote?.takeIf { it.isNotBlank() }?.trim(),
                status = status,
                // Start of today: due immediately, even for queries whose `now` predates the save.
                nextReviewAt = dueAtStartOfDay(now, intervalDays = 0),
                createdAt = now,
            ),
        )
        if (id == -1L) {
            val existing = dao.getByWord(normalized)
            return existing?.let { SaveOutcome.Duplicate(it.id) } ?: SaveOutcome.Empty
        }
        return when (status) {
            MeaningStatus.PENDING_LOOKUP -> SaveOutcome.Pending(id)
            MeaningStatus.RESOLVED, MeaningStatus.MANUAL -> SaveOutcome.Resolved(id)
        }
    }

    /**
     * Attempts a network lookup for an existing pending row. Best-effort:
     * we still pick only the first sense automatically because the user isn't
     * present to choose. They can re-pick on the detail screen later.
     */
    suspend fun resolveLookup(id: Long): ResolveResult {
        val existing = dao.getById(id) ?: return ResolveResult.NOT_FOUND
        if (existing.status != MeaningStatus.PENDING_LOOKUP) return ResolveResult.RESOLVED
        val found = when (val outcome = dictionary.lookup(existing.word)) {
            is LookupOutcome.Success -> outcome
            LookupOutcome.NotFound -> return ResolveResult.NOT_FOUND
            LookupOutcome.NetworkError -> return ResolveResult.FAILED
        }
        dao.update(
            existing.copy(
                meanings = listOf(found.options.first().toMeaningEntry()),
                phoneticAudioUrl = found.audioUrl ?: existing.phoneticAudioUrl,
                status = MeaningStatus.RESOLVED,
            ),
        )
        return ResolveResult.RESOLVED
    }

    /** Tries every pending word; returns how many failed on the network (worth retrying). */
    suspend fun retryAllPending(): Int =
        dao.getPendingOnce().count { resolveLookup(it.id) == ResolveResult.FAILED }

    suspend fun setManualMeaning(id: Long, meaning: String) {
        val w = dao.getById(id) ?: return
        val trimmed = meaning.trim()
        if (trimmed.isEmpty()) return
        dao.update(
            w.copy(
                meanings = listOf(MeaningEntry(definition = trimmed)),
                status = MeaningStatus.MANUAL,
            ),
        )
    }

    suspend fun updateUserFields(id: Long, contextSentence: String?, personalNote: String?) {
        val w = dao.getById(id) ?: return
        dao.update(
            w.copy(
                contextSentence = contextSentence?.takeIf { it.isNotBlank() }?.trim(),
                personalNote = personalNote?.takeIf { it.isNotBlank() }?.trim(),
            ),
        )
    }

    suspend fun applyReview(id: Long, quality: Int) {
        val w = dao.getById(id) ?: return
        val prev = Sm2State(w.easeFactor, w.intervalDays, w.repetitions)
        val next = applySm2(prev, quality)
        val now = System.currentTimeMillis()
        val nextReview = dueAtStartOfDay(now, next.intervalDays)
        dao.update(
            w.copy(
                easeFactor = next.ef,
                intervalDays = next.intervalDays,
                repetitions = next.repetitions,
                nextReviewAt = nextReview,
                lastReviewedAt = now,
            ),
        )
    }

    suspend fun getDueWords(now: Long = System.currentTimeMillis()): List<WordEntity> = dao.getDueOnce(now)

    /** Every word with a meaning; the source of the user's-own-words quiz options. */
    suspend fun getReviewableWords(): List<WordEntity> = dao.getReviewable()

    /** Words related in meaning (synonyms, decoys) for building quiz questions; empty offline. */
    suspend fun related(word: String): List<RelatedWord> = dictionary.related(word)

    suspend fun countDueNow(now: Long = System.currentTimeMillis()): Int =
        dao.getDueOnce(now).size

    suspend fun delete(word: WordEntity) = dao.delete(word)

    suspend fun deleteByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids.toList())
    }
}

private const val DUE_REFRESH_MS = 60_000L

enum class ResolveResult { RESOLVED, NOT_FOUND, FAILED }

/** Case-insensitive match on the word or any saved sense's text (never the JSON field names). */
internal fun WordEntity.matchesQuery(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return word.contains(q, ignoreCase = true) || meanings.any { m ->
        m.definition.contains(q, ignoreCase = true) ||
            m.example?.contains(q, ignoreCase = true) == true ||
            m.partOfSpeech?.contains(q, ignoreCase = true) == true
    }
}

private fun DefinitionOption.toMeaningEntry(): MeaningEntry = MeaningEntry(
    partOfSpeech = partOfSpeech,
    definition = definition,
    example = example,
)

sealed interface SaveOutcome {
    data class Resolved(val id: Long) : SaveOutcome
    data class Pending(val id: Long) : SaveOutcome
    data class Duplicate(val id: Long) : SaveOutcome
    data object Empty : SaveOutcome
}
