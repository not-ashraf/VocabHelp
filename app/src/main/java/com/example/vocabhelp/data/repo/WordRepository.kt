package com.example.vocabhelp.data.repo

import com.example.vocabhelp.data.db.MeaningEntry
import com.example.vocabhelp.data.db.MeaningStatus
import com.example.vocabhelp.data.db.WordDao
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.data.remote.DictionaryApi
import com.example.vocabhelp.data.remote.DictionaryEntryDto
import com.example.vocabhelp.sm2.Sm2State
import com.example.vocabhelp.sm2.applySm2
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException

class WordRepository(
    private val dao: WordDao,
    private val api: DictionaryApi,
) {
    fun observeAll(query: String): Flow<List<WordEntity>> = dao.observeAll(query)
    fun observeDue(now: Long = System.currentTimeMillis()): Flow<List<WordEntity>> = dao.observeDue(now)
    fun countDue(now: Long = System.currentTimeMillis()): Flow<Int> = dao.countDue(now)
    fun observeById(id: Long): Flow<WordEntity?> = dao.observeById(id)
    fun observePending(): Flow<List<WordEntity>> = dao.observePending()

    suspend fun getById(id: Long): WordEntity? = dao.getById(id)

    suspend fun findByWord(word: String): WordEntity? =
        dao.getByWord(word.trim().lowercase())

    /** Hits the dictionary API and returns ALL senses without writing to the DB. */
    suspend fun lookup(word: String): LookupOutcome {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return LookupOutcome.NotFound
        return try {
            val entries = api.lookup(normalized)
            val options = entries.flatMap { entry ->
                entry.meanings.flatMap { meaning ->
                    meaning.definitions.map { def ->
                        DefinitionOption(
                            partOfSpeech = meaning.partOfSpeech,
                            definition = def.definition,
                            example = def.example,
                        )
                    }
                }
            }
            val audio = entries.flatMap { it.phonetics }
                .firstOrNull { !it.audio.isNullOrBlank() }
                ?.audio
            if (options.isEmpty()) LookupOutcome.NotFound else LookupOutcome.Success(options, audio)
        } catch (e: HttpException) {
            if (e.code() == 404) LookupOutcome.NotFound else LookupOutcome.NetworkError
        } catch (_: Exception) {
            LookupOutcome.NetworkError
        }
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
                nextReviewAt = now,
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
    suspend fun resolveLookup(id: Long): Boolean {
        val existing = dao.getById(id) ?: return false
        if (existing.status != MeaningStatus.PENDING_LOOKUP) return true
        val flat = try {
            flatten(api.lookup(existing.word))
        } catch (_: Exception) {
            null
        } ?: return false
        dao.update(
            existing.copy(
                meanings = listOf(flat.entry),
                phoneticAudioUrl = flat.audio ?: existing.phoneticAudioUrl,
                status = MeaningStatus.RESOLVED,
            ),
        )
        return true
    }

    suspend fun retryAllPending(): Int {
        var resolved = 0
        dao.getPendingOnce().forEach { if (resolveLookup(it.id)) resolved++ }
        return resolved
    }

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
        val nextReview = now + next.intervalDays.toLong() * 24L * 60L * 60L * 1000L
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

    suspend fun pickQuizQuestion(now: Long = System.currentTimeMillis()): QuizQuestion? {
        val due = dao.getDueOnce(now).firstOrNull() ?: return null
        val distractors = dao.getRandomDistractors(due.id, limit = 3)
        return QuizQuestion(due, distractors)
    }

    suspend fun countDueNow(now: Long = System.currentTimeMillis()): Int =
        dao.getDueOnce(now).size

    suspend fun delete(word: WordEntity) = dao.delete(word)

    suspend fun deleteByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids.toList())
    }

    private data class FlattenedDef(val entry: MeaningEntry, val audio: String?)

    private fun flatten(entries: List<DictionaryEntryDto>): FlattenedDef? {
        val firstMeaning = entries.firstNotNullOfOrNull { entry ->
            entry.meanings.firstOrNull { it.definitions.isNotEmpty() }
        } ?: return null
        val firstDef = firstMeaning.definitions.first()
        val audio = entries.flatMap { it.phonetics }
            .firstOrNull { !it.audio.isNullOrBlank() }
            ?.audio
        return FlattenedDef(
            entry = MeaningEntry(
                partOfSpeech = firstMeaning.partOfSpeech,
                definition = firstDef.definition,
                example = firstDef.example,
            ),
            audio = audio,
        )
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

data class QuizQuestion(
    val target: WordEntity,
    val distractors: List<WordEntity>,
)
