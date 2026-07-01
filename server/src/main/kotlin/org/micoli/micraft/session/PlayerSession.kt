package org.micoli.micraft.session

import io.ktor.websocket.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.ItemType

class NetworkStats {
    val bytesIn = AtomicLong(0)
    val bytesOut = AtomicLong(0)
}

fun List<ItemType?>.toSlotMap(): Map<Int, ItemType> =
    mapIndexedNotNull { i, t -> t?.let { i to it } }.toMap()

fun PlayerSession.hasPermission(perm: String): Boolean = "*" in permissions || perm in permissions

open class PlayerSession(
    val id: String,
    val userName: String,
    val socket: DefaultWebSocketSession,
    @Volatile var state: PlayerState,
    @Volatile var vy: Float = 0f,
    val networkStats: NetworkStats = NetworkStats(),
    val permissions: Set<String> = emptySet(),
    val chunkMode: String = "websocket",
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
        val bytes = ServerMessageCodec.encode(msg)
        networkStats.bytesOut.addAndGet(bytes.size.toLong())
        socket.send(Frame.Binary(true, bytes))
    }

    open suspend fun sendChunk(msg: ServerMessage.ChunkData) {
        val bytes = ServerMessageCodec.encode(msg)
        networkStats.bytesOut.addAndGet(bytes.size.toLong())
        val cs = chunkSocket
        if (cs != null) cs.send(Frame.Binary(true, bytes))
        else socket.send(Frame.Binary(true, bytes))
    }
}
