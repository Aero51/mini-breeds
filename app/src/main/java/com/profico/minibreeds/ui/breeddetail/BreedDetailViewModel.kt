package com.profico.minibreeds.ui.breeddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.core.onFailure
import com.profico.minibreeds.core.onSuccess
import com.profico.minibreeds.domain.repository.BreedRepository
import com.profico.minibreeds.ui.navigation.BreedDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages detail state for the breed identified by the
 * [BreedDetailRoute.ARG_BREED_NAME] navigation argument. Initiates a network
 * refresh when the in-memory cache is empty (e.g. after process death).
 */
class BreedDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BreedRepository,
) : ViewModel() {

    private val breedName: String =
        checkNotNull(savedStateHandle[BreedDetailRoute.ARG_BREED_NAME]) {
            "BreedDetailViewModel requires a '${BreedDetailRoute.ARG_BREED_NAME}' navigation argument"
        }

    /** Non-null when the cold-cache refresh failed; cleared on retry. */
    private val refreshError = MutableStateFlow<AppError?>(null)

    /** Random photo URL; stays null until a fetch succeeds. */
    private val imageUrl = MutableStateFlow<String?>(null)

    /** Combined state merging the cached breed entry, favorites, image URL, and any refresh error. */
    val uiState: StateFlow<BreedDetailUiState> =
        combine(
            repository.observeBreed(breedName),
            repository.favorites,
            refreshError,
            imageUrl,
        ) { breed, favorites, error, image ->
            when {
                breed != null -> BreedDetailUiState.Content(
                    name = breed.name,
                    subBreeds = breed.subBreeds,
                    isFavorite = breed.name in favorites,
                    imageUrl = image,
                )
                error != null -> BreedDetailUiState.Error(error)
                else -> BreedDetailUiState.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = BreedDetailUiState.Loading,
        )

    init {
        // After process death the in-memory cache is gone; refetch so a deep
        // restore onto this screen still renders instead of hanging.
        if (repository.cachedBreeds.value == null) {
            refresh()
        }
        loadImage()
    }

    /** Re-triggers the breed refresh and image fetch after a previous failure. */
    fun retry() {
        refresh()
        loadImage()
    }

    /** Toggles the favorite status of the current breed. */
    fun onToggleFavorite() {
        viewModelScope.launch { repository.toggleFavorite(breedName) }
    }

    /** Performs the network fetch and updates [refreshError] on failure. On success the cache update flows into [uiState] via [BreedRepository.observeBreed]. */
    private fun refresh() {
        viewModelScope.launch {
            refreshError.value = null
            repository.refreshBreeds().onFailure { refreshError.value = it }
        }
    }

    /** Fetches the breed photo. A failure leaves [imageUrl] null so the header keeps the monogram avatar — it never replaces breed content with an error screen. */
    private fun loadImage() {
        viewModelScope.launch {
            repository.fetchBreedImageUrl(breedName).onSuccess { imageUrl.value = it }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
