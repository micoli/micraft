package org.micoli.micraft.game.hub

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.world.GameWorldOptions
import org.micoli.micraft.game.world.GameWorldRegistry
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.buildGameWorld
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.support.FakeWebSocketSession
import org.micoli.micraft.support.testI18n

private val shared by lazy { SharedGameServices.default() }

private fun gen() = EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1)

class HubConnectAuthTest {
    private val scope = CoroutineScope(Dispatchers.Default)

    private fun registry(persistence: WorldPersistence? = null) =
        GameWorldRegistry(
            defaultWorld =
                buildGameWorld(
                    "hub-auth-test", gen(), shared, GameWorldOptions(persistence = persistence)),
            e2eEnabled = false,
            factory = { error("no dynamic worlds in this test") },
        )

    @Test
    fun handle_invalidToken_closesWithViolatedPolicy() = runBlocking {
        val store = TokenStore(scope)
        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(
            Frame.Binary(
                true,
                ClientMessageCodec.encode(
                    ClientMessage.Connect(playerName = "Alice", token = "not-a-valid-jwt"))))
        socket.incomingChannel.close()

        HubConnection(registry(), store, null, testI18n()).handle(socket, null)

        val closeFrame = socket.outgoingChannel.tryReceive().getOrNull() as? Frame.Close
        assertNotNull(closeFrame, "an invalid token must close the socket")
        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, closeFrame.readReason()?.code)
    }

    @Test
    fun handle_noPersistence_closesWithoutRegisteringACompanion() = runBlocking {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult(playerId = "p1", displayName = "Alice"))
        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(
            Frame.Binary(
                true,
                ClientMessageCodec.encode(
                    ClientMessage.Connect(playerName = "Alice", token = token))))
        socket.incomingChannel.close()

        val gw = registry(persistence = null).defaultWorld
        HubConnection(GameWorldRegistry(gw, false) { error("n/a") }, store, null, testI18n())
            .handle(socket, null)

        val closeFrame = socket.outgoingChannel.tryReceive().getOrNull() as? Frame.Close
        assertNotNull(closeFrame, "a world with no persistence can't serve a hub connection")
        assertNull(gw.sessions.companion("p1"))
    }

    @Test
    fun handle_unknownCharacter_closesWithoutRegisteringACompanion() = runBlocking {
        val store = TokenStore(scope)
        val token = store.issue(AuthResult(playerId = "p1", displayName = "alice@test.local"))
        val dir = createTempDirectory("hub-test-world")
        val persistence = WorldPersistence(dir)
        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(
            Frame.Binary(
                true,
                ClientMessageCodec.encode(
                    ClientMessage.Connect(playerName = "GhostPlayer", token = token))))
        socket.incomingChannel.close()

        val gw = registry(persistence).defaultWorld
        HubConnection(GameWorldRegistry(gw, false) { error("n/a") }, store, null, testI18n())
            .handle(socket, null)

        val closeFrame = socket.outgoingChannel.tryReceive().getOrNull() as? Frame.Close
        assertNotNull(closeFrame, "a character that doesn't exist must be rejected")
        Unit
    }

    @Test
    fun handle_offlinePlayer_registersACompanionSessionExcludedFromPlaying() = runBlocking {
        val store = TokenStore(scope)
        val dir = createTempDirectory("hub-test-world")
        val persistence = WorldPersistence(dir)
        persistence.savePlayerState(
            "Alice",
            PlayerState(
                id = "alice-id",
                name = "Alice",
                pos = Vec3(8f, 8f, 8f),
                orientation = Orientation(0f, 0f),
                email = "alice@test.local"))
        val token =
            store.issue(
                AuthResult(
                    playerId = "alice-id", displayName = "Alice", email = "alice@test.local"))

        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(
            Frame.Binary(
                true,
                ClientMessageCodec.encode(
                    ClientMessage.Connect(playerName = "Alice", token = token))))
        // Deliberately NOT closing incomingChannel yet: handle() would otherwise run its whole
        // connect-then-disconnect lifecycle synchronously and there would be nothing left to
        // observe mid-connection. Run it in the background and wait for the first post-connect
        // sync message — by then the companion is registered and handle() is parked reading the
        // next (never-sent) frame.

        val gw = registry(persistence).defaultWorld
        val job = launch {
            HubConnection(GameWorldRegistry(gw, false) { error("n/a") }, store, null, testI18n())
                .handle(socket, null)
        }
        socket.outgoingChannel.receive() // first sync message (WalletUpdate) — registration is done

        assertNotNull(
            gw.sessions.companion("alice-id"), "the offline player must get a companion session")
        assertNull(
            gw.sessions["alice-id"], "a hub-only session must never register as a playing session")
        assertTrue(gw.sessions.all().any { it.id == "alice-id" })
        assertTrue(gw.sessions.playing().none { it.id == "alice-id" })

        socket.incomingChannel.close()
        job.join()
        assertNull(
            gw.sessions.companion("alice-id"), "disconnecting must drop the companion session")
    }
}
