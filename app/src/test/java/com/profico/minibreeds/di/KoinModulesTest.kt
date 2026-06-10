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

    @Test
    fun `network module definitions are resolvable`() {
        networkModule.verify()
    }

    @Test
    fun `data module definitions are resolvable`() {
        dataModule.verify(
            // Provided by the Koin Android environment at runtime.
            extraTypes = listOf(Context::class),
        )
    }
}
