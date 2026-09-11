package org.micoli.micraft.game.world

import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator
import org.micoli.micraft.protocol.SUPERSEDED_CONNECTION_CLOSE_CODE
import org.micoli.micraft.support.FakeWebSocketSession
import org.micoli.micraft.support.testSession

private val shared by lazy { SharedGameServices.default() }

private fun gen() = EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1)

class GameWorldOnPlayerJoinTest {

    @Test
    fun onPlayerJoin_closesOldSocketWithSupersededCode_whenSamePlayerIdReconnects() = runBlocking {
        val world = buildGameWorld("join-test", gen(), shared)
        val first = testSession(id = "shared-id", name = "Morlin")
        val second = testSession(id = "shared-id", name = "Morlin")

        world.onPlayerJoin(first)
        world.onPlayerJoin(second)

        val closeFrame =
            (first.socket as FakeWebSocketSession).outgoingChannel.tryReceive().getOrNull()
                as? Frame.Close
        assertNotNull(closeFrame, "the superseded session's socket must receive a close frame")
        assertEquals(SUPERSEDED_CONNECTION_CLOSE_CODE, closeFrame.readReason()?.code)
    }
}
