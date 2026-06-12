package com.profico.minibreeds.data.repository

import android.util.Log
import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.core.AppResult
import com.profico.minibreeds.core.DispatcherProvider
import com.profico.minibreeds.core.onFailure
import com.profico.minibreeds.core.onSuccess
import com.profico.minibreeds.data.local.FavoritesDataSource
import com.profico.minibreeds.data.remote.DogApiService
import com.profico.minibreeds.data.remote.dto.BreedsResponseDto
import com.profico.minibreeds.data.remote.safeApiCall
import com.profico.minibreeds.domain.model.Breed
import com.profico.minibreeds.domain.repository.BreedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [BreedRepository] that fetches breeds over the network once per session and
 * keeps them in an in-memory [MutableStateFlow] cache, avoiding redundant
 * requests while the process is alive.
 */
class BreedRepositoryImpl(
    private val api: DogApiService,
    private val favoritesDataSource: FavoritesDataSource,
    private val dispatchers: DispatcherProvider,
) : BreedRepository {

    private val breedsCache = MutableStateFlow<List<Breed>?>(null)

    override val cachedBreeds: StateFlow<List<Breed>?> = breedsCache.asStateFlow()

    override suspend fun refreshBreeds(): AppResult<List<Breed>> =
        withContext(dispatchers.io) {
            safeApiCall { api.getAllBreeds() }
                .toBreeds()
                .onSuccess { breeds -> breedsCache.value = breeds }
                .onFailure { error -> Log.w(TAG, "Breed refresh failed: $error") }
        }

    override fun observeBreed(name: String): Flow<Breed?> =
        breedsCache.map { breeds -> breeds?.firstOrNull { it.name == name } }

    override val favorites: Flow<Set<String>> = favoritesDataSource.favorites

    override suspend fun toggleFavorite(breedName: String) {
        favoritesDataSource.toggle(breedName)
    }

    /** Maps the raw DTO to a sorted list of domain [Breed] objects, or a [AppResult.Failure] if the API status field is not "success". */
    private fun AppResult<BreedsResponseDto>.toBreeds(): AppResult<List<Breed>> =
        when (this) {
            is AppResult.Failure -> this
            is AppResult.Success ->
                if (value.status != BreedsResponseDto.STATUS_SUCCESS) {
                    AppResult.Failure(AppError.ApiStatus(value.status))
                } else {
                    AppResult.Success(
                        value.message
                            .map { (name, subBreeds) -> Breed(name = name, subBreeds = subBreeds) }
                            .sortedBy { it.name },
                    )
                }
        }

    private companion object {
        const val TAG = "BreedRepository"
    }
}
