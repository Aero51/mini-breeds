package com.profico.minibreeds.ui.breedlist

import com.profico.minibreeds.core.AppError

/**
 * Row model the breed list screen renders.
 * Favorites are already merged in so the screen is purely a function of this state.
 */
data class BreedRowUi(
    val name: String,
    val subBreedCount: Int,
    val isFavorite: Boolean,
)

/** Sealed states emitted by [BreedListViewModel]; drives the list screen UI. */
sealed interface BreedListUiState {

    /** Fetch is in progress; no data is available yet. */
    data object Loading : BreedListUiState

    /** Fetch failed; [error] describes what went wrong. */
    data class Error(val error: AppError) : BreedListUiState

    /** Fetch succeeded; [rows] is the (possibly search-filtered) list to display. */
    data class Content(
        val rows: List<BreedRowUi>,
        /** True when breeds exist in the cache but the current query matches none. */
        val noResultsForQuery: Boolean,
    ) : BreedListUiState
}
