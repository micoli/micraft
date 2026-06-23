package org.micoli.micraft

import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testVersionEndpoint() = testApplication {
        application { module() }
        val r = client.get("/api/version")
        assertEquals(HttpStatusCode.OK, r.status)
        val json = Json.parseToJsonElement(r.bodyAsText()).jsonObject
        val server = json["server"]?.jsonPrimitive?.content
        assertNotNull(server)
        assertTrue(server.isNotEmpty())
    }

    @Test
    fun testVersionStableWithinProcess() = testApplication {
        application { module() }
        val r1 = client.get("/api/version").bodyAsText()
        val r2 = client.get("/api/version").bodyAsText()
        assertEquals(r1, r2)
    }

    @Test
    fun testWebSocketWelcome() = testApplication {
        application { module() }
        val wsClient = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }
        wsClient.webSocket("/game") {
            send(Frame.Text(Json.encodeToString<ClientMessage>(ClientMessage.Connect("TestPlayer"))))
            val frame = incoming.receive()
            assertIs<Frame.Text>(frame)
            val msg = Json.decodeFromString<ServerMessage>(frame.readText())
            assertIs<ServerMessage.Welcome>(msg)
            assertNotNull(msg.playerId)
        }
    }
}
