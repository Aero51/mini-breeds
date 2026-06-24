package com.profico.minibreeds.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for breed data. Kept as an interface so the ViewModel
 * can be tested against a fake instead of the real network/DataStore.
 */
interface BreedRepository {

    /** Fetches the breed list from the network. Throws on network or parsing failure. */
    suspend fun getBreeds(): List<Breed>

    /** Names of breeds the user marked as favorite, persisted on disk. */
    val favorites: Flow<Set<String>>

    /** Adds [name] to favorites, or removes it if already present. */
    suspend fun toggleFavorite(name: String)
}

/** Default implementation backed by Retrofit ([DogApi]) and Preferences DataStore. */
class BreedRepositoryImpl(
    private val api: DogApi,
    private val dataStore: DataStore<Preferences>,
) : BreedRepository {

    override suspend fun getBreeds(): List<Breed> {
        val response = api.getAllBreeds()
        check(response.status == "success") { "API returned status=${response.status}" }
        return response.toBreeds()
    }

    override val favorites: Flow<Set<String>> = dataStore.data
        // A corrupted/unreadable store degrades to "no favorites" instead of crashing.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[FAVORITES_KEY].orEmpty() }

    override suspend fun toggleFavorite(name: String) {
        dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY].orEmpty()
            prefs[FAVORITES_KEY] = if (name in current) current - name else current + name
        }
    }

    private companion object {
        val FAVORITES_KEY = stringSetPreferencesKey("favorite_breeds")
    }
}

/** Maps the API response to a sorted list of [Breed]s. Pure, so it is easy to unit-test. */
fun DogResponse.toBreeds(): List<Breed> =
    message
        .map { (name, subBreeds) -> Breed(name = name, subBreeds = subBreeds) }
        .sortedBy { it.name }
