package com.profico.minibreeds.ui.breeddetail

import com.profico.minibreeds.core.AppError

/** Sealed states emitted by [BreedDetailViewModel]; drives the detail screen UI. */
sealed interface BreedDetailUiState {

    /** Breed data is being fetched or the in-memory cache is still cold. */
    data object Loading : BreedDetailUiState

    /** Fetch failed; [error] describes what went wrong. */
    data class Error(val error: AppError) : BreedDetailUiState

    /** Breed data is available and ready to display. */
    data class Content(
        val name: String,
        val subBreeds: List<String>,
        val isFavorite: Boolean,
    ) : BreedDetailUiState
}
