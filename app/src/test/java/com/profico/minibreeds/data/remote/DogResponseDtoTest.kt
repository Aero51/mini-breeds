package com.profico.minibreeds.data.remote

import com.profico.minibreeds.data.remote.dto.DogResponseDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DogResponseDtoTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `parses spec-shaped breeds payload into breed map`() {
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

        val dto = json.decodeFromString<DogResponseDto<Map<String, List<String>>>>(payload)

        assertEquals("success", dto.status)
        assertEquals(5, dto.message.size)
        assertEquals(listOf("boston", "french"), dto.message["bulldog"])
        assertEquals(emptyList<String>(), dto.message["akita"])
    }

    @Test
    fun `parses image payload into url string`() {
        val payload = """{"message":"https://images.dog.ceo/breeds/akita/1.jpg","status":"success"}"""

        val dto = json.decodeFromString<DogResponseDto<String>>(payload)

        assertEquals("success", dto.status)
        assertEquals("https://images.dog.ceo/breeds/akita/1.jpg", dto.message)
    }

    @Test
    fun `ignores unknown top-level keys`() {
        val payload = """{"message":{"akita":[]},"status":"success","extra":"ignored"}"""

        val dto = json.decodeFromString<DogResponseDto<Map<String, List<String>>>>(payload)

        assertEquals(setOf("akita"), dto.message.keys)
    }

    @Test
    fun `missing status falls back to empty string`() {
        val dto = json.decodeFromString<DogResponseDto<Map<String, List<String>>>>(
            """{"message":{"akita":[]}}""",
        )

        assertEquals("", dto.status)
    }

    @Test
    fun `missing message throws SerializationException`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<DogResponseDto<Map<String, List<String>>>>("{}")
        }
    }

    @Test
    fun `malformed body throws SerializationException`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<DogResponseDto<Map<String, List<String>>>>(
                """{"message":"not a map"}""",
            )
        }
    }
}
