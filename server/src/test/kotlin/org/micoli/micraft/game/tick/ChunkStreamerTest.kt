package org.micoli.micraft.game.tick

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class ChunkStreamerTest {
    @Test
    fun deliverReady_emptyPool_doesNotThrow() =
        runBlocking<Unit> {
            val streamer = ChunkStreamer(testWorld())
            val session = testSession()
            streamer.deliverReady(session)
        }

    @Test
    fun cleanupSession_doesNotThrow() {
        val streamer = ChunkStreamer(testWorld())
        val session = testSession()
        streamer.checkAndRequest(session)
        streamer.cleanupSession(session.id)
    }

    @Test
    fun checkAndRequest_queuesChunksForSession() {
        val streamer = ChunkStreamer(testWorld())
        val session = testSession(pos = Vec3(8f, 8f, 8f))
        streamer.checkAndRequest(session)
        // After request, session should have some in-flight or pending chunks
        val hasPendingWork = session.inFlightChunks.isNotEmpty()
        // Either chunks are queued in-flight or they were delivered synchronously (possible for
        // fast paths)
        assertTrue(hasPendingWork || session.lastChunkPos != null)
    }

    @Test
    fun checkAndRequest_setsLastChunkPos() {
        val streamer = ChunkStreamer(testWorld())
        val session = testSession(pos = Vec3(8f, 8f, 8f))
        streamer.checkAndRequest(session)
        val expected = ChunkPos(0, 0)
        assertTrue(session.lastChunkPos == expected || session.inFlightChunks.isNotEmpty())
    }

    @Test
    fun requestAround_doesNotThrowForFarChunks() {
        val streamer = ChunkStreamer(testWorld())
        val session = testSession(pos = Vec3(1000f, 8f, 1000f))
        streamer.requestAround(session, 62, 62)
    }

    @Test
    fun worldStreamingDisabled_noChunksQueued() {
        val streamer = ChunkStreamer(testWorld())
        val session = testSession(pos = Vec3(8f, 8f, 8f)).also { it.worldStreaming = false }
        streamer.checkAndRequest(session)
        streamer.requestAround(session, 0, 0)
        assertTrue(session.inFlightChunks.isEmpty() && session.lastChunkPos == null)
    }

    @Test
    fun cleanupSession_unknownSession_doesNotThrow() {
        val streamer = ChunkStreamer(testWorld())
        streamer.cleanupSession("nonexistent-session-id")
    }
}
