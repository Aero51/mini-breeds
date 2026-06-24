package com.profico.minibreeds.data

/**
 * A dog breed.
 *
 * @property name Lowercase breed name from the API (e.g. "bulldog").
 * @property subBreeds Sub-breed names; empty when the breed has none.
 */
data class Breed(
    val name: String,
    val subBreeds: List<String>,
)
