package org.micoli.micraft.game

import io.ktor.websocket.*
import kotlin.test.Test
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.support.FakeWebSocketSession
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class ChunkConnectAuthTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    @Test
    fun `onChunkConnect_valid_token_attaches_then_detaches_socket`() = runBlocking {
        val store = TokenStore(scope)
        val registry = SessionRegistry()
        val gameLoop = GameLoop(testWorld(), tokenStore = store, sessionRegistry = registry)

        val token = store.issue(AuthResult(playerId = "p1", displayName = "Alice"))
        registry["p1"] = testSession(id = "p1")

        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(Frame.Text(token))
        socket.incomingChannel.close()

        gameLoop.onChunkConnect(socket)

        assertNull(registry["p1"]?.chunkSocket)
    }

    @Test
    fun `onChunkConnect_invalid_token_rejects_without_attaching_session`() = runBlocking {
        val store = TokenStore(scope)
        val registry = SessionRegistry()
        val gameLoop = GameLoop(testWorld(), tokenStore = store, sessionRegistry = registry)
        registry["p1"] = testSession(id = "p1")

        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(Frame.Text("not-a-valid-jwt"))
        socket.incomingChannel.close()

        gameLoop.onChunkConnect(socket)

        assertNull(registry["p1"]?.chunkSocket)
    }

    @Test
    fun `onChunkConnect_expired_token_rejects_connection`() = runBlocking {
        val store = TokenStore(scope, ttlSeconds = -1)
        val registry = SessionRegistry()
        val gameLoop = GameLoop(testWorld(), tokenStore = store, sessionRegistry = registry)

        val token = store.issue(AuthResult(playerId = "p1", displayName = "Alice"))
        registry["p1"] = testSession(id = "p1")

        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(Frame.Text(token))
        socket.incomingChannel.close()

        gameLoop.onChunkConnect(socket)

        assertNull(registry["p1"]?.chunkSocket)
    }

    @Test
    fun `onChunkConnect_no_token_store_uses_player_id_directly`() = runBlocking {
        val registry = SessionRegistry()
        val gameLoop = GameLoop(testWorld(), sessionRegistry = registry)
        registry["p1"] = testSession(id = "p1")

        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(Frame.Text("p1"))
        socket.incomingChannel.close()

        gameLoop.onChunkConnect(socket)

        assertNull(registry["p1"]?.chunkSocket)
    }

    @Test
    fun `onChunkConnect_token_wrong_player_not_in_registry_returns_gracefully`() = runBlocking {
        val store = TokenStore(scope)
        val registry = SessionRegistry()
        val gameLoop = GameLoop(testWorld(), tokenStore = store, sessionRegistry = registry)

        val token = store.issue(AuthResult(playerId = "unknown-player", displayName = "Ghost"))

        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(Frame.Text(token))
        socket.incomingChannel.close()

        gameLoop.onChunkConnect(socket)
    }
}
