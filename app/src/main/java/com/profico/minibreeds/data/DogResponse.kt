package com.profico.minibreeds.data

import kotlinx.serialization.Serializable

/**
 * Response shape of `GET api/breeds/list/all`.
 *
 * `message` maps each breed name to its list of sub-breeds; `status` is
 * "success" on a healthy response.
 */
@Serializable
data class DogResponse(
    val message: Map<String, List<String>>,
    val status: String,
)
