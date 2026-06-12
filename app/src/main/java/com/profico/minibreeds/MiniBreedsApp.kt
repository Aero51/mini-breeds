package com.profico.minibreeds

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.profico.minibreeds.di.appModules
import okhttp3.OkHttpClient
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * [android.app.Application] subclass that initialises the Koin dependency
 * graph and provides the app-wide Coil [ImageLoader].
 */
class MiniBreedsApp : Application(), SingletonImageLoader.Factory {

    /** Starts Koin with [appModules] before any component is created. */
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@MiniBreedsApp)
            modules(appModules)
        }
    }

    /**
     * Builds the singleton [ImageLoader] Coil resolves lazily on first use.
     * Image requests go through the Koin-provided [OkHttpClient], so they
     * share the API client's connection pool, timeouts, and debug logging
     * instead of Coil creating a second HTTP client with its own defaults.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { getKoin().get<OkHttpClient>() }))
            }
            .build()
}
