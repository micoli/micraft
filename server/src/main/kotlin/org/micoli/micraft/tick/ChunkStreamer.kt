package org.micoli.micraft.tick

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.micoli.micraft.player.Vec3
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
private const val VEL_ALPHA = 0.3f
private const val LOOKAHEAD_TICKS = 40

class ChunkStreamer(private val world: WorldState) {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val readyPools = ConcurrentHashMap<String, ConcurrentHashMap<ChunkPos, Chunk>>()
    private val pendingPools = ConcurrentHashMap<String, MutableSet<ChunkPos>>()
    private val prevPositions = ConcurrentHashMap<String, Vec3>()
    private val smoothedVelocities = ConcurrentHashMap<String, Pair<Float, Float>>()

    fun cleanupSession(sessionId: String) {
        readyPools.remove(sessionId)
        pendingPools.remove(sessionId)
        prevPositions.remove(sessionId)
        smoothedVelocities.remove(sessionId)
    }

    fun checkAndRequest(session: PlayerSession) {
        updateVelocity(session)
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

    suspend fun sendCenterChunkNow(session: PlayerSession, cp: ChunkPos) {
        val chunk = withContext(Dispatchers.IO) { world.getOrGenerate(cp) }
        session.loadedChunks.add(cp)
        session.sendChunk(ServerMessage.ChunkData(chunk.pos, chunk.topY(), chunk.encodeWire()))
    }

    fun requestAround(session: PlayerSession, cx: Int, cz: Int) {
        val yaw = session.state.orientation.yaw.toDouble()
        val offsets = buildOffsets(yaw, cx, cz)
        val pending = pendingPools.getOrPut(session.id) { ConcurrentHashMap.newKeySet() }
        val primaryAllCovered = offsets.none { (dx, dz) ->
            chunkScore(dx, dz, yaw) < 4000.0 &&
                run {
                    val cp = ChunkPos(cx + dx, cz + dz)
                    !session.loadedChunks.contains(cp) &&
                        !session.inFlightChunks.contains(cp) &&
                        !pending.contains(cp)
                }
        }
        for ((dx, dz) in offsets) {
            val cp = ChunkPos(cx + dx, cz + dz)
            if (session.loadedChunks.contains(cp) || session.inFlightChunks.contains(cp)) continue
            if (!primaryAllCovered && chunkScore(dx, dz, yaw) >= 4000.0) continue
            pending.add(cp)
        }
        drainPending(session)
    }

    private fun updateVelocity(session: PlayerSession) {
        val pos = session.state.pos
        val prev = prevPositions[session.id]
        if (prev != null) {
            val rawVx = pos.x - prev.x
            val rawVz = pos.z - prev.z
            val (oldVx, oldVz) = smoothedVelocities[session.id] ?: (0f to 0f)
            smoothedVelocities[session.id] =
                (oldVx + VEL_ALPHA * (rawVx - oldVx)) to (oldVz + VEL_ALPHA * (rawVz - oldVz))
        }
        prevPositions[session.id] = pos
    }

    private fun predictedCenter(session: PlayerSession): Pair<Int, Int> {
        val pos = session.state.pos
        val (vx, vz) = smoothedVelocities[session.id] ?: (0f to 0f)
        val predX = pos.x + vx * LOOKAHEAD_TICKS
        val predZ = pos.z + vz * LOOKAHEAD_TICKS
        return Math.floorDiv(predX.toInt(), WorldConstants.CHUNK_SIZE) to
            Math.floorDiv(predZ.toInt(), WorldConstants.CHUNK_SIZE)
    }

    private fun drainPending(session: PlayerSession) {
        val pending = pendingPools[session.id] ?: return
        if (pending.isEmpty()) return

        val slots = MAX_IN_FLIGHT - session.inFlightChunks.size
        if (slots <= 0) return

        val (pcx, pcz) = predictedCenter(session)
        val yaw = session.state.orientation.yaw.toDouble()

        val candidates =
            pending.sortedBy { cp -> chunkScore(cp.cx - pcx, cp.cz - pcz, yaw) }.take(slots)

        val pool = readyPools.getOrPut(session.id) { ConcurrentHashMap() }
        for (cp in candidates) {
            if (session.inFlightChunks.size >= MAX_IN_FLIGHT) break
            pending.remove(cp)
            session.inFlightChunks.add(cp)
            ioScope.launch {
                val chunk = world.getOrGenerate(cp)
                pool[cp] = chunk
            }
        }
    }

    suspend fun deliverReady(session: PlayerSession) {
        val pool = readyPools[session.id] ?: return
        if (pool.isEmpty()) return

        val (pcx, pcz) = predictedCenter(session)
        val yaw = session.state.orientation.yaw.toDouble()

        val candidates =
            pool.keys
                .sortedBy { cp -> chunkScore(cp.cx - pcx, cp.cz - pcz, yaw) }
                .take(MAX_DELIVER_PER_TICK)

        var delivered = 0
        for (cp in candidates) {
            val chunk = pool.remove(cp) ?: continue
            session.loadedChunks.add(cp)
            session.inFlightChunks.remove(cp)
            session.sendChunk(ServerMessage.ChunkData(chunk.pos, chunk.topY(), chunk.encodeWire()))
            delivered++
        }
        if (delivered > 0) {
            log.debug("{} chunks delivered to {}", delivered, session.id.take(8))
            drainPending(session)
        }
    }

    private fun chunkScore(dx: Int, dz: Int, yaw: Double): Double {
        val dist = sqrt((dx * dx + dz * dz).toDouble())
        if (dist == 0.0) return -1.0
        if (dist <= sqrt(2.0) + 0.01) return 1000.0 + dist
        val fwdX = -sin(yaw)
        val fwdZ = -cos(yaw)
        val dot = (dx * fwdX + dz * fwdZ) / dist
        val angleDeg = Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))
        val halfR = WorldConstants.FORWARD_VIEW_RADIUS / 2.0
        return when {
            dist <= halfR && angleDeg < 60.0 -> 2000.0 + dist
            dist > halfR -> 3000.0 + dist
            else -> 4000.0 + dist
        }
    }

    private fun buildOffsets(yaw: Double, cx: Int, cz: Int): List<Pair<Int, Int>> {
        val fwdR = WorldConstants.FORWARD_VIEW_RADIUS
        return (-fwdR..fwdR)
            .flatMap { dx -> (-fwdR..fwdR).map { dz -> dx to dz } }
            .sortedBy { (dx, dz) -> chunkScore(dx, dz, yaw) }
    }
}
