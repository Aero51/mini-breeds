package com.profico.minibreeds.ui.navigation

import kotlinx.serialization.Serializable

/** Navigation route for the breed list (home) screen. */
@Serializable
data object BreedListRoute

@Serializable
data class BreedDetailRoute(val breedName: String) {
    companion object {
        /**
         * SavedStateHandle key of [breedName]; typed navigation stores route
         * arguments under their property names. Reading by key (instead of
         * SavedStateHandle.toRoute()) keeps the ViewModel testable on the JVM,
         * where toRoute() needs a real Android Bundle.
         */
        const val ARG_BREED_NAME = "breedName"
    }
}
