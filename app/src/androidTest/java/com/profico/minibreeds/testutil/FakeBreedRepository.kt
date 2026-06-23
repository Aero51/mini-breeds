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
 *
 * Scripting points:
 *  - [refreshResults] — queue of results returned by successive [refreshBreeds]
 *    calls.
 *  - [imageResult] — the next answer for [fetchBreedImageUrl].
 *
 * Recorders:
 *  - [refreshCallCount] — how many times [refreshBreeds] was called.
 *  - [toggledNames] — every name passed to [toggleFavorite].
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

    /** Read-only view of the in-memory cache used by the UI layer. */
    override val cachedBreeds: StateFlow<List<Breed>?> = cache.asStateFlow()

    /**
     * Pops the next scripted result, or falls back to `Success(cache)` if none
     * is queued. Increments [refreshCallCount] and writes successful results
     * through to the cache so observers update.
     */
    override suspend fun refreshBreeds(): AppResult<List<Breed>> {
        refreshCallCount++
        val result = refreshResults.removeFirstOrNull()
            ?: AppResult.Success(cache.value.orEmpty())
        if (result is AppResult.Success) cache.value = result.value
        return result
    }

    /** Maps the cache flow to the single matching breed (or `null` for miss). */
    override fun observeBreed(name: String): Flow<Breed?> =
        cache.map { breeds -> breeds?.firstOrNull { it.name == name } }

    /** Result returned by [fetchBreedImageUrl]; defaults to a stable fake URL. */
    var imageResult: AppResult<String> = AppResult.Success("https://example.com/dog.jpg")

    /** Returns [imageResult] verbatim; the breed name is ignored. */
    override suspend fun fetchBreedImageUrl(breedName: String): AppResult<String> = imageResult

    /** Exposes [favoritesState] as a read-only flow for the UI layer. */
    override val favorites: Flow<Set<String>> = favoritesState

    /** Records the name into [toggledNames] and flips set membership. */
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
