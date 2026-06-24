package com.profico.minibreeds

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.profico.minibreeds.data.BreedRepository
import com.profico.minibreeds.data.BreedRepositoryImpl
import com.profico.minibreeds.data.DogApi

/** App-wide Preferences DataStore used to persist favorites. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "minibreeds_prefs")

/**
 * Application entry point. Does the app's dependency injection by hand: it builds
 * the single [BreedRepository] that ViewModels read through [repository].
 */
class MiniBreedsApp : Application() {

    lateinit var repository: BreedRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BreedRepositoryImpl(DogApi.create(), dataStore)
    }
}
