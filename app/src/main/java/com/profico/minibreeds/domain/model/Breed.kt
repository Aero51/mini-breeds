package com.profico.minibreeds.domain.model

/**
 * Domain model for a dog breed.
 *
 * @property name Lowercase breed name as returned by the API (e.g. "bulldog").
 * @property subBreeds Ordered list of sub-breed names; empty if the breed has none.
 */
data class Breed(
    val name: String,
    val subBreeds: List<String>,
)
