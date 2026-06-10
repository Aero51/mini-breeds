package com.profico.minibreeds.data.remote

import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.core.AppResult
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * The single place where transport-level exceptions are caught and translated
 * into the typed [AppError] taxonomy. Layers above the data layer never see
 * raw exceptions from networking or parsing.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        // Cancellation must propagate so structured concurrency keeps working.
        throw e
    } catch (t: Throwable) {
        AppResult.Failure(t.toAppError())
    }

/** Maps this throwable to the most specific [AppError] case. */
fun Throwable.toAppError(): AppError = when (this) {
    is UnknownHostException, is ConnectException -> AppError.NoConnection
    is SocketTimeoutException -> AppError.Timeout
    is InterruptedIOException -> AppError.Timeout
    is HttpException -> AppError.Http(code())
    is SerializationException -> AppError.Serialization
    // Remaining IO failures (resets, broken pipes, airplane mode mid-request)
    // are connectivity problems from the user's point of view.
    is IOException -> AppError.NoConnection
    else -> AppError.Unknown(this)
}
