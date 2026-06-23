package com.profico.minibreeds.data.remote

import com.profico.minibreeds.data.remote.dto.DogResponseDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure JSON contract tests for [DogResponseDto]. The [json] config mirrors the
 * production one so the deserialization tolerances tested here match what the
 * app actually sees in flight.
 */
class DogResponseDtoTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /**
     * The spec-shaped breeds payload (map of breed → sub-breed list) decodes
     * into [DogResponseDto] of `Map<String, List<String>>`, preserving counts,
     * sub-breed contents, and empty sub-breed lists like `akita`.
     */
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

    /**
     * The `random image` endpoint shape uses a plain string for `message`; the
     * same generic DTO carries it through to a `DogResponseDto<String>`.
     */
    @Test
    fun `parses image payload into url string`() {
        val payload = """{"message":"https://images.dog.ceo/breeds/akita/1.jpg","status":"success"}"""

        val dto = json.decodeFromString<DogResponseDto<String>>(payload)

        assertEquals("success", dto.status)
        assertEquals("https://images.dog.ceo/breeds/akita/1.jpg", dto.message)
    }

    /** `ignoreUnknownKeys = true` — extra top-level fields don't fail the decode. */
    @Test
    fun `ignores unknown top-level keys`() {
        val payload = """{"message":{"akita":[]},"status":"success","extra":"ignored"}"""

        val dto = json.decodeFromString<DogResponseDto<Map<String, List<String>>>>(payload)

        assertEquals(setOf("akita"), dto.message.keys)
    }

    /**
     * Missing `status` falls back to the DTO default ("") via
     * `coerceInputValues`, instead of throwing.
     */
    @Test
    fun `missing status falls back to empty string`() {
        val dto = json.decodeFromString<DogResponseDto<Map<String, List<String>>>>(
            """{"message":{"akita":[]}}""",
        )

        assertEquals("", dto.status)
    }

    /** `message` is required — its absence is a hard parse error. */
    @Test
    fun `missing message throws SerializationException`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<DogResponseDto<Map<String, List<String>>>>("{}")
        }
    }

    /**
     * Type mismatches on `message` (string when a map is expected) are fatal,
     * not coerced — protects callers from silently getting wrong shapes.
     */
    @Test
    fun `malformed body throws SerializationException`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<DogResponseDto<Map<String, List<String>>>>(
                """{"message":"not a map"}""",
            )
        }
    }
}
