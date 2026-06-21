package org.micoli.micraft.session

import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.ItemType
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class PlayerSession(
    val id: String,
    val socket: DefaultWebSocketSession,
    @Volatile var state: PlayerState,
    @Volatile var vy: Float = 0f,
) {
    val intents = Channel<ClientMessage>(capacity = Channel.UNLIMITED)
    val loadedChunks: MutableSet<ChunkPos> = Collections.newSetFromMap(ConcurrentHashMap())
    @Volatile var lastChunkPos: ChunkPos? = null
    @Volatile var breakTarget: BlockPos? = null
    @Volatile var breakProgress: Int = 0
    val inventory: MutableMap<ItemType, Int> = ConcurrentHashMap()

    suspend fun send(msg: ServerMessage) {
        socket.send(Frame.Text(Json.encodeToString(msg)))
    }
}
