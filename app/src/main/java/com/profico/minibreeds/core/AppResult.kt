package com.profico.minibreeds.core

/**
 * Result type used across layer boundaries instead of thrown exceptions,
 * so every caller is forced to handle the failure path explicitly.
 */
sealed interface AppResult<out T> {

    /** Wraps a successfully computed value. */
    data class Success<out T>(val value: T) : AppResult<T>

    /** Wraps a typed error that prevented computation. */
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Transforms the success value with [transform]; passes failures through unchanged. */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

/** Runs [action] on the success value; returns [this] for chaining. */
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

/** Runs [action] with the error on failure; returns [this] for chaining. */
inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}
