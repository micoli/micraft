package org.micoli.micraft

import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ApplicationTest {

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
