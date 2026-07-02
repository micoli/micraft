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
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants

class HttpChunkFetcher(
    private val chunkManager: ChunkManager,
    private val token: String,
    private val scope: CoroutineScope,
) {
    private val inFlight = mutableSetOf<ChunkPos>()
    val inFlightCount: Int get() = inFlight.size
    private val httpClient = HttpClient(Js)
    private val baseUrl = "http://${jsGetPageHost()}:${jsGetPagePort()}"

    fun trigger(playerCx: Int, playerCz: Int, yaw: Float) {
        val offsets = buildOffsets(yaw.toDouble(), playerCx, playerCz)
        for ((dx, dz) in offsets) {
            val cp = ChunkPos(playerCx + dx, playerCz + dz)
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
                    Chunk.decodeWire(msg.pos, msg.topY, msg.wireBlocks), msg.topY)
        } finally {
            inFlight.remove(cp)
        }
    }

    private fun buildOffsets(yaw: Double, cx: Int, cz: Int): List<Pair<Int, Int>> {
        val r = WorldConstants.CLIENT_VIEW_RADIUS
        return (-r..r)
            .flatMap { dx -> (-r..r).map { dz -> dx to dz } }
            .sortedBy { (dx, dz) -> chunkScore(dx, dz, yaw) }
    }

    private fun chunkScore(dx: Int, dz: Int, yaw: Double): Double {
        if (dx == 0 && dz == 0) return -1.0
        val fwdX = -sin(yaw)
        val fwdZ = -cos(yaw)
        val dist = sqrt((dx * dx + dz * dz).toDouble())
        val dot = (dx * fwdX + dz * fwdZ) / dist
        val angleDeg = acos(dot.coerceIn(-1.0, 1.0)) * (180.0 / PI)
        return when {
            angleDeg < 60.0 -> dist
            angleDeg < 120.0 -> 1000.0 + dist
            else -> 2000.0 + dist
        }
    }
}
