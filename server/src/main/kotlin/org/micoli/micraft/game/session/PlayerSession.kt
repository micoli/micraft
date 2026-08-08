package org.micoli.micraft.game.session

import io.ktor.websocket.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel
import org.micoli.micraft.combat.CombatState
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec

fun List<ShortcutSlot?>.toSlotMap(): Map<Int, ShortcutSlot> =
    mapIndexedNotNull { i, t -> t?.let { i to it } }.toMap()

fun Array<MutableList<ShortcutSlot?>>.toPageMap(): Map<Int, Map<Int, ShortcutSlot>> =
    mapIndexedNotNull { page, slots ->
            val nonNull = slots.toSlotMap()
            if (nonNull.isEmpty()) null else page to nonNull
        }
        .toMap()

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
    @Volatile var lastZonePos: Pair<Int, Int>? = null
    @Volatile var lastInstanceZoneId: String? = null
    @Volatile var breakTarget: BlockPos? = null
    val inventory: MutableMap<ItemType, Int> = ConcurrentHashMap()
    val actionHistory: ArrayDeque<WorldActionRecord> = ArrayDeque()
    val shortcutBarPages: Array<MutableList<ShortcutSlot?>> = Array(10) { MutableList(10) { null } }
    val knownRecipes: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>()).also {
            it.addAll(state.knownRecipes)
        }

    @Volatile var characterData: CharacterData? = null
    @Volatile var combatState: CombatState = CombatState()
    val isDowned: Boolean
        get() = (characterData?.currentHp ?: 1) <= 0

    @Volatile var lastMoveDx: Float = 0f
    @Volatile var lastMoveDz: Float = 0f
    @Volatile var lastMoveDy: Float = 0f

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
        if (cs != null) {
            try {
                cs.send(Frame.Binary(true, bytes))
                return
            } catch (_: Exception) {
                // chunk socket closed mid-delivery; fall through to main socket
            }
        }
        socket.send(Frame.Binary(true, bytes))
    }
}
