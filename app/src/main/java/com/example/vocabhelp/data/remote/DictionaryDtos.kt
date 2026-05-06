package com.example.vocabhelp.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class DictionaryEntryDto(
    val word: String,
    val phonetic: String? = null,
    val phonetics: List<PhoneticDto> = emptyList(),
    val meanings: List<MeaningDto> = emptyList(),
)

@Serializable
data class PhoneticDto(
    val text: String? = null,
    val audio: String? = null,
)

@Serializable
data class MeaningDto(
    val partOfSpeech: String? = null,
    val definitions: List<DefinitionDto> = emptyList(),
)

@Serializable
data class DefinitionDto(
    val definition: String,
    val example: String? = null,
)
