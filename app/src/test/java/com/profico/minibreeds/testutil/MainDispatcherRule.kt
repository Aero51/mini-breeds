package com.profico.minibreeds.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit 4 rule that swaps `Dispatchers.Main` (used by `viewModelScope`) with a
 * [TestDispatcher] for ViewModel tests, and restores it afterwards.
 *
 * Callers may pass an `UnconfinedTestDispatcher` to get immediate execution
 * instead of the default lazy [StandardTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    /** Installs the test dispatcher as `Dispatchers.Main` before each test. */
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    /** Restores the real `Dispatchers.Main` after each test. */
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
