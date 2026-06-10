package com.profico.minibreeds.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.profico.minibreeds.BuildConfig
import com.profico.minibreeds.core.DefaultDispatcherProvider
import com.profico.minibreeds.core.DispatcherProvider
import com.profico.minibreeds.data.local.DataStoreFavoritesDataSource
import com.profico.minibreeds.data.local.FavoritesDataSource
import com.profico.minibreeds.data.remote.DogApiService
import com.profico.minibreeds.data.repository.BreedRepositoryImpl
import com.profico.minibreeds.domain.repository.BreedRepository
import com.profico.minibreeds.ui.breeddetail.BreedDetailViewModel
import com.profico.minibreeds.ui.breedlist.BreedListViewModel
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val BASE_URL = "https://dog.ceo/"
private const val TIMEOUT_SECONDS = 10L

// The one place the app-wide DataStore instance lives; creating it more than
// once per file is unsupported by the library.
private val Context.minibreedsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "minibreeds_prefs",
)

/** Koin module providing [kotlinx.serialization.json.Json], [okhttp3.OkHttpClient], [retrofit2.Retrofit], and [DogApiService]. */
val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
    single {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()
    }
    single {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(get())
            .addConverterFactory(
                get<Json>().asConverterFactory("application/json".toMediaType()),
            )
            .build()
    }
    single<DogApiService> { get<Retrofit>().create(DogApiService::class.java) }
}

/** Koin module providing [DispatcherProvider], [androidx.datastore.core.DataStore], [FavoritesDataSource], and [BreedRepository]. */
val dataModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<DataStore<Preferences>> { androidContext().minibreedsDataStore }
    single<FavoritesDataSource> { DataStoreFavoritesDataSource(get()) }
    single<BreedRepository> { BreedRepositoryImpl(get(), get(), get()) }
}

/** Koin module providing [BreedListViewModel] and [BreedDetailViewModel] via Koin's ViewModel DSL. */
val viewModelModule = module {
    viewModelOf(::BreedListViewModel)
    viewModelOf(::BreedDetailViewModel) // SavedStateHandle is injected by Koin
}

/** Aggregated list of all Koin modules; pass this to [org.koin.core.context.startKoin]. */
val appModules = listOf(networkModule, dataModule, viewModelModule)
