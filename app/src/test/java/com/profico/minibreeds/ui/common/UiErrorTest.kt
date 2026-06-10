package com.profico.minibreeds.ui.common

import com.profico.minibreeds.R
import com.profico.minibreeds.core.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

class UiErrorTest {

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

    @Test
    fun `http error carries the status code as a format argument`() {
        assertEquals(listOf<Any>(503), AppError.Http(503).toUiMessage().formatArgs)
    }
}
