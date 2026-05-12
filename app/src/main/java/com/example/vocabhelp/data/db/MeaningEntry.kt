package com.example.vocabhelp.data.db

import kotlinx.serialization.Serializable

@Serializable
data class MeaningEntry(
    val partOfSpeech: String? = null,
    val definition: String,
    val example: String? = null,
)
