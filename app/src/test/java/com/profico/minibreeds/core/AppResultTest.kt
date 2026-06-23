package com.profico.minibreeds.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for the [AppResult] transformation helpers used across layer
 * boundaries. These are exercised indirectly elsewhere (e.g. the repository's
 * `onFailure`), but their branching is pinned down directly here.
 */
class AppResultTest {

    private val failure = AppResult.Failure(AppError.NoConnection)

    /**
     * Happy path for [AppResult.map]: on a [AppResult.Success], the transform
     * is applied and the result stays wrapped in `Success`.
     */
    @Test
    fun `map transforms the success value`() {
        val result: AppResult<Int> = AppResult.Success(21)

        assertEquals(AppResult.Success(42), result.map { it * 2 })
    }

    /**
     * Short-circuit guarantee: on a [AppResult.Failure], [AppResult.map] must
     * return the same failure unchanged and must not invoke the transform.
     */
    @Test
    fun `map passes failures through without invoking the transform`() {
        var invoked = false

        val result = failure.map { invoked = true; it }

        assertEquals(failure, result)
        assertFalse(invoked)
    }

    /**
     * [AppResult.onSuccess] runs its block only on `Success`, exposes the
     * wrapped value to it, and returns the original receiver so callers can
     * chain. The mirror case on `Failure` must not invoke the block.
     */
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

    /**
     * [AppResult.onFailure] runs its block only on `Failure`, exposes the
     * [AppError] to it, and returns the original receiver. The mirror case on
     * `Success` must not invoke the block.
     */
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
}
