package com.profico.minibreeds.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire format of every dog.ceo endpoint: a `message` payload wrapped in an
 * envelope with a `status` field that is "success" on a healthy response.
 *
 * [T] is the payload shape — a breed→sub-breeds map for
 * `GET api/breeds/list/all`, a single image URL string for
 * `GET api/breed/{breed}/images/random`.
 */
@Serializable
data class DogResponseDto<T>(
    val message: T,
    val status: String = "",
) {
    companion object {
        const val STATUS_SUCCESS = "success"
    }
}
