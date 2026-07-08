package org.micoli.micraft.game

import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Plugin
import org.micoli.micraft.game.session.PlayerSession
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
        val connect = ClientMessage.Connect(playerName = "Alice")
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
    fun autocomplete_unknownCommandId_returnsEmptyList() = runTest {
        val gameLoop = GameLoop(testWorld())
        val result = gameLoop.autocomplete(UUID.randomUUID().toString(), 0, "", "Alice")
        assertTrue(result.isEmpty())
    }
}
