package com.profico.minibreeds.ui.breedlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.profico.minibreeds.MiniBreedsApp
import com.profico.minibreeds.data.Breed
import com.profico.minibreeds.data.BreedRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Row shown in the breed list; favorite state is already merged in. */
data class BreedListItem(
    val name: String,
    val subBreedCount: Int,
    val isFavorite: Boolean,
)

/** What the breed list screen renders. */
sealed interface BreedListUiState {
    data object Loading : BreedListUiState
    data class Error(val isOffline: Boolean) : BreedListUiState
    data class Content(val breeds: List<BreedListItem>) : BreedListUiState
}

/**
 * Loads the breed list, applies the real-time search filter, and merges in
 * persisted favorites.
 *
 * The ViewModel keeps the three inputs it needs — the loaded [breeds], the search
 * [query], and the current [favorites] — as plain fields, and rebuilds [uiState]
 * with [recompute] whenever any of them changes. All of this runs on the main
 * thread (via [viewModelScope]), so no synchronization is needed.
 */
class BreedListViewModel(
    private val repository: BreedRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BreedListUiState>(BreedListUiState.Loading)
    val uiState: StateFlow<BreedListUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Latest inputs that [recompute] builds the UI state from.
    private var breeds: List<Breed> = emptyList()
    private var favorites: Set<String> = emptySet()
    private var loading: Boolean = true
    private var error: BreedListUiState.Error? = null

    init {
        loadBreeds()
        // Keep favorites in sync and re-render the list whenever they change.
        viewModelScope.launch {
            repository.favorites.collect { latest ->
                favorites = latest
                recompute()
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        recompute()
    }

    fun retry() = loadBreeds()

    fun onToggleFavorite(name: String) {
        viewModelScope.launch { repository.toggleFavorite(name) }
    }

    private fun loadBreeds() {
        loading = true
        error = null
        recompute()
        viewModelScope.launch {
            try {
                breeds = repository.getBreeds()
            } catch (e: IOException) {
                // No network / host unreachable.
                error = BreedListUiState.Error(isOffline = true)
            } catch (e: Exception) {
                error = BreedListUiState.Error(isOffline = false)
            }
            loading = false
            recompute()
        }
    }

    /** Rebuilds [uiState] from the latest breeds, query, and favorites. */
    private fun recompute() {
        val failure = error
        _uiState.value = when {
            failure != null -> failure
            loading -> BreedListUiState.Loading
            else -> BreedListUiState.Content(
                breeds
                    .filter { it.name.contains(_query.value.trim(), ignoreCase = true) }
                    .map { breed ->
                        BreedListItem(
                            name = breed.name,
                            subBreedCount = breed.subBreeds.size,
                            isFavorite = breed.name in favorites,
                        )
                    },
            )
        }
    }

    companion object {
        /** Manual ViewModel factory: pulls the repository off [MiniBreedsApp]. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MiniBreedsApp
                BreedListViewModel(app.repository)
            }
        }
    }
}
