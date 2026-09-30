package com.example.vocabhelp.quiz

import com.example.vocabhelp.data.db.MeaningEntry
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.data.repo.RelatedWord
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

enum class QuizType { MEANING, REVERSE, SYNONYM, FLASHCARD }

sealed interface QuizCard {
    val target: WordEntity
    val type: QuizType
}

/** Self-graded: show the word, the user recalls, reveals, then grades themselves. */
data class FlashCard(override val target: WordEntity) : QuizCard {
    override val type: QuizType get() = QuizType.FLASHCARD
}

/** Multiple choice: [prompt] is a word (MEANING, SYNONYM) or a definition (REVERSE). */
data class ChoiceCard(
    override val target: WordEntity,
    override val type: QuizType,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
) : QuizCard

/**
 * Builds one quiz card for a due word. Wrong options come, in order of preference,
 * from words related to the user's *other* saved words (real, uncommon words of
 * the same part of speech — fetched via [related]), then from the user's own saved
 * words, then from a small built-in list, so it always works offline.
 */
class QuizQuestionFactory(
    private val related: suspend (String) -> List<RelatedWord>,
    private val random: Random = Random.Default,
) {
    suspend fun build(
        target: WordEntity,
        others: List<WordEntity>,
        avoid: QuizType? = null,
    ): QuizCard {
        val sense = target.meanings.randomOrNull(random) ?: return FlashCard(target)
        val pos = normalizePos(sense.partOfSpeech)
        val seeds = decoySeeds(others, pos)
        // All lookups are independent: fetch in parallel.
        val (targetRelated, seedRelated) = coroutineScope {
            val t = async { related(target.word) }
            val d = seeds.map { async { related(it.word) } }
            t.await() to d.awaitAll().flatten()
        }
        // Anything related to the target could arguably be a right answer: never use as a decoy.
        val offLimits = targetRelated.map { it.word }.toSet() + target.word
        val decoys = seedRelated
            .filter { it.word !in offLimits && it.definitions.isNotEmpty() }
            .filter { pos == null || pos in it.partsOfSpeech }
            .distinctBy { it.word }
            .shuffled(random)

        val synonym = synonymFor(target, targetRelated, pos)
        // A junk/placeholder meaning ("rr") would make the answer obvious in definition questions.
        val usableDefinition = cleanDefinition(sense.definition).length >= MIN_DEFINITION_LENGTH
        val candidates = buildList {
            add(QuizType.FLASHCARD)
            if (usableDefinition) {
                add(QuizType.MEANING)
                add(QuizType.REVERSE)
            }
            if (synonym != null) add(QuizType.SYNONYM)
        }
        val type = candidates.filter { it != avoid }.ifEmpty { candidates }.random(random)

        return when {
            type == QuizType.MEANING -> meaningCard(target, sense, pos, others, decoys, offLimits)
            type == QuizType.REVERSE -> reverseCard(target, sense, pos, others, decoys, offLimits)
            type == QuizType.SYNONYM && synonym != null -> synonymCard(target, synonym, others, decoys, offLimits)
            else -> FlashCard(target)
        }
    }

    /** A few other saved words (preferring the target's part of speech) whose related words become decoys. */
    private fun decoySeeds(others: List<WordEntity>, pos: String?): List<WordEntity> =
        others.filter { pos == null || it.partsOfSpeech().contains(pos) }
            .ifEmpty { others }
            .shuffled(random)
            .take(DECOY_SEEDS)

    private fun meaningCard(
        target: WordEntity,
        sense: MeaningEntry,
        pos: String?,
        others: List<WordEntity>,
        decoys: List<RelatedWord>,
        offLimits: Set<String>,
    ): ChoiceCard {
        val correct = cleanDefinition(sense.definition)
        val external = decoys.mapNotNull { d ->
            (d.definitions.firstOrNull { normalizePos(it.partOfSpeech) == pos } ?: d.definitions.firstOrNull())
                ?.definition
        }
        val saved = others.filter { it.word !in offLimits }
            .sortedByDescending { pos != null && pos in it.partsOfSpeech() }
            .mapNotNull { w ->
                (w.meanings.firstOrNull { normalizePos(it.partOfSpeech) == pos } ?: w.meanings.randomOrNull(random))
                    ?.definition
            }
        val wrong = pickWrong(correct, external.take(2) + saved.take(1) + external.drop(2) + saved.drop(1) + FALLBACK_DEFINITIONS) {
            cleanDefinition(it)
        }
        return choice(target, QuizType.MEANING, prompt = target.word, correct, wrong)
    }

    private fun reverseCard(
        target: WordEntity,
        sense: MeaningEntry,
        pos: String?,
        others: List<WordEntity>,
        decoys: List<RelatedWord>,
        offLimits: Set<String>,
    ): ChoiceCard {
        val saved = others.filter { it.word !in offLimits }
            .sortedByDescending { pos != null && pos in it.partsOfSpeech() }
            .map { it.word }
        val external = decoys.map { it.word }
        val wrong = pickWrong(target.word, external.take(2) + saved.take(1) + external.drop(2) + saved.drop(1) + FALLBACK_WORDS) { it }
        val prompt = maskWord(cleanDefinition(sense.definition), target.word)
        return choice(target, QuizType.REVERSE, prompt, target.word, wrong)
    }

    private fun synonymCard(
        target: WordEntity,
        synonym: String,
        others: List<WordEntity>,
        decoys: List<RelatedWord>,
        offLimits: Set<String>,
    ): ChoiceCard {
        // Decoys are synonyms of the user's other words: sophisticated, but unrelated to the target.
        val saved = others.map { it.word }.filter { it !in offLimits }
        val wrong = pickWrong(synonym, decoys.map { it.word } + saved + FALLBACK_WORDS) { it }
        return choice(target, QuizType.SYNONYM, prompt = target.word, synonym, wrong)
    }

    /**
     * Best synonym for the target that isn't just a form of the same word ("aglow" →
     * "glowing" would give itself away). When the tested sense has a part of speech,
     * the synonym must be tagged with it: an untagged adjective offered for a noun
     * sense stands out against same-POS decoys.
     */
    private fun synonymFor(target: WordEntity, related: List<RelatedWord>, pos: String?): String? {
        return related
            .filter { !sharesChunk(it.word, target.word, SHARED_CHUNK_LENGTH) }
            .filter { pos == null || pos in it.partsOfSpeech }
            .sortedByDescending { it.isSynonym }
            .firstOrNull()
            ?.word
    }

    private fun pickWrong(correct: String, pool: List<String>, clean: (String) -> String): List<String> {
        val seen = mutableSetOf(correct.lowercase())
        return pool.asSequence()
            .map(clean)
            .filter { it.isNotBlank() && seen.add(it.lowercase()) }
            .take(OPTION_COUNT - 1)
            .toList()
    }

    private fun choice(target: WordEntity, type: QuizType, prompt: String, correct: String, wrong: List<String>): ChoiceCard {
        val options = (wrong + correct).shuffled(random)
        return ChoiceCard(target, type, prompt, options, options.indexOf(correct))
    }

    private companion object {
        const val OPTION_COUNT = 4
        const val DECOY_SEEDS = 3
        const val SHARED_CHUNK_LENGTH = 4
        const val MIN_DEFINITION_LENGTH = 12

        val FALLBACK_DEFINITIONS = listOf(
            "A small alcove or recess.",
            "A sharp criticism.",
            "A state of tranquility.",
            "A long, narrow strip of land.",
            "A feeling of intense longing.",
            "A brief, witty remark.",
        )
        val FALLBACK_WORDS = listOf("reticent", "ephemeral", "obdurate", "lachrymose", "perfidy", "sanguine")
    }
}

/** True if the words share any run of [length] letters ("aglow"/"glowing" share "glow"). */
internal fun sharesChunk(a: String, b: String, length: Int = 4): Boolean {
    if (a.length < length || b.length < length) return a.contains(b) || b.contains(a)
    return (0..a.length - length).any { i -> b.contains(a.substring(i, i + length)) }
}

/** Maps any part-of-speech label ("Noun", "proper noun", "adj", ...) to noun/verb/adjective/adverb. */
internal fun normalizePos(raw: String?): String? {
    val p = raw?.trim()?.lowercase() ?: return null
    return when {
        p.contains("adverb") || p == "adv" -> "adverb"
        p.contains("adjective") || p == "adj" -> "adjective"
        p.contains("noun") || p == "n" -> "noun"
        p.contains("verb") || p == "v" -> "verb"
        else -> null
    }
}

private fun WordEntity.partsOfSpeech(): Set<String> = meanings.mapNotNull { normalizePos(it.partOfSpeech) }.toSet()

private val LEADING_LABELS = Regex("^(\\([^)]*\\)\\s*)+")

/**
 * Normalises definitions from different sources so the correct answer doesn't
 * stand out by style: drops leading usage labels like "(derogatory)",
 * capitalises the first letter and ends with a full stop.
 */
internal fun cleanDefinition(text: String): String {
    val core = LEADING_LABELS.replace(text.trim(), "").trim().replaceFirstChar { it.uppercaseChar() }
    return if (core.isNotEmpty() && core.last().isLetterOrDigit()) "$core." else core
}

/**
 * Hides the word and its relatives inside its own definition. The stem is ~60% of
 * the word so "grotesquerie" also hides "grotesque", while short words keep 4 letters.
 */
internal fun maskWord(definition: String, word: String): String {
    val stem = Regex.escape(word.take(maxOf(4, word.length * 3 / 5)))
    return Regex("\\b$stem\\w*", RegexOption.IGNORE_CASE).replace(definition, "____")
}
