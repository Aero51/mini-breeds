package com.profico.minibreeds.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** [FavoritesDataSource] backed by Preferences DataStore. */
class DataStoreFavoritesDataSource(
    private val dataStore: DataStore<Preferences>,
) : FavoritesDataSource {

    override val favorites: Flow<Set<String>> = dataStore.data
        // A corrupted or unreadable store degrades to "no favorites" instead
        // of crashing; non-IO failures are real bugs and must surface.
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> preferences[FAVORITES_KEY].orEmpty() }

    override suspend fun toggle(breedName: String) {
        dataStore.edit { preferences ->
            val current = preferences[FAVORITES_KEY].orEmpty()
            preferences[FAVORITES_KEY] =
                if (breedName in current) current - breedName else current + breedName
        }
    }

    private companion object {
        val FAVORITES_KEY = stringSetPreferencesKey("favorite_breeds")
    }
}
