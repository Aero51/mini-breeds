package com.profico.minibreeds.testutil

import com.profico.minibreeds.core.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * [DispatcherProvider] that points every channel (`io`, `default`, `main`) at
 * a single passed-in [TestDispatcher], so `runTest` can advance and observe
 * all repository coroutines in lockstep.
 */
class TestDispatcherProvider(testDispatcher: TestDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val main: CoroutineDispatcher = testDispatcher
}
