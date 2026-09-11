package org.micoli.micraft.game.hub

import io.ktor.websocket.Frame
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.mail.MailPersistence
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.GameWorld
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
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec
import org.micoli.micraft.support.FakeWebSocketSession
import org.micoli.micraft.support.testI18n

private val shared by lazy { SharedGameServices.default() }

private fun gen() = EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1)

/** End-to-end coverage of `/hub` reusing the real feature managers — see the web-companion plan. */
class HubManagerRoundTripTest {
    private val scope = CoroutineScope(Dispatchers.Default)

    private fun world(persistence: WorldPersistence): GameWorld =
        buildGameWorld(
            "hub-roundtrip-test", gen(), shared, GameWorldOptions(persistence = persistence))

    private fun registryFor(gw: GameWorld) =
        GameWorldRegistry(gw, false) { error("no dynamic worlds") }

    private fun save(persistence: WorldPersistence, id: String, name: String, email: String) {
        persistence.savePlayerState(
            name,
            PlayerState(
                id = id,
                name = name,
                pos = Vec3(8f, 8f, 8f),
                orientation = Orientation(0f, 0f),
                email = email))
    }

    private fun outgoingMessages(socket: FakeWebSocketSession): List<ServerMessage> =
        generateSequence { socket.outgoingChannel.tryReceive().getOrNull() }
            .filterIsInstance<Frame.Binary>()
            .map { ServerMessageCodec.decode(it.data) }
            .toList()

    @Test
    fun sendMail_toOfflinePlayer_persistsAndSyncsOnTheirNextHubConnection() = runBlocking {
        val store = TokenStore(scope)
        val dir = createTempDirectory("hub-mail-test")
        val persistence = WorldPersistence(dir)
        save(persistence, "alice-id", "Alice", "alice@test.local")
        save(persistence, "bob-id", "Bob", "bob@test.local")
        val gw = world(persistence)
        val registry = registryFor(gw)

        // Alice connects, sends mail to Bob (offline), disconnects.
        val aliceSocket = FakeWebSocketSession()
        val aliceToken =
            store.issue(
                AuthResult(
                    playerId = "alice-id", displayName = "Alice", email = "alice@test.local"))
        aliceSocket.incomingChannel.send(
            Frame.Binary(
                true,
                ClientMessageCodec.encode(
                    ClientMessage.Connect(playerName = "Alice", token = aliceToken))))
        aliceSocket.incomingChannel.send(
            Frame.Binary(
                true,
                ClientMessageCodec.encode(
                    ClientMessage.SendMail(to = "Bob", subject = "hi", body = "from the hub"))))
        aliceSocket.incomingChannel.close()
        HubConnection(registry, store, null, testI18n()).handle(aliceSocket, null)

        val mailPersistence = MailPersistence(dir.resolve("players"))
        assertTrue(
            mailPersistence.loadMails("Bob").any { it.subject == "hi" && it.from == "Alice" },
            "the mail must be persisted for the offline recipient")

        // Bob connects later — his hub session's initial MailSync must contain it.
        val bobSocket = FakeWebSocketSession()
        val bobToken =
            store.issue(
                AuthResult(playerId = "bob-id", displayName = "Bob", email = "bob@test.local"))
        bobSocket.incomingChannel.send(
            Frame.Binary(
                true,
                ClientMessageCodec.encode(
                    ClientMessage.Connect(playerName = "Bob", token = bobToken))))
        bobSocket.incomingChannel.close()
        HubConnection(registry, store, null, testI18n()).handle(bobSocket, null)

        val sync =
            outgoingMessages(bobSocket).filterIsInstance<ServerMessage.MailSync>().lastOrNull()
        assertNotNull(sync, "Bob's hub connection must receive a MailSync")
        assertTrue(sync.mails.any { it.subject == "hi" && it.from == "Alice" })
    }

    @Test
    fun hubConnection_forAPlayerAlreadyInGame_attachesInsteadOfCreatingASecondSession() =
        runBlocking {
            val store = TokenStore(scope)
            val dir = createTempDirectory("hub-attach-test")
            val persistence = WorldPersistence(dir)
            save(persistence, "alice-id", "Alice", "alice@test.local")
            val gw = world(persistence)
            val registry = registryFor(gw)

            val gameSocket = FakeWebSocketSession()
            val gameSession =
                PlayerSession(
                    id = "alice-id",
                    userName = "alice@test.local",
                    socket = gameSocket,
                    state =
                        PlayerState(
                            id = "alice-id",
                            name = "Alice",
                            pos = Vec3(8f, 8f, 8f),
                            orientation = Orientation(0f, 0f),
                            email = "alice@test.local"),
                )
            gw.sessions["alice-id"] = gameSession

            val hubSocket = FakeWebSocketSession()
            val token =
                store.issue(
                    AuthResult(
                        playerId = "alice-id", displayName = "Alice", email = "alice@test.local"))
            hubSocket.incomingChannel.send(
                Frame.Binary(
                    true,
                    ClientMessageCodec.encode(
                        ClientMessage.Connect(playerName = "Alice", token = token))))

            val job = launch {
                HubConnection(registry, store, null, testI18n()).handle(hubSocket, null)
            }
            hubSocket.outgoingChannel.receive() // first sync message — attach is done

            // A broadcast after attach must fan out to both sockets of the one PlayerSession.
            gameSession.send(ServerMessage.Notification("hello"))
            assertTrue(
                outgoingMessages(gameSocket).filterIsInstance<ServerMessage.Notification>().any {
                    it.message == "hello"
                })
            assertTrue(
                outgoingMessages(hubSocket).filterIsInstance<ServerMessage.Notification>().any {
                    it.message == "hello"
                })
            assertEquals(
                1, gw.sessions.all().count { it.id == "alice-id" }, "must stay a single session")

            hubSocket.incomingChannel.close()
            job.join()
        }
}
