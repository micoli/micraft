package org.micoli.micraft

import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec

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
        val wsClient = createClient { install(io.ktor.client.plugins.websocket.WebSockets) }
        wsClient.webSocket("/game") {
            send(Frame.Binary(true, ClientMessageCodec.encode(ClientMessage.Connect("TestPlayer"))))
            val frame = incoming.receive()
            assertIs<Frame.Binary>(frame)
            val msg = ServerMessageCodec.decode(frame.readBytes())
            assertIs<ServerMessage.Welcome>(msg)
            assertNotNull(msg.playerId)
        }
    }

    @Test
    fun testWebSocketRegistrySyncSentAfterWelcome() = testApplication {
        application { module() }
        val wsClient = createClient { install(io.ktor.client.plugins.websocket.WebSockets) }
        wsClient.webSocket("/game") {
            send(Frame.Binary(true, ClientMessageCodec.encode(ClientMessage.Connect("TestPlayer"))))

            // First message: Welcome
            val welcomeFrame = incoming.receive()
            assertIs<Frame.Binary>(welcomeFrame)
            val welcomeMsg = ServerMessageCodec.decode(welcomeFrame.readBytes())
            assertIs<ServerMessage.Welcome>(welcomeMsg)

            // Second message: RegistrySync
            val registryFrame = incoming.receive()
            assertIs<Frame.Binary>(registryFrame)
            val registryMsg = ServerMessageCodec.decode(registryFrame.readBytes())
            assertIs<ServerMessage.RegistrySync>(registryMsg)

            assertTrue(
                registryMsg.blocks.isNotEmpty(), "RegistrySync must contain block definitions")
            assertTrue(registryMsg.items.isNotEmpty(), "RegistrySync must contain item definitions")

            // Verify first block is AIR
            assertEquals("AIR", registryMsg.blocks[0].name)
            assertEquals(0f, registryMsg.blocks[0].hardness)

            // Verify COBBLESTONE is buildable
            val cobble = registryMsg.items["COBBLESTONE"]
            assertNotNull(cobble)
            assertTrue(cobble.buildable)
            assertEquals("STONE", cobble.placesBlock)
        }
    }
}
