package org.micoli.micraft.game

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.protocol.CHUNK_HANDSHAKE_SEPARATOR
import org.micoli.micraft.support.FakeWebSocketSession
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class ChunkConnectAuthTest {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val sep = CHUNK_HANDSHAKE_SEPARATOR

    private fun registryWith(characterId: String, email: String): SessionRegistry {
        val registry = SessionRegistry()
        val session = testSession(id = characterId)
        session.state = session.state.copy(email = email)
        registry[characterId] = session
        return registry
    }

    /** Runs onChunkConnect with the socket kept open and reports the attached socket, if any. */
    private suspend fun attachedSocketAfter(
        gameLoop: GameLoop,
        registry: SessionRegistry,
        characterId: String,
        firstFrame: String,
    ): Any? {
        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(Frame.Text(firstFrame))
        val job = CoroutineScope(Dispatchers.Default).async { gameLoop.onChunkConnect(socket) }
        delay(200)
        val attached = registry[characterId]?.chunkSocket
        socket.incomingChannel.close()
        withTimeout(2_000) { job.await() }
        assertNull(registry[characterId]?.chunkSocket, "detached once the socket closes")
        return attached
    }

    @Test
    fun `token of the owning account attaches to the character session`() =
        runBlocking<Unit> {
            val store = TokenStore(scope)
            val registry = registryWith("char-1", "alice@example.com")
            val gameLoop = GameLoop(testWorld(), tokenStore = store, sessionRegistry = registry)
            val token =
                store.issue(AuthResult(playerId = "alice@example.com", displayName = "Alice"))

            val attached =
                attachedSocketAfter(gameLoop, registry, "char-1", "$token$sep" + "char-1")

            assertNotNull(attached)
        }

    @Test
    fun `token of another account is rejected with a policy violation`() =
        runBlocking<Unit> {
            val store = TokenStore(scope)
            val registry = registryWith("char-1", "alice@example.com")
            val gameLoop = GameLoop(testWorld(), tokenStore = store, sessionRegistry = registry)
            val token = store.issue(AuthResult(playerId = "bob@example.com", displayName = "Bob"))
            val socket = FakeWebSocketSession()
            socket.incomingChannel.send(Frame.Text("$token$sep" + "char-1"))

            gameLoop.onChunkConnect(socket)

            assertNull(registry["char-1"]?.chunkSocket)
            val reason = socket.outgoingChannel.receive() as Frame.Close
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason.readReason()?.code)
        }

    @Test
    fun `invalid token is rejected`() =
        runBlocking<Unit> {
            val store = TokenStore(scope)
            val registry = registryWith("char-1", "alice@example.com")
            val gameLoop = GameLoop(testWorld(), tokenStore = store, sessionRegistry = registry)

            val attached =
                attachedSocketAfter(gameLoop, registry, "char-1", "not-a-valid-jwt$sep" + "char-1")

            assertNull(attached)
        }

    @Test
    fun `expired token is rejected`() =
        runBlocking<Unit> {
            val store = TokenStore(scope, ttlSeconds = -1)
            val registry = registryWith("char-1", "alice@example.com")
            val gameLoop = GameLoop(testWorld(), tokenStore = store, sessionRegistry = registry)
            val token =
                store.issue(AuthResult(playerId = "alice@example.com", displayName = "Alice"))

            val attached =
                attachedSocketAfter(gameLoop, registry, "char-1", "$token$sep" + "char-1")

            assertNull(attached)
        }

    @Test
    fun `without a token store the player id alone attaches`() =
        runBlocking<Unit> {
            val registry = registryWith("char-1", "alice@example.com")
            val gameLoop = GameLoop(testWorld(), sessionRegistry = registry)

            val attached = attachedSocketAfter(gameLoop, registry, "char-1", "${sep}char-1")

            assertNotNull(attached)
        }

    @Test
    fun `valid token for a character with no live session returns gracefully`() =
        runBlocking<Unit> {
            val store = TokenStore(scope)
            val registry = SessionRegistry()
            val gameLoop = GameLoop(testWorld(), tokenStore = store, sessionRegistry = registry)
            val token =
                store.issue(AuthResult(playerId = "ghost@example.com", displayName = "Ghost"))
            val socket = FakeWebSocketSession()
            socket.incomingChannel.send(Frame.Text("$token$sep" + "ghost-char"))

            gameLoop.onChunkConnect(socket)
        }
}
