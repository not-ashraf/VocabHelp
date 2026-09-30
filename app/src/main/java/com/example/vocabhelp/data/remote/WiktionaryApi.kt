package com.example.vocabhelp.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Wikimedia REST definition endpoint. Lookups are case-sensitive and return
 * 404 for unknown words. Definition and example strings are HTML fragments.
 */
interface WiktionaryApi {
    /** Keyed by language code ("en", "fr", ...). */
    @GET("api/rest_v1/page/definition/{word}")
    suspend fun definitions(@Path("word") word: String): Map<String, List<WiktionaryUsageDto>>

    companion object {
        const val BASE_URL = "https://en.wiktionary.org/"
    }
}

@Serializable
data class WiktionaryUsageDto(
    val partOfSpeech: String? = null,
    val definitions: List<WiktionaryDefinitionDto> = emptyList(),
)

@Serializable
data class WiktionaryDefinitionDto(
    val definition: String = "",
    val examples: List<String> = emptyList(),
)
