package com.profico.minibreeds.testutil

import com.profico.minibreeds.core.AppResult
import com.profico.minibreeds.domain.model.Breed
import com.profico.minibreeds.domain.repository.BreedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Instrumented-test twin of the JVM-test fake (`src/test/.../testutil`);
 * source sets cannot share code without extra Gradle wiring, so the 40
 * lines are duplicated instead.
 */
class FakeBreedRepository(
    initialCache: List<Breed>? = null,
    initialFavorites: Set<String> = emptySet(),
) : BreedRepository {

    private val cache = MutableStateFlow(initialCache)
    val favoritesState = MutableStateFlow(initialFavorites)

    /** Queue of results returned by successive [refreshBreeds] calls. */
    val refreshResults = ArrayDeque<AppResult<List<Breed>>>()
    var refreshCallCount = 0
        private set
    val toggledNames = mutableListOf<String>()

    override val cachedBreeds: StateFlow<List<Breed>?> = cache.asStateFlow()

    override suspend fun refreshBreeds(): AppResult<List<Breed>> {
        refreshCallCount++
        val result = refreshResults.removeFirstOrNull()
            ?: AppResult.Success(cache.value.orEmpty())
        if (result is AppResult.Success) cache.value = result.value
        return result
    }

    override fun observeBreed(name: String): Flow<Breed?> =
        cache.map { breeds -> breeds?.firstOrNull { it.name == name } }

    override val favorites: Flow<Set<String>> = favoritesState

    override suspend fun toggleFavorite(breedName: String) {
        toggledNames += breedName
        favoritesState.value =
            if (breedName in favoritesState.value) {
                favoritesState.value - breedName
            } else {
                favoritesState.value + breedName
            }
    }
}
