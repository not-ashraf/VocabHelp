package com.example.vocabhelp.data.repo

import com.example.vocabhelp.data.db.MeaningEntry
import com.example.vocabhelp.data.db.MeaningStatus
import com.example.vocabhelp.data.db.WordEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchesQueryTest {

    private val crone = WordEntity(
        word = "crone",
        meanings = listOf(MeaningEntry(partOfSpeech = "noun", definition = "An old woman.", example = "The crone cackled.")),
        status = MeaningStatus.RESOLVED,
        nextReviewAt = 0,
        createdAt = 0,
    )

    @Test
    fun `JSON field names no longer match every word`() {
        // The meanings column is stored as [{"partOfSpeech":..,"definition":..,"example":..}].
        listOf("definition", "example", "partOfSpeech", "\":\"").forEach {
            assertFalse(it, crone.matchesQuery(it))
        }
    }

    @Test
    fun `word, definition, example and part of speech still match case-insensitively`() {
        listOf("CRO", "old wom", "cackled", "noun", "").forEach {
            assertTrue(it, crone.matchesQuery(it))
        }
    }
}
