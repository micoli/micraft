package org.micoli.micraft.tick

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
        var sent = 0
        for (dx in -r..r) {
            for (dz in -r..r) {
                val cp = ChunkPos(cx + dx, cz + dz)
                if (session.loadedChunks.add(cp)) {
                    val chunk = world.getOrGenerate(cp)
                    session.send(ServerMessage.ChunkData(chunk.pos, chunk.topY(), chunk.encodeWire()))
                    sent++
                }
            }
        }
        if (sent > 0) log.debug("{} new chunks sent to {}", sent, session.id.take(8))
    }
}
