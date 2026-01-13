package com.example.dictionary.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    val api: DictionaryApi = Retrofit.Builder()
        .baseUrl("https://api.dictionaryapi.dev/api/v2/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DictionaryApi::class.java)
}
