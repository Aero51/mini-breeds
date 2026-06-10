package com.profico.minibreeds.ui.breeddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.core.AppResult
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

    /** Combined state merging the cached breed entry, favorites, and any refresh error. */
    val uiState: StateFlow<BreedDetailUiState> =
        combine(
            repository.observeBreed(breedName),
            repository.favorites,
            refreshError,
        ) { breed, favorites, error ->
            when {
                breed != null -> BreedDetailUiState.Content(
                    name = breed.name,
                    subBreeds = breed.subBreeds,
                    isFavorite = breed.name in favorites,
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
    }

    /** Re-triggers a network refresh after a previous failure. */
    fun retry() = refresh()

    /** Toggles the favorite status of the current breed. */
    fun onToggleFavorite() {
        viewModelScope.launch { repository.toggleFavorite(breedName) }
    }

    /** Performs the network fetch and updates [refreshError] on failure. */
    private fun refresh() {
        viewModelScope.launch {
            refreshError.value = null
            when (val result = repository.refreshBreeds()) {
                is AppResult.Success -> Unit // cache update flows into uiState
                is AppResult.Failure -> refreshError.value = result.error
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
