package com.profico.minibreeds.data.remote

import com.profico.minibreeds.data.remote.dto.BreedImageDto
import com.profico.minibreeds.data.remote.dto.BreedsResponseDto
import retrofit2.http.GET
import retrofit2.http.Path

/** Retrofit service for the dog.ceo public API. */
interface DogApiService {

    /** Returns all dog breeds and their sub-breeds from `GET api/breeds/list/all`. */
    @GET("api/breeds/list/all")
    suspend fun getAllBreeds(): BreedsResponseDto

    /** Returns the URL of one random image for [breed] from `GET api/breed/{breed}/images/random`. */
    @GET("api/breed/{breed}/images/random")
    suspend fun getBreedImage(@Path("breed") breed: String): BreedImageDto
}
