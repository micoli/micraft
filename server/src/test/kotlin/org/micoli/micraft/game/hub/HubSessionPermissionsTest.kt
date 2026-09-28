package org.micoli.micraft.game.hub

import io.ktor.websocket.Frame
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.auth.AuthResult
import org.micoli.micraft.auth.GroupEntry
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.auth.Permission
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

class HubSessionPermissionsTest {
    private val scope = CoroutineScope(Dispatchers.Default)

    @Test
    fun companionSession_takesTheCharactersGroupPermissions_notTheAccounts() = runBlocking {
        val store = TokenStore(scope)
        val persistence = WorldPersistence(createTempDirectory("hub-permissions-test"))
        persistence.savePlayerState(
            "Alice",
            PlayerState(
                id = "alice-id",
                name = "Alice",
                pos = Vec3(8f, 8f, 8f),
                orientation = Orientation(0f, 0f),
                email = "alice@test.local",
                groups = listOf("builder")))
        val token =
            store.issue(
                AuthResult(
                    playerId = "alice-id",
                    displayName = "Alice",
                    email = "alice@test.local",
                    permissions = setOf(Permission.WILDCARD)))
        val socket = FakeWebSocketSession()
        socket.incomingChannel.send(
            Frame.Binary(
                true,
                ClientMessageCodec.encode(
                    ClientMessage.Connect(playerName = "Alice", token = token))))
        val gw =
            buildGameWorld(
                "hub-permissions-test",
                EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1),
                SharedGameServices.default(),
                GameWorldOptions(persistence = persistence))
        val groups = GroupsConfig(groups = listOf(GroupEntry("builder", listOf("claim:build"))))

        val job = launch {
            HubConnection(GameWorldRegistry(gw, false) { error("n/a") }, store, testI18n(), groups)
                .handle(socket, null)
        }
        socket.outgoingChannel.receive()

        val companion = assertNotNull(gw.sessions.companion("alice-id"))
        assertEquals(setOf(Permission("claim:build")), companion.permissions)

        socket.incomingChannel.close()
        job.join()
    }
}
