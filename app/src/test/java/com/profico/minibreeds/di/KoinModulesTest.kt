package com.profico.minibreeds.di

import android.content.Context
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

/**
 * Static verification of the Koin graph: every definition's dependencies must
 * be resolvable. Catches missing/mis-bound definitions without an emulator.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinModulesTest {

    /**
     * `networkModule` is self-contained; every dependency it pulls in is
     * declared within the module, so `.verify()` runs without any extras.
     */
    @Test
    fun `network module definitions are resolvable`() {
        networkModule.verify()
    }

    /**
     * `dataModule` consumes [Context], which is injected by the Koin Android
     * runtime in production. We declare it as an `extraType` so verification
     * accepts it without needing an Android environment here.
     */
    @Test
    fun `data module definitions are resolvable`() {
        dataModule.verify(
            // Provided by the Koin Android environment at runtime.
            extraTypes = listOf(Context::class),
        )
    }
}
