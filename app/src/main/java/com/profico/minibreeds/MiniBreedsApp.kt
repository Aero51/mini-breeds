package com.profico.minibreeds

import android.app.Application
import com.profico.minibreeds.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/** [android.app.Application] subclass that initialises the Koin dependency graph. */
class MiniBreedsApp : Application() {

    /** Starts Koin with [appModules] before any component is created. */
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@MiniBreedsApp)
            modules(appModules)
        }
    }
}
