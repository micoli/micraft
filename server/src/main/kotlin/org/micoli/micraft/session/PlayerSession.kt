package org.micoli.micraft.session

import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.ItemType
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

open class PlayerSession(
    val id: String,
    val userName: String,
    val socket: DefaultWebSocketSession,
    @Volatile var state: PlayerState,
    @Volatile var vy: Float = 0f,
) {
    val intents = Channel<ClientMessage>(capacity = Channel.UNLIMITED)
    val loadedChunks: MutableSet<ChunkPos> = Collections.newSetFromMap(ConcurrentHashMap())
    val inFlightChunks: MutableSet<ChunkPos> = Collections.newSetFromMap(ConcurrentHashMap())
    @Volatile var lastChunkPos: ChunkPos? = null
    @Volatile var breakTarget: BlockPos? = null
    @Volatile var breakProgress: Int = 0
    val inventory: MutableMap<ItemType, Int> = ConcurrentHashMap()
    val actionHistory: ArrayDeque<WorldActionRecord> = ArrayDeque()
    val shortcutBar: MutableList<ItemType?> = MutableList(10) { null }

    @Volatile var chunkSocket: DefaultWebSocketSession? = null

    open suspend fun send(msg: ServerMessage) {
        socket.send(Frame.Text(Json.encodeToString(msg)))
    }

    open suspend fun sendChunk(msg: ServerMessage.ChunkData) {
        val cs = chunkSocket
        if (cs != null) cs.send(Frame.Text(Json.encodeToString<ServerMessage>(msg)))
        else send(msg)  // fallback: chunk socket not yet connected
    }
}
