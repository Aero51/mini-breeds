package com.profico.minibreeds.ui.common

import com.profico.minibreeds.R
import com.profico.minibreeds.core.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down the [AppError] → [UiMessage] (string-resource) mapping that the UI
 * layer uses to render user-facing error text.
 */
class UiErrorTest {

    /**
     * One assertion per [AppError] variant against its expected `R.string.*`
     * id — straight identity check that nothing has been silently rewired.
     */
    @Test
    fun `every error case maps to its expected string resource`() {
        assertEquals(R.string.error_no_connection, AppError.NoConnection.toUiMessage().textRes)
        assertEquals(R.string.error_timeout, AppError.Timeout.toUiMessage().textRes)
        assertEquals(R.string.error_server, AppError.Http(500).toUiMessage().textRes)
        assertEquals(
            R.string.error_unexpected_response,
            AppError.Serialization.toUiMessage().textRes,
        )
        assertEquals(R.string.error_api_status, AppError.ApiStatus("error").toUiMessage().textRes)
        assertEquals(R.string.error_unknown, AppError.Unknown(null).toUiMessage().textRes)
    }

    /**
     * Distinct-resource guard: collects every variant's `textRes`, dedupes,
     * and asserts the set size equals the list size. Prevents two errors from
     * accidentally pointing at the same message.
     */
    @Test
    fun `error cases map to distinct resources`() {
        val resources = listOf(
            AppError.NoConnection,
            AppError.Timeout,
            AppError.Http(500),
            AppError.Serialization,
            AppError.ApiStatus("error"),
            AppError.Unknown(null),
        ).map { it.toUiMessage().textRes }

        assertEquals(resources.size, resources.toSet().size)
    }

    /**
     * The HTTP status code is forwarded as a `formatArgs` value, so the
     * `getString(res, *args)` call in the UI can interpolate it.
     */
    @Test
    fun `http error carries the status code as a format argument`() {
        assertEquals(listOf<Any>(503), AppError.Http(503).toUiMessage().formatArgs)
    }
}
