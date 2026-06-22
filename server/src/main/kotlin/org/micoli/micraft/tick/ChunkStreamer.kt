package org.micoli.micraft.tick

import kotlin.math.*
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldState
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ChunkStreamer::class.java)

class ChunkStreamer(private val world: WorldState) {

    suspend fun checkAndStream(session: PlayerSession) {
        val newCp = ChunkPos(
            Math.floorDiv(session.state.pos.x.toInt(), WorldConstants.CHUNK_SIZE),
            Math.floorDiv(session.state.pos.z.toInt(), WorldConstants.CHUNK_SIZE),
        )
        if (newCp == session.lastChunkPos) return
        session.lastChunkPos = newCp
        streamAround(session, newCp.cx, newCp.cz)
    }

    suspend fun streamAround(session: PlayerSession, cx: Int, cz: Int) {
        val r = WorldConstants.VIEW_RADIUS
        val yaw = session.state.orientation.yaw.toDouble()
        val fwdX = sin(yaw)
        val fwdZ = cos(yaw)

        val fwdR = WorldConstants.FORWARD_VIEW_RADIUS

        val offsets = (-fwdR..fwdR).flatMap { dx -> (-fwdR..fwdR).map { dz -> dx to dz } }
            .filter { (dx, dz) ->
                val chebyshev = maxOf(abs(dx), abs(dz))
                if (chebyshev <= r) true
                else {
                    // Include extra chunks only if they are in the forward half (angleDeg < 90°)
                    val len = sqrt((dx * dx + dz * dz).toDouble())
                    val dot = (dx * fwdX + dz * fwdZ) / len
                    val angleDeg = Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))
                    angleDeg < 90.0
                }
            }
            .sortedBy { (dx, dz) ->
                if (dx == 0 && dz == 0) -1.0
                else {
                    val len = sqrt((dx * dx + dz * dz).toDouble())
                    val dot = (dx * fwdX + dz * fwdZ) / len
                    val angleDeg = Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))
                    angleDeg + 0.5 * maxOf(abs(dx), abs(dz))
                }
            }

        var sent = 0
        for ((dx, dz) in offsets) {
            val cp = ChunkPos(cx + dx, cz + dz)
            if (session.loadedChunks.add(cp)) {
                val chunk = world.getOrGenerate(cp)
                session.send(ServerMessage.ChunkData(chunk.pos, chunk.topY(), chunk.encodeWire()))
                sent++
            }
        }
        if (sent > 0) log.debug("{} new chunks sent to {}", sent, session.id.take(8))
    }
}
