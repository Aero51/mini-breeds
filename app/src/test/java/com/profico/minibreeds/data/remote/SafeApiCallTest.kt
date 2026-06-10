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

class SafeApiCallTest {

    private suspend fun errorOf(t: Throwable): AppError {
        val result = safeApiCall<Unit> { throw t }
        return (result as AppResult.Failure).error
    }

    @Test
    fun `success wraps value`() = runTest {
        val result = safeApiCall { 42 }
        assertEquals(AppResult.Success(42), result)
    }

    @Test
    fun `UnknownHostException maps to NoConnection`() = runTest {
        assertEquals(AppError.NoConnection, errorOf(UnknownHostException("dog.ceo")))
    }

    @Test
    fun `ConnectException maps to NoConnection`() = runTest {
        assertEquals(AppError.NoConnection, errorOf(ConnectException("refused")))
    }

    @Test
    fun `SocketTimeoutException maps to Timeout`() = runTest {
        assertEquals(AppError.Timeout, errorOf(SocketTimeoutException("timed out")))
    }

    @Test
    fun `InterruptedIOException maps to Timeout`() = runTest {
        assertEquals(AppError.Timeout, errorOf(InterruptedIOException("interrupted")))
    }

    @Test
    fun `HttpException maps to Http with status code`() = runTest {
        val body = "".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(Response.error<Unit>(503, body))

        assertEquals(AppError.Http(503), errorOf(httpException))
    }

    @Test
    fun `SerializationException maps to Serialization`() = runTest {
        assertEquals(AppError.Serialization, errorOf(SerializationException("bad json")))
    }

    @Test
    fun `generic IOException maps to NoConnection`() = runTest {
        assertEquals(AppError.NoConnection, errorOf(IOException("connection reset")))
    }

    @Test
    fun `unexpected exception maps to Unknown with cause`() = runTest {
        val boom = IllegalStateException("boom")
        val error = errorOf(boom)

        assertTrue(error is AppError.Unknown)
        assertEquals(boom, (error as AppError.Unknown).cause)
    }

    @Test
    fun `CancellationException is rethrown, not swallowed`() {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                safeApiCall<Unit> { throw CancellationException("cancelled") }
            }
        }
    }
}
