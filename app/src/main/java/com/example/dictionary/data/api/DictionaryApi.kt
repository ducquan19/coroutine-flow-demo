package com.example.dictionary.data.api

import com.example.dictionary.data.model.DictionaryResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface DictionaryApi {

    @GET("entries/en/{word}")
    suspend fun searchWord(
        @Path("word") word: String
    ): List<DictionaryResponse>
}
