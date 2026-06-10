package com.profico.minibreeds.testutil

import com.profico.minibreeds.data.local.FavoritesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFavoritesDataSource(
    initial: Set<String> = emptySet(),
) : FavoritesDataSource {

    val state = MutableStateFlow(initial)
    val toggledNames = mutableListOf<String>()

    override val favorites: Flow<Set<String>> = state

    override suspend fun toggle(breedName: String) {
        toggledNames += breedName
        state.value =
            if (breedName in state.value) state.value - breedName else state.value + breedName
    }
}
