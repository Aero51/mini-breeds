package com.profico.minibreeds.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BreedMappingTest {

    @Test
    fun `toBreeds maps payload and sorts by name`() {
        val response = DogResponse(
            message = mapOf(
                "bulldog" to listOf("boston", "french"),
                "akita" to emptyList(),
            ),
            status = "success",
        )

        val breeds = response.toBreeds()

        assertEquals(listOf("akita", "bulldog"), breeds.map { it.name })
        assertEquals(listOf("boston", "french"), breeds.first { it.name == "bulldog" }.subBreeds)
    }
}
