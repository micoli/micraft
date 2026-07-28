package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec

class ChunkController(
    private val world: WorldState,
    private val tokenStore: TokenStore?,
    httpWorkers: Int,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(httpWorkers)

    fun register(route: Route) =
        route.apply {
            get("/api/chunks/{cx}/{cz}") {
                if (tokenStore != null) {
                    val header = call.request.headers[HttpHeaders.Authorization]
                    val token = header?.removePrefix("Bearer ")?.trim()
                    if (token == null || tokenStore.validate(token) == null) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@get
                    }
                }
                val cx =
                    call.parameters["cx"]?.toIntOrNull()
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }
                val cz =
                    call.parameters["cz"]?.toIntOrNull()
                        ?: run {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }
                val chunk = withContext(dispatcher) { world.getOrGenerate(ChunkPos(cx, cz)) }
                val msg =
                    ServerMessage.ChunkData(
                        chunk.pos,
                        chunk.topY(),
                        chunk.encodeWire(),
                        chunk.encodeWireStates() ?: ByteArray(0),
                        world.chunkEntityProtos(chunk.pos))
                call.respondBytes(
                    ServerMessageCodec.encode(msg), ContentType.Application.OctetStream)
            }
        }
}
