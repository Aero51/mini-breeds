package com.profico.minibreeds.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Local persistence boundary for favorite breeds. Abstracted behind an
 * interface so the repository (and tests) never depend on DataStore directly.
 */
interface FavoritesDataSource {

    /** Set of breed names the user marked as favorite. Never throws. */
    val favorites: Flow<Set<String>>

    /** Adds [breedName] to favorites, or removes it if already present. */
    suspend fun toggle(breedName: String)
}
