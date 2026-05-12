package com.example.vocabhelp.data.repo

/** A single sense of a word, suitable for display + selection in the UI. */
data class DefinitionOption(
    val partOfSpeech: String?,
    val definition: String,
    val example: String?,
)

sealed interface LookupOutcome {
    data class Success(
        val options: List<DefinitionOption>,
        val audioUrl: String?,
    ) : LookupOutcome

    /** Word not in dictionary (404 or empty results). */
    data object NotFound : LookupOutcome

    /** Network or server problem; the user can retry. */
    data object NetworkError : LookupOutcome
}
