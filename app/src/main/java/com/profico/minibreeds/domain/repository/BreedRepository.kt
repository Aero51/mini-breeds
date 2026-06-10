package com.profico.minibreeds.domain.repository

import com.profico.minibreeds.core.AppResult
import com.profico.minibreeds.domain.model.Breed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for breed data. This interface is the decoupling
 * boundary between the UI layer and everything network/persistence related.
 */
interface BreedRepository {

    /** Last successfully fetched breeds; null until the first successful load. */
    val cachedBreeds: StateFlow<List<Breed>?>

    /** Fetches breeds from the network and updates [cachedBreeds] on success. */
    suspend fun refreshBreeds(): AppResult<List<Breed>>

    /** Observes a single breed from the cache; emits null while absent. */
    fun observeBreed(name: String): Flow<Breed?>

    /** Fetches the URL of one random image for [breedName]. Not cached — each call may return a different image. */
    suspend fun fetchBreedImageUrl(breedName: String): AppResult<String>

    /** Names of breeds the user marked as favorite. */
    val favorites: Flow<Set<String>>

    /** Adds [breedName] to favorites if absent, or removes it if already present. */
    suspend fun toggleFavorite(breedName: String)
}
