package com.profico.minibreeds.data.remote

import com.profico.minibreeds.data.remote.dto.BreedsResponseDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BreedsResponseDtoTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `parses spec-shaped payload into breed map`() {
        val payload = """
            {
              "message": {
                "bulldog": ["boston", "french"],
                "collie": ["border"],
                "hound": ["afghan", "basset"],
                "retriever": ["golden", "labrador"],
                "akita": []
              },
              "status": "success"
            }
        """.trimIndent()

        val dto = json.decodeFromString<BreedsResponseDto>(payload)

        assertEquals("success", dto.status)
        assertEquals(5, dto.message.size)
        assertEquals(listOf("boston", "french"), dto.message["bulldog"])
        assertEquals(emptyList<String>(), dto.message["akita"])
    }

    @Test
    fun `ignores unknown top-level keys`() {
        val payload = """{"message":{"akita":[]},"status":"success","extra":"ignored"}"""

        val dto = json.decodeFromString<BreedsResponseDto>(payload)

        assertEquals(setOf("akita"), dto.message.keys)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val dto = json.decodeFromString<BreedsResponseDto>("{}")

        assertTrue(dto.message.isEmpty())
        assertEquals("", dto.status)
    }

    @Test
    fun `malformed body throws SerializationException`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<BreedsResponseDto>("""{"message":"not a map"}""")
        }
    }
}
