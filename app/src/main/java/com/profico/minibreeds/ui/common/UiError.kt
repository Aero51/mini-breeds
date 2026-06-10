package com.profico.minibreeds.ui.common

import androidx.annotation.StringRes
import com.profico.minibreeds.R
import com.profico.minibreeds.core.AppError

/** User-facing message for each error case; formatting args where relevant. */
data class UiErrorMessage(
    @field:StringRes val textRes: Int,
    val formatArgs: List<Any> = emptyList(),
)

/** Maps this [AppError] to a localized [UiErrorMessage] suitable for display in the UI. */
fun AppError.toUiMessage(): UiErrorMessage = when (this) {
    AppError.NoConnection -> UiErrorMessage(R.string.error_no_connection)
    AppError.Timeout -> UiErrorMessage(R.string.error_timeout)
    is AppError.Http -> UiErrorMessage(R.string.error_server, listOf(code))
    AppError.Serialization -> UiErrorMessage(R.string.error_unexpected_response)
    is AppError.ApiStatus -> UiErrorMessage(R.string.error_api_status)
    is AppError.Unknown -> UiErrorMessage(R.string.error_unknown)
}
