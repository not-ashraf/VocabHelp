package com.example.vocabhelp.data.repo

import com.example.vocabhelp.data.db.MeaningStatus
import com.example.vocabhelp.data.db.WordDao
import com.example.vocabhelp.data.db.WordEntity
import com.example.vocabhelp.data.remote.DictionaryApi
import com.example.vocabhelp.data.remote.DictionaryEntryDto
import com.example.vocabhelp.sm2.Sm2State
import com.example.vocabhelp.sm2.applySm2
import kotlinx.coroutines.flow.Flow

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

    /**
     * Inserts the word as PENDING_LOOKUP, then attempts a network lookup.
     * Returns Result.success(id) when the row is saved (regardless of lookup outcome),
     * Result.failure when a duplicate exists or input is empty.
     */
    suspend fun saveAndLookup(
        word: String,
        contextSentence: String?,
        personalNote: String?,
    ): SaveOutcome {
        val normalized = word.trim().lowercase()
        if (normalized.isEmpty()) return SaveOutcome.Empty
        dao.getByWord(normalized)?.let { return SaveOutcome.Duplicate(it.id) }

        val now = System.currentTimeMillis()
        val id = dao.insert(
            WordEntity(
                word = normalized,
                meaning = null,
                contextSentence = contextSentence?.takeIf { it.isNotBlank() }?.trim(),
                personalNote = personalNote?.takeIf { it.isNotBlank() }?.trim(),
                status = MeaningStatus.PENDING_LOOKUP,
                nextReviewAt = now,
                createdAt = now,
            ),
        )
        if (id == -1L) {
            // Race: another insert won. Re-fetch and return its id.
            val existing = dao.getByWord(normalized)
            return existing?.let { SaveOutcome.Duplicate(it.id) } ?: SaveOutcome.Empty
        }

        val resolved = runCatching { resolveLookup(id) }.getOrDefault(false)
        return if (resolved) SaveOutcome.Resolved(id) else SaveOutcome.Pending(id)
    }

    /** Attempts a network lookup for an existing pending row. Returns true if resolved. */
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
                meaning = flat.meaning,
                partOfSpeech = flat.partOfSpeech,
                example = flat.example ?: existing.example,
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
        dao.update(w.copy(meaning = trimmed, status = MeaningStatus.MANUAL))
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

    suspend fun delete(word: WordEntity) = dao.delete(word)

    private data class FlattenedDef(
        val meaning: String,
        val partOfSpeech: String?,
        val example: String?,
        val audio: String?,
    )

    private fun flatten(entries: List<DictionaryEntryDto>): FlattenedDef? {
        val firstMeaning = entries.firstNotNullOfOrNull { entry ->
            entry.meanings.firstOrNull { it.definitions.isNotEmpty() }
        } ?: return null
        val firstDef = firstMeaning.definitions.first()
        val audio = entries
            .flatMap { it.phonetics }
            .firstOrNull { !it.audio.isNullOrBlank() }
            ?.audio
        return FlattenedDef(
            meaning = firstDef.definition,
            partOfSpeech = firstMeaning.partOfSpeech,
            example = firstDef.example,
            audio = audio,
        )
    }
}

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
