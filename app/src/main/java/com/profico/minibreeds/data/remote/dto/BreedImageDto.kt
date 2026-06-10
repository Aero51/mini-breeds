package com.profico.minibreeds.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire format of GET https://dog.ceo/api/breed/{breed}/images/random.
 *
 * [message] is the URL of a single random image for the breed.
 * [status] is "success" on a healthy response.
 */
@Serializable
data class BreedImageDto(
    val message: String = "",
    val status: String = "",
) {
    companion object {
        const val STATUS_SUCCESS = "success"
    }
}
