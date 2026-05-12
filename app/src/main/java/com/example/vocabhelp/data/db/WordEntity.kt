package com.example.vocabhelp.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    indices = [
        Index(value = ["word"], unique = true),
        Index("nextReviewAt"),
        Index("status"),
    ],
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val meanings: List<MeaningEntry> = emptyList(),
    val phoneticAudioUrl: String? = null,
    val contextSentence: String? = null,
    val personalNote: String? = null,
    val status: MeaningStatus,
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val nextReviewAt: Long,
    val lastReviewedAt: Long? = null,
    val createdAt: Long,
)
