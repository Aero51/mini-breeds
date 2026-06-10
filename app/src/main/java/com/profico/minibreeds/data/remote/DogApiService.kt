package com.profico.minibreeds.data.remote

import com.profico.minibreeds.data.remote.dto.BreedsResponseDto
import retrofit2.http.GET

/** Retrofit service for the dog.ceo public API. */
interface DogApiService {

    /** Returns all dog breeds and their sub-breeds from `GET api/breeds/list/all`. */
    @GET("api/breeds/list/all")
    suspend fun getAllBreeds(): BreedsResponseDto
}
