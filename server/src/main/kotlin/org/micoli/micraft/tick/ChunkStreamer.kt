package org.micoli.micraft.tick

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldState
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ChunkStreamer::class.java)

private const val MAX_IN_FLIGHT = 12
private const val MAX_DELIVER_PER_TICK = 3

class ChunkStreamer(private val world: WorldState) {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val readyPools = ConcurrentHashMap<String, ConcurrentHashMap<ChunkPos, Chunk>>()

    fun cleanupSession(sessionId: String) {
        readyPools.remove(sessionId)
    }

    fun checkAndRequest(session: PlayerSession) {
        val newCp =
            ChunkPos(
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
            val pool = readyPools.getOrPut(session.id) { ConcurrentHashMap() }
            ioScope.launch {
                val chunk = world.getOrGenerate(cp)
                pool[cp] = chunk
            }
        }
    }

    suspend fun deliverReady(session: PlayerSession) {
        val pool = readyPools[session.id] ?: return
        if (pool.isEmpty()) return

        val cx = Math.floorDiv(session.state.pos.x.toInt(), WorldConstants.CHUNK_SIZE)
        val cz = Math.floorDiv(session.state.pos.z.toInt(), WorldConstants.CHUNK_SIZE)
        val yaw = session.state.orientation.yaw.toDouble()

        val candidates = pool.keys
            .sortedBy { cp -> chunkScore(cp.cx - cx, cp.cz - cz, yaw) }
            .take(MAX_DELIVER_PER_TICK)

        var delivered = 0
        for (cp in candidates) {
            val chunk = pool.remove(cp) ?: continue
            session.loadedChunks.add(cp)
            session.inFlightChunks.remove(cp)
            session.sendChunk(ServerMessage.ChunkData(chunk.pos, chunk.topY(), chunk.encodeWire()))
            delivered++
        }
        if (delivered > 0) log.debug("{} chunks delivered to {}", delivered, session.id.take(8))
    }

    private fun chunkScore(dx: Int, dz: Int, yaw: Double): Double {
        if (dx == 0 && dz == 0) return -1.0
        val fwdX = sin(yaw)
        val fwdZ = cos(yaw)
        val dist = sqrt((dx * dx + dz * dz).toDouble())
        val dot = (dx * fwdX + dz * fwdZ) / dist
        val angleDeg = Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))
        return when {
            angleDeg < 60.0 -> dist
            angleDeg < 120.0 -> 1000.0 + dist
            else -> 2000.0 + dist
        }
    }

    private fun buildOffsets(yaw: Double, cx: Int, cz: Int): List<Pair<Int, Int>> {
        val fwdR = WorldConstants.FORWARD_VIEW_RADIUS
        return (-fwdR..fwdR)
            .flatMap { dx -> (-fwdR..fwdR).map { dz -> dx to dz } }
            .sortedBy { (dx, dz) -> chunkScore(dx, dz, yaw) }
    }
}
