package org.micoli.micraft.game

import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Plugin
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec
import org.micoli.micraft.support.FakeWebSocketSession
import org.micoli.micraft.support.testWorld

private class FakeCommandHandler(override val id: UUID, override val name: String) :
    CommandHandler {
    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {}
}

private class FakePlugin(override val id: UUID, override val name: String) : Plugin

class GameLoopTest {

    @Test
    fun discoverCommandHandlers_findsBuiltinCommands() {
        val commands = discoverCommandHandlers()
        assertTrue(commands.isNotEmpty())
        assertTrue(commands.values.all { it.command.startsWith("/") })
    }

    @Test
    fun validatePluginSystemIds_noDuplicates_doesNotThrow() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val commands =
            mapOf("a" to FakeCommandHandler(id1, "a"), "b" to FakeCommandHandler(id2, "b"))
        validatePluginSystemIds(commands, emptyList())
    }

    @Test
    fun validatePluginSystemIds_duplicateCommandIds_throws() {
        val sharedId = UUID.randomUUID()
        val commands =
            mapOf(
                "a" to FakeCommandHandler(sharedId, "a"),
                "b" to FakeCommandHandler(sharedId, "b"),
            )
        val ex =
            assertFailsWith<IllegalStateException> {
                validatePluginSystemIds(commands, emptyList())
            }
        assertTrue(ex.message!!.contains("Duplicate command UUIDs"))
    }

    @Test
    fun validatePluginSystemIds_duplicatePluginIds_throws() {
        val sharedId = UUID.randomUUID()
        val plugins = listOf(FakePlugin(sharedId, "p1"), FakePlugin(sharedId, "p2"))
        val ex =
            assertFailsWith<IllegalStateException> { validatePluginSystemIds(emptyMap(), plugins) }
        assertTrue(ex.message!!.contains("Duplicate plugin UUIDs"))
    }

    @Test
    fun freshGameLoop_hasNoPlayersAndDefaultGameTicks() {
        val gameLoop = GameLoop(testWorld())
        assertTrue(gameLoop.getPlayerStates().isEmpty())
        assertEquals(18_000L, gameLoop.getGameTicks())
        assertEquals(0, gameLoop.getWorldItemCount())
        assertEquals(0, gameLoop.getActiveLiquidCount())
    }

    @Test
    fun onConnect_sendsWelcomeAndRegistrationMessages() = runTest {
        val gameLoop = GameLoop(testWorld())
        val socket = FakeWebSocketSession()
        val connect = ClientMessage.Connect(playerName = "Alice", userName = "alice@example.com")
        socket.incomingChannel.trySend(Frame.Binary(true, ClientMessageCodec.encode(connect)))
        socket.incomingChannel.close()

        gameLoop.onConnect(socket)

        val received = mutableListOf<ServerMessage>()
        var frame = socket.outgoingChannel.tryReceive().getOrNull()
        while (frame != null) {
            received.add(ServerMessageCodec.decode((frame as Frame.Binary).readBytes()))
            frame = socket.outgoingChannel.tryReceive().getOrNull()
        }

        assertTrue(received.any { it is ServerMessage.Welcome && it.playerName == "Alice" })
        assertTrue(received.any { it is ServerMessage.RegistrySync })
        assertTrue(received.any { it is ServerMessage.PreferencesSync })
        // player is removed again once the incoming channel closes and the session ends
        assertTrue(gameLoop.getPlayerStates().isEmpty())
    }

    @Test
    fun preferencesUpdate_continuousBreak_roundTripsThroughSync() = runTest {
        val gameLoop = GameLoop(testWorld())
        val socket = FakeWebSocketSession()
        val connect = ClientMessage.Connect(playerName = "Bob", userName = "bob@example.com")
        val prefsUpdate =
            ClientMessage.PreferencesUpdate(
                subscribedChannels = emptyList(),
                disabledCommands = emptySet(),
                shadersEnabled = true,
                continuousBreak = true)
        socket.incomingChannel.trySend(Frame.Binary(true, ClientMessageCodec.encode(connect)))
        socket.incomingChannel.trySend(Frame.Binary(true, ClientMessageCodec.encode(prefsUpdate)))
        socket.incomingChannel.close()

        gameLoop.onConnect(socket)

        val received = mutableListOf<ServerMessage>()
        var frame = socket.outgoingChannel.tryReceive().getOrNull()
        while (frame != null) {
            received.add(ServerMessageCodec.decode((frame as Frame.Binary).readBytes()))
            frame = socket.outgoingChannel.tryReceive().getOrNull()
        }

        val syncs = received.filterIsInstance<ServerMessage.PreferencesSync>()
        assertTrue(syncs.isNotEmpty())
        assertEquals(false, syncs.first().continuousBreak)
        assertEquals(true, syncs.last().continuousBreak)
    }

    @Test
    fun onConnect_reusesPersistedPlayerIdAcrossReconnects() = runTest {
        val persistence = WorldPersistence(Files.createTempDirectory("gameloop-reconnect-test"))
        val gameLoop = GameLoop(testWorld(), persistence)

        val firstSocket = FakeWebSocketSession()
        val connect = ClientMessage.Connect(playerName = "Carol", userName = "carol@example.com")
        firstSocket.incomingChannel.trySend(Frame.Binary(true, ClientMessageCodec.encode(connect)))
        firstSocket.incomingChannel.close()
        gameLoop.onConnect(firstSocket)
        val firstWelcome =
            generateSequence { firstSocket.outgoingChannel.tryReceive().getOrNull() }
                .map { ServerMessageCodec.decode((it as Frame.Binary).readBytes()) }
                .filterIsInstance<ServerMessage.Welcome>()
                .first()

        val secondSocket = FakeWebSocketSession()
        secondSocket.incomingChannel.trySend(Frame.Binary(true, ClientMessageCodec.encode(connect)))
        secondSocket.incomingChannel.close()
        gameLoop.onConnect(secondSocket)
        val secondWelcome =
            generateSequence { secondSocket.outgoingChannel.tryReceive().getOrNull() }
                .map { ServerMessageCodec.decode((it as Frame.Binary).readBytes()) }
                .filterIsInstance<ServerMessage.Welcome>()
                .first()

        assertEquals(firstWelcome.playerId, secondWelcome.playerId)
        assertEquals(persistence.loadPlayerStateById(firstWelcome.playerId)?.name, "Carol")
    }

    @Test
    fun onConnect_preservesOwnedEquipmentAcrossReconnects() = runTest {
        val persistence = WorldPersistence(Files.createTempDirectory("gameloop-owned-test"))
        persistence.savePlayerState(
            "Dave",
            org.micoli.micraft.player.PlayerState(
                id = UUID.randomUUID().toString(),
                name = "Dave",
                pos = org.micoli.micraft.player.Vec3(0f, 0f, 0f),
                orientation = org.micoli.micraft.player.Orientation(0f, 0f),
                ownedArmors = listOf("iron_armor"),
                ownedWeapons = listOf("iron_sword"),
                ownedTools = listOf("iron_pickaxe"),
            ),
        )
        val gameLoop = GameLoop(testWorld(), persistence)
        val socket = FakeWebSocketSession()
        val connect = ClientMessage.Connect(playerName = "Dave", userName = "dave@example.com")
        socket.incomingChannel.trySend(Frame.Binary(true, ClientMessageCodec.encode(connect)))
        socket.incomingChannel.close()

        gameLoop.onConnect(socket)

        val reloaded = persistence.loadPlayerState("Dave")
        assertEquals(listOf("iron_armor"), reloaded?.ownedArmors)
        assertEquals(listOf("iron_sword"), reloaded?.ownedWeapons)
        assertEquals(listOf("iron_pickaxe"), reloaded?.ownedTools)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun onConnect_concurrentSameName_evictsStaleSessionInsteadOfBlockingIt() = runTest {
        val persistence = WorldPersistence(Files.createTempDirectory("gameloop-concurrent-test"))
        val gameLoop = GameLoop(testWorld(), persistence)

        // Save a player file up front so both connects resolve to the same persisted id — a
        // fresh, never-saved name would otherwise get a random id per connect and never collide.
        persistence.savePlayerState(
            "Eve",
            org.micoli.micraft.player.PlayerState(
                id = "eve-fixed-id",
                name = "Eve",
                pos = org.micoli.micraft.player.Vec3(0f, 0f, 0f),
                orientation = org.micoli.micraft.player.Orientation(0f, 0f),
            ),
        )

        val firstSocket = FakeWebSocketSession()
        val connect = ClientMessage.Connect(playerName = "Eve", userName = "eve@example.com")
        firstSocket.incomingChannel.trySend(Frame.Binary(true, ClientMessageCodec.encode(connect)))
        val firstJob = launch { gameLoop.onConnect(firstSocket) }
        // Drive the virtual test dispatcher so firstJob actually starts running and reaches the
        // point where it hops onto Dispatchers.IO (chunk generation) — that real dispatcher isn't
        // controlled by the virtual scheduler, so poll for completion with a real delay after.
        advanceUntilIdle()
        withContext(Dispatchers.Default) {
            var waited = 0
            while (gameLoop.getPlayerStates().isEmpty() && waited < 5000) {
                delay(10)
                waited += 10
            }
        }
        assertEquals(1, gameLoop.getPlayerStates().size)

        val secondSocket = FakeWebSocketSession()
        secondSocket.incomingChannel.trySend(Frame.Binary(true, ClientMessageCodec.encode(connect)))
        val secondJob = launch { gameLoop.onConnect(secondSocket) }
        advanceUntilIdle()
        withContext(Dispatchers.Default) {
            var waited = 0
            while (firstSocket.outgoingChannel.isEmpty && waited < 5000) {
                // wait for the eviction close frame to land on the stale (first) socket
                delay(10)
                waited += 10
            }
        }

        // only the newer session is live and gets ticked
        assertEquals(1, gameLoop.getPlayerStates().size)

        // let the stale session's read loop unwind (as it would once its socket actually closes)
        firstSocket.incomingChannel.close()
        firstJob.join()

        // its finally-block cleanup must not have torn down the session that replaced it
        assertEquals(1, gameLoop.getPlayerStates().size)

        secondSocket.incomingChannel.close()
        secondJob.join()
    }

    @Test
    fun autocomplete_unknownCommandId_returnsEmptyList() = runTest {
        val gameLoop = GameLoop(testWorld())
        val result = gameLoop.autocomplete(UUID.randomUUID().toString(), 0, "", "Alice")
        assertTrue(result.isEmpty())
    }
}
