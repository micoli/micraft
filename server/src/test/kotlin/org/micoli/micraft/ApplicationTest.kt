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
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec

class ApplicationTest {

    @Test
    fun applyE2eWorldOverrides_spawnsJustAboveTheGroundAndShrinksTheView() {
        val spawn = org.micoli.micraft.game.SPAWN_Y
        val viewRadius = WorldConstants.VIEW_RADIUS
        val forwardViewRadius = WorldConstants.FORWARD_VIEW_RADIUS
        val waterLevel = WorldConstants.WATER_LEVEL
        try {
            applyE2eWorldOverrides(groundY = 64)
            assertEquals(72f, org.micoli.micraft.game.SPAWN_Y, "spawn 8 blocks above ground")
            assertEquals(3, WorldConstants.FORWARD_VIEW_RADIUS)
            assertEquals(0, WorldConstants.WATER_LEVEL)
        } finally {
            org.micoli.micraft.game.SPAWN_Y = spawn
            WorldConstants.VIEW_RADIUS = viewRadius
            WorldConstants.FORWARD_VIEW_RADIUS = forwardViewRadius
            WorldConstants.WATER_LEVEL = waterLevel
        }
    }

    @Test
    fun testAssetManifestReturnsJsonObject() = testApplication {
        application { module() }
        val r = client.get("/api/assets/manifest")
        assertEquals(HttpStatusCode.OK, r.status)
        // MICRAFT_WEB_DIST absent in tests → {} is a valid empty JSON object
        Json.parseToJsonElement(r.bodyAsText()).jsonObject
    }

    @Test
    fun testWebSocketWelcome() = testApplication {
        application { module() }
        val wsClient = createClient { install(io.ktor.client.plugins.websocket.WebSockets) }
        wsClient.webSocket("/game") {
            send(
                Frame.Binary(
                    true,
                    ClientMessageCodec.encode(
                        ClientMessage.Connect(
                            playerName = "TestPlayer", userName = "test@example.com"))))
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
            send(
                Frame.Binary(
                    true,
                    ClientMessageCodec.encode(
                        ClientMessage.Connect(
                            playerName = "TestPlayer", userName = "test@example.com"))))

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
