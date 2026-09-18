package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class OllamaClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun ollamaEnvelope(contentJson: String): String =
        """{"model":"gemma:2b","message":{"role":"assistant","content":${JsonPrimitive(contentJson)}},"done":true}"""

    @Test
    fun wellFormedResponse_parsesReplyAndAction() {
        val response =
            ollamaEnvelope(
                """{"reply":"Greetings, traveler!","action":{"type":"offer_quest","questId":"q1"}}""")
        val result = parseOllamaChatResponse(response, json)
        assertEquals("Greetings, traveler!", result?.reply)
        assertEquals("offer_quest", result?.actionType)
        assertEquals("q1", result?.questId)
    }

    @Test
    fun noActionField_defaultsToNone() {
        val response = ollamaEnvelope("""{"reply":"Hi there."}""")
        val result = parseOllamaChatResponse(response, json)
        assertEquals("Hi there.", result?.reply)
        assertEquals("none", result?.actionType)
    }

    @Test
    fun missingMessageField_returnsNull() {
        assertNull(parseOllamaChatResponse("""{"done":true}""", json))
    }

    @Test
    fun missingReplyField_returnsNull() {
        val response = ollamaEnvelope("""{"action":{"type":"none"}}""")
        assertNull(parseOllamaChatResponse(response, json))
    }

    @Test
    fun malformedOuterJson_returnsNull() {
        assertNull(parseOllamaChatResponse("not json at all", json))
    }

    @Test
    fun malformedInnerContentJson_returnsNull() {
        val response = """{"message":{"content":"not json at all"}}"""
        assertNull(parseOllamaChatResponse(response, json))
    }
}
