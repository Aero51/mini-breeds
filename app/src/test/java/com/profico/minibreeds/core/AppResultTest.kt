package com.profico.minibreeds.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [AppResult] transformation helpers used across layer
 * boundaries. These are exercised indirectly elsewhere (e.g. the repository's
 * `onFailure`), but their branching is pinned down directly here.
 */
class AppResultTest {

    private val failure = AppResult.Failure(AppError.NoConnection)

    @Test
    fun `map transforms the success value`() {
        val result: AppResult<Int> = AppResult.Success(21)

        assertEquals(AppResult.Success(42), result.map { it * 2 })
    }

    @Test
    fun `map passes failures through without invoking the transform`() {
        var invoked = false

        val result = failure.map { invoked = true; it }

        assertEquals(failure, result)
        assertFalse(invoked)
    }

    @Test
    fun `onSuccess runs only on success and returns the receiver`() {
        val success = AppResult.Success("ok")
        var seen: String? = null

        val returned = success.onSuccess { seen = it }

        assertEquals("ok", seen)
        assertEquals(success, returned)

        var ranOnFailure = false
        failure.onSuccess { ranOnFailure = true }
        assertFalse(ranOnFailure)
    }

    @Test
    fun `onFailure runs only on failure and returns the receiver`() {
        var seen: AppError? = null

        val returned = failure.onFailure { seen = it }

        assertEquals(AppError.NoConnection, seen)
        assertEquals(failure, returned)

        var ranOnSuccess = false
        AppResult.Success("ok").onFailure { ranOnSuccess = true }
        assertFalse(ranOnSuccess)
    }

    @Test
    fun `factory helpers build the matching variants`() {
        assertTrue(AppResult.success(1) is AppResult.Success)
        assertTrue(AppResult.failure(AppError.Timeout) is AppResult.Failure)
    }
}
