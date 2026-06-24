package com.profico.minibreeds.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Hand-written fake repository for ViewModel tests — no mocking library needed. */
class FakeBreedRepository(
    private val breeds: List<Breed> = emptyList(),
    private val errorOnGet: Throwable? = null,
) : BreedRepository {

    private val favoritesState = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun getBreeds(): List<Breed> {
        errorOnGet?.let { throw it }
        return breeds
    }

    override val favorites: Flow<Set<String>> = favoritesState

    override suspend fun toggleFavorite(name: String) {
        favoritesState.update { if (name in it) it - name else it + name }
    }
}
