package com.example.vocabhelp.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** https://www.datamuse.com/api/ — free, no key. Unknown words return `[]`, not 404. */
interface DatamuseApi {
    /** Exact-spelling lookup with definitions (`md=d`). */
    @GET("words")
    suspend fun define(
        @Query("sp") word: String,
        @Query("md") metadata: String = "d",
        @Query("max") max: Int = 1,
    ): List<DatamuseWordDto>

    /** Autocomplete: words that start with (or are spelled close to) [prefix]. */
    @GET("sug")
    suspend fun suggest(
        @Query("s") prefix: String,
        @Query("max") max: Int = 8,
    ): List<DatamuseWordDto>

    companion object {
        const val BASE_URL = "https://api.datamuse.com/"
    }
}

@Serializable
data class DatamuseWordDto(
    val word: String,
    /** Each entry is "<pos>\t<definition>", e.g. "n\tA furtive sneaking motion. ". */
    val defs: List<String> = emptyList(),
)
