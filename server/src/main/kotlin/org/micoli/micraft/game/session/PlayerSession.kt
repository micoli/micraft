package org.micoli.micraft.game.session

import io.ktor.websocket.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** Adds items to the live inventory and pushes an [ServerMessage.InventoryUpdate]. */
suspend fun PlayerSession.addItems(items: Map<ItemType, Int>) {
    items.forEach { (type, count) ->
        if (count != 0) inventory.merge(type, count) { a, b -> a + b }
    }
    inventory.entries.removeIf { it.value <= 0 }
    send(ServerMessage.InventoryUpdate(inventory.toMap()))
}

/**
 * Removes items if the inventory holds enough of every entry; returns false and no-ops otherwise.
 */
suspend fun PlayerSession.removeItems(items: Map<ItemType, Int>): Boolean {
    if (items.any { (type, count) -> (inventory[type] ?: 0) < count }) return false
    items.forEach { (type, count) -> inventory.merge(type, -count) { a, b -> a + b } }
    inventory.entries.removeIf { it.value <= 0 }
    send(ServerMessage.InventoryUpdate(inventory.toMap()))
    return true
}

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
    @Volatile var creativeFocusPos: Pair<Float, Float>? = null
    @Volatile var lastZonePos: Pair<Int, Int>? = null
    @Volatile var lastInstanceZoneId: String? = null
    @Volatile var breakTarget: BlockPos? = null
    @Volatile var breakTargetXOffset: Int = 0
    @Volatile var breakTargetZOffset: Int = 0
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

    // Transient (not persisted) — id of the vehicle currently being ridden, if any. Cleared on
    // disconnect/reconnect; reset by VehicleManager if the vehicle disappears while mounted.
    @Volatile var mountedVehicleId: String? = null

    // Seq of the last MoveIntent actually applied — echoed back in PlayerUpdate so the client
    // can replay only its still-unconfirmed inputs instead of reconciling against a stale
    // position (see LocalPlayerController.updateFromServer).
    @Volatile var lastProcessedSeq: Long = 0L

    @Volatile var chunkSocket: DefaultWebSocketSession? = null

    // The `?gameSession=` id this connection joined — null / "default" is GameLoop's default world.
    // Lets command handling resolve the session's own GameWorld instead of the default one.
    @Volatile var gameSessionId: String? = null

    // false => the client announced needsWorld=false (E2E specs with no terrain). Chunk streaming
    // stops after the center chunk.
    @Volatile var worldStreaming: Boolean = true

    // Ktor's WebSocketSession.send() isn't safe to call concurrently from multiple coroutines
    // (e.g. the per-tick movement broadcast racing another player's action broadcast landing on
    // this same session) — it can silently wedge the outgoing frame channel, leaving the socket
    // reported as open while nothing more ever gets sent. Serialize all writes per socket.
    private val sendMutex = Mutex()
    private val chunkSendMutex = Mutex()

    open suspend fun send(msg: ServerMessage) {
        val bytes = ServerMessageCodec.encode(msg)
        networkStats.bytesOut.addAndGet(bytes.size.toLong())
        sendMutex.withLock { socket.send(Frame.Binary(true, bytes)) }
    }

    open suspend fun sendChunk(msg: ServerMessage.ChunkData) {
        val bytes = ServerMessageCodec.encode(msg)
        networkStats.bytesOut.addAndGet(bytes.size.toLong())
        val cs = chunkSocket
        if (cs != null) {
            try {
                chunkSendMutex.withLock { cs.send(Frame.Binary(true, bytes)) }
                return
            } catch (_: Exception) {
                // chunk socket closed mid-delivery; fall through to main socket
            }
        }
        sendMutex.withLock { socket.send(Frame.Binary(true, bytes)) }
    }
}
