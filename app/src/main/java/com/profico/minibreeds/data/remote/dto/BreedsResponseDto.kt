package com.profico.minibreeds.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire format of GET https://dog.ceo/api/breeds/list/all.
 *
 * The root keys of [message] are breed names; each value is the (possibly
 * empty) list of sub-breeds. [status] is "success" on a healthy response.
 */
@Serializable
data class BreedsResponseDto(
    val message: Map<String, List<String>> = emptyMap(),
    val status: String = "",
) {
    companion object {
        const val STATUS_SUCCESS = "success"
    }
}
