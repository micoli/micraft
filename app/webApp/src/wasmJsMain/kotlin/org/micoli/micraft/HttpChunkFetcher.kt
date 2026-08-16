package org.micoli.micraft

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.micoli.micraft.babylon.jsGetPageHost
import org.micoli.micraft.babylon.jsGetPagePort
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec

class HttpChunkFetcher(
    private val chunkManager: ChunkManager,
    private val token: String,
    private val scope: CoroutineScope,
) {
    private val inFlight = mutableSetOf<ChunkPos>()
    val inFlightCount: Int
        get() = inFlight.size

    private val pendingQueue = ArrayDeque<ChunkPos>()

    private val httpClient = HttpClient(Js)
    private val baseUrl = "http://${jsGetPageHost()}:${jsGetPagePort()}"

    fun trigger(playerCx: Int, playerCz: Int, yaw: Float) {
        val yawD = yaw.toDouble()
        val primaryAllCovered = chunkManager.allNearFovChunksMeshed(playerCx, playerCz, yawD)
        val needed =
            buildOffsets(yawD, playerCx, playerCz).mapNotNull { (dx, dz) ->
                val cp = ChunkPos(playerCx + dx, playerCz + dz)
                if (chunkManager.loadedChunks.contains(cp) || inFlight.contains(cp))
                    return@mapNotNull null
                if (!primaryAllCovered && chunkScore(dx, dz, yawD) >= 3000.0) return@mapNotNull null
                cp
            }
        pendingQueue.clear()
        pendingQueue.addAll(needed)
        pumpQueue()
    }

    private fun pumpQueue() {
        while (inFlight.size < MAX_CONCURRENT && pendingQueue.isNotEmpty()) {
            val cp = pendingQueue.removeFirst()
            if (chunkManager.loadedChunks.contains(cp) || inFlight.contains(cp)) continue
            inFlight.add(cp)
            scope.launch { fetch(cp) }
        }
    }

    private suspend fun fetch(cp: ChunkPos) {
        try {
            val bytes =
                httpClient
                    .get("$baseUrl/api/chunks/${cp.cx}/${cp.cz}") {
                        if (token.isNotEmpty()) header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    .body<ByteArray>()
            val msg = runCatching { ServerMessageCodec.decode(bytes) }.getOrNull()
            if (msg is ServerMessage.ChunkData)
                chunkManager.enqueueChunk(
                    Chunk.decodeWire(
                        msg.pos,
                        msg.topY,
                        msg.wireBlocks,
                        msg.wireStates.takeIf { it.isNotEmpty() },
                        msg.wireExtraStates.takeIf { it.isNotEmpty() }),
                    msg.topY)
        } finally {
            inFlight.remove(cp)
            pumpQueue()
        }
    }

    private fun buildOffsets(yaw: Double, cx: Int, cz: Int): List<Pair<Int, Int>> {
        val r = WorldConstants.CLIENT_VIEW_RADIUS
        return (-r..r)
            .flatMap { dx -> (-r..r).map { dz -> dx to dz } }
            .sortedBy { (dx, dz) -> chunkScore(dx, dz, yaw) }
    }

    private fun chunkScore(dx: Int, dz: Int, yaw: Double): Double {
        val dist = sqrt((dx * dx + dz * dz).toDouble())
        if (dist == 0.0) return 0.0
        if (dist <= sqrt(2.0) + 0.01) return 1000.0 + dist
        val fwdX = sin(yaw)
        val fwdZ = cos(yaw)
        val dot = (dx * fwdX + dz * fwdZ) / dist
        val angleDeg = acos(dot.coerceIn(-1.0, 1.0)) * (180.0 / PI)
        val halfR = WorldConstants.CLIENT_VIEW_RADIUS / 2.0
        return when {
            dist <= halfR && angleDeg < 60.0 -> 2000.0 + dist
            dist > halfR -> 3000.0 + dist
            else -> 4000.0 + dist
        }
    }

    companion object {
        private const val MAX_CONCURRENT = 4
    }
}
