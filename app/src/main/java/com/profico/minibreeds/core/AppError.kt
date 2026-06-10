package com.profico.minibreeds.core

/**
 * Typed error taxonomy for the whole app. Every failure that crosses a layer
 * boundary is represented as one of these cases, never as a raw exception.
 */
sealed interface AppError {

    /** Device has no usable network connection or the host is unreachable. */
    data object NoConnection : AppError

    /** The request was sent but the server did not respond in time. */
    data object Timeout : AppError

    /** The server answered with a non-2xx HTTP status code. */
    data class Http(val code: Int) : AppError

    /** The response body could not be parsed into the expected shape. */
    data object Serialization : AppError

    /** The API responded with 2xx but its own "status" field signals failure. */
    data class ApiStatus(val status: String) : AppError

    /** Anything not covered by the cases above. */
    data class Unknown(val cause: Throwable? = null) : AppError
}
