package com.example.vocabhelp.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface DictionaryApi {
    @GET("api/v2/entries/en/{word}")
    suspend fun lookup(@Path("word") word: String): List<DictionaryEntryDto>

    companion object {
        const val BASE_URL = "https://api.dictionaryapi.dev/"
    }
}
