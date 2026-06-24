package com.profico.minibreeds.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

/** Retrofit service for the dog.ceo API. */
interface DogApi {

    @GET("api/breeds/list/all")
    suspend fun getAllBreeds(): DogResponse

    companion object {
        private const val BASE_URL = "https://dog.ceo/"

        fun create(): DogApi {
            val json = Json { ignoreUnknownKeys = true }
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(DogApi::class.java)
        }
    }
}
