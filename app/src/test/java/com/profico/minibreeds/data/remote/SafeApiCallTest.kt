package com.profico.minibreeds.data.remote

import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.core.AppResult
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Pins down the exception → [AppError] mapping performed by `safeApiCall`,
 * which is the single conversion point between Retrofit/OkHttp/Kotlinx
 * failures and our domain errors.
 */
class SafeApiCallTest {

    /**
     * Runs `safeApiCall` with a block that throws [t] and unwraps the
     * resulting [AppResult.Failure] to its [AppError]. Keeps each test a
     * single one-line assertion.
     */
    private suspend fun errorOf(t: Throwable): AppError {
        val result = safeApiCall<Unit> { throw t }
        return (result as AppResult.Failure).error
    }

    /** Happy path: a returning block is wrapped in [AppResult.Success]. */
    @Test
    fun `success wraps value`() = runTest {
        val result = safeApiCall { 42 }
        assertEquals(AppResult.Success(42), result)
    }

    /** DNS failure → [AppError.NoConnection]. */
    @Test
    fun `UnknownHostException maps to NoConnection`() = runTest {
        assertEquals(AppError.NoConnection, errorOf(UnknownHostException("dog.ceo")))
    }

    /** TCP refused → [AppError.NoConnection]. */
    @Test
    fun `ConnectException maps to NoConnection`() = runTest {
        assertEquals(AppError.NoConnection, errorOf(ConnectException("refused")))
    }

    /** Read/connect timeout → [AppError.Timeout]. */
    @Test
    fun `SocketTimeoutException maps to Timeout`() = runTest {
        assertEquals(AppError.Timeout, errorOf(SocketTimeoutException("timed out")))
    }

    /** OkHttp's call-deadline path also funnels into [AppError.Timeout]. */
    @Test
    fun `InterruptedIOException maps to Timeout`() = runTest {
        assertEquals(AppError.Timeout, errorOf(InterruptedIOException("interrupted")))
    }

    /**
     * Builds a real [retrofit2.HttpException] over a 503 [Response.error] and
     * asserts the status code round-trips into [AppError.Http].
     */
    @Test
    fun `HttpException maps to Http with status code`() = runTest {
        val body = "".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(Response.error<Unit>(503, body))

        assertEquals(AppError.Http(503), errorOf(httpException))
    }

    /** Kotlinx parse failure → [AppError.Serialization]. */
    @Test
    fun `SerializationException maps to Serialization`() = runTest {
        assertEquals(AppError.Serialization, errorOf(SerializationException("bad json")))
    }

    /** Any other I/O failure (e.g. "connection reset") falls into [AppError.NoConnection]. */
    @Test
    fun `generic IOException maps to NoConnection`() = runTest {
        assertEquals(AppError.NoConnection, errorOf(IOException("connection reset")))
    }

    /**
     * Unrecognised exceptions become [AppError.Unknown] with the original
     * [Throwable] preserved as `cause` for logging/debugging.
     */
    @Test
    fun `unexpected exception maps to Unknown with cause`() = runTest {
        val boom = IllegalStateException("boom")
        val error = errorOf(boom)

        assertTrue(error is AppError.Unknown)
        assertEquals(boom, (error as AppError.Unknown).cause)
    }

    /**
     * Structured-concurrency guarantee: [CancellationException] must propagate
     * out of `safeApiCall`, never be turned into a fake [AppResult.Failure].
     * Uses `runBlocking` (not `runTest`) so the cancellation can escape the
     * coroutine to [assertThrows].
     */
    @Test
    fun `CancellationException is rethrown, not swallowed`() {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                safeApiCall<Unit> { throw CancellationException("cancelled") }
            }
        }
    }
}
