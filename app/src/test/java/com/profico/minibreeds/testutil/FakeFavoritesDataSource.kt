package com.profico.minibreeds.testutil

import com.profico.minibreeds.data.local.FavoritesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Minimal in-memory fake of [FavoritesDataSource] used by the repository test.
 *
 * - [state] is publicly mutable so a test can both observe and force-set the set.
 * - [toggledNames] records every name passed to [toggle], for delegation asserts.
 */
class FakeFavoritesDataSource(
    initial: Set<String> = emptySet(),
) : FavoritesDataSource {

    val state = MutableStateFlow(initial)
    val toggledNames = mutableListOf<String>()

    /** Exposes [state] as a flow for the SUT to collect. */
    override val favorites: Flow<Set<String>> = state

    /** Records the call and flips membership of [breedName] in [state]. */
    override suspend fun toggle(breedName: String) {
        toggledNames += breedName
        state.value =
            if (breedName in state.value) state.value - breedName else state.value + breedName
    }
}
