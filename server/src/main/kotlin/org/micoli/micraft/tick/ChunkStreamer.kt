package org.micoli.micraft.tick

import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldState
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger(ChunkStreamer::class.java)

private const val MAX_IN_FLIGHT = 12
private const val MAX_DELIVER_PER_TICK = 3

class ChunkStreamer(private val world: WorldState) {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val readyQueues = ConcurrentHashMap<String, Channel<Pair<ChunkPos, Chunk>>>()

    fun cleanupSession(sessionId: String) {
        readyQueues.remove(sessionId)?.cancel()
    }

    fun checkAndRequest(session: PlayerSession) {
        val newCp = ChunkPos(
            Math.floorDiv(session.state.pos.x.toInt(), WorldConstants.CHUNK_SIZE),
            Math.floorDiv(session.state.pos.z.toInt(), WorldConstants.CHUNK_SIZE),
        )
        val posChanged = newCp != session.lastChunkPos
        if (!posChanged && session.inFlightChunks.size >= MAX_IN_FLIGHT) return
        if (posChanged) session.lastChunkPos = newCp
        requestAround(session, newCp.cx, newCp.cz)
    }

    fun requestAround(session: PlayerSession, cx: Int, cz: Int) {
        val offsets = buildOffsets(session.state.orientation.yaw.toDouble(), cx, cz)
        for ((dx, dz) in offsets) {
            if (session.inFlightChunks.size >= MAX_IN_FLIGHT) break
            val cp = ChunkPos(cx + dx, cz + dz)
            if (session.loadedChunks.contains(cp) || session.inFlightChunks.contains(cp)) continue
            session.inFlightChunks.add(cp)
            val queue = readyQueues.getOrPut(session.id) { Channel(256) }
            ioScope.launch {
                val chunk = world.getOrGenerate(cp)
                queue.trySendBlocking(cp to chunk)
            }
        }
    }

    suspend fun deliverReady(session: PlayerSession) {
        val queue = readyQueues[session.id] ?: return
        var delivered = 0
        while (delivered < MAX_DELIVER_PER_TICK) {
            val (cp, chunk) = queue.tryReceive().getOrNull() ?: break
            session.loadedChunks.add(cp)
            session.inFlightChunks.remove(cp)
            session.sendChunk(ServerMessage.ChunkData(chunk.pos, chunk.topY(), chunk.encodeWire()))
            delivered++
        }
        if (delivered > 0) log.debug("{} chunks delivered to {}", delivered, session.id.take(8))
    }

    private fun buildOffsets(yaw: Double, cx: Int, cz: Int): List<Pair<Int, Int>> {
        val fwdX = sin(yaw)
        val fwdZ = cos(yaw)
        val r = WorldConstants.VIEW_RADIUS
        val fwdR = WorldConstants.FORWARD_VIEW_RADIUS
        return (-fwdR..fwdR).flatMap { dx -> (-fwdR..fwdR).map { dz -> dx to dz } }
            .filter { (dx, dz) ->
                val chebyshev = maxOf(abs(dx), abs(dz))
                if (chebyshev <= r) true
                else {
                    val len = sqrt((dx * dx + dz * dz).toDouble())
                    val dot = (dx * fwdX + dz * fwdZ) / len
                    Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0))) < 90.0
                }
            }
            .sortedBy { (dx, dz) ->
                if (dx == 0 && dz == 0) -1.0
                else {
                    val len = sqrt((dx * dx + dz * dz).toDouble())
                    val dot = (dx * fwdX + dz * fwdZ) / len
                    Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0))) + 0.5 * maxOf(abs(dx), abs(dz))
                }
            }
    }
}
