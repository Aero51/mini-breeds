package com.profico.minibreeds.ui.breedlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.core.AppResult
import com.profico.minibreeds.domain.model.Breed
import com.profico.minibreeds.domain.repository.BreedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages breed list state: loads breeds on creation, applies real-time search
 * filtering, and merges favorite state — all exposed as a single [uiState] flow.
 */
class BreedListViewModel(
    private val repository: BreedRepository,
) : ViewModel() {

    private sealed interface LoadState {
        data object Loading : LoadState
        data class Loaded(val breeds: List<Breed>) : LoadState
        data class Failed(val error: AppError) : LoadState
    }

    private val loadState = MutableStateFlow<LoadState>(LoadState.Loading)

    // Kept separate from uiState so typed text survives Loading/Error phases.
    private val _query = MutableStateFlow("")

    /** Current search query; exposed so the screen can restore the field value. */
    val query: StateFlow<String> = _query.asStateFlow()

    /** Combined, search-filtered, favorites-merged state ready for the screen to render. */
    val uiState: StateFlow<BreedListUiState> =
        combine(loadState, _query, repository.favorites) { load, query, favorites ->
            when (load) {
                is LoadState.Loading -> BreedListUiState.Loading
                is LoadState.Failed -> BreedListUiState.Error(load.error)
                is LoadState.Loaded -> {
                    val rows = load.breeds
                        .filter { it.name.contains(query.trim(), ignoreCase = true) }
                        .map { breed ->
                            BreedRowUi(
                                name = breed.name,
                                subBreedCount = breed.subBreeds.size,
                                isFavorite = breed.name in favorites,
                            )
                        }
                    BreedListUiState.Content(
                        rows = rows,
                        noResultsForQuery = rows.isEmpty() && load.breeds.isNotEmpty(),
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = BreedListUiState.Loading,
        )

    init {
        load()
    }

    /** Updates the search query used to filter the breed list in [uiState]. */
    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    /** Re-triggers a network fetch after a previous failure. */
    fun retry() = load()

    /** Toggles the favorite status of [breedName] in persistent storage. */
    fun onToggleFavorite(breedName: String) {
        viewModelScope.launch { repository.toggleFavorite(breedName) }
    }

    /** Performs the network fetch and transitions [loadState] accordingly. */
    private fun load() {
        viewModelScope.launch {
            loadState.value = LoadState.Loading
            loadState.value = when (val result = repository.refreshBreeds()) {
                is AppResult.Success -> LoadState.Loaded(result.value)
                is AppResult.Failure -> LoadState.Failed(result.error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
