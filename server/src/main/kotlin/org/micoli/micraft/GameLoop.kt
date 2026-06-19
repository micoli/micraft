package org.micoli.micraft

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldState
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("GameLoop")

private const val TICK_MS = 50L
private const val TICK_SECONDS = TICK_MS / 1000f
private const val SPAWN_X = 8f
private const val SPAWN_Y = 200f
private const val SPAWN_Z = 8f
private const val MOVE_SPEED = 5f * TICK_SECONDS
private const val GRAVITY = -20f
private const val CHUNK_RADIUS = 2

class GameLoop(private val world: WorldState) {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()

    fun start(app: Application) {
        log.info("GameLoop starting (tick=${TICK_MS}ms, gravity=$GRAVITY)")
        app.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    private suspend fun tick() {
        sessions.values.forEach { session ->
            var pendingYaw = session.state.orientation.yaw
            var pendingPitch = session.state.orientation.pitch
            var dx = 0f
            var dz = 0f

            while (true) {
                val intent = session.intents.tryReceive().getOrNull() ?: break
                when (intent) {
                    is ClientMessage.MoveIntent -> {
                        dx += intent.dx
                        dz += intent.dz
                        pendingYaw = intent.yaw
                        pendingPitch = intent.pitch
                    }
                    else -> {}
                }
            }

            val old = session.state
            val newX = old.pos.x + dx * MOVE_SPEED
            val newZ = old.pos.z + dz * MOVE_SPEED
            val newY = applyGravity(session, old.pos.y)

            val changed = newX != old.pos.x || newY != old.pos.y || newZ != old.pos.z
                    || pendingYaw != old.orientation.yaw || pendingPitch != old.orientation.pitch
            if (changed) {
                session.state = old.copy(
                    pos = Vec3(newX, newY, newZ),
                    orientation = Orientation(pendingYaw, pendingPitch),
                )
                val update = ServerMessage.PlayerUpdate(session.state)
                sessions.values.forEach { it.send(update) }
            }
        }
    }

    private fun applyGravity(session: PlayerSession, currentY: Float): Float {
        val blockX = kotlin.math.floor(session.state.pos.x.toDouble()).toInt()
        val blockZ = kotlin.math.floor(session.state.pos.z.toDouble()).toInt()

        // Already grounded: skip gravity to avoid accumulation spam.
        val blockBelowY = kotlin.math.floor(currentY.toDouble() - 0.001).toInt()
        if (session.vy <= 0f && world.getBlock(blockX, blockBelowY, blockZ) != BlockType.AIR) {
            session.vy = 0f
            return (blockBelowY + 1).toFloat()
        }

        session.vy += GRAVITY * TICK_SECONDS
        val proposedY = currentY + session.vy * TICK_SECONDS

        if (session.vy > 0f) return proposedY

        // Sweep to prevent tunneling at high fall speed.
        val fromBlock = kotlin.math.floor(currentY.toDouble() - 0.001).toInt()
        val toBlock   = kotlin.math.floor(proposedY.toDouble() - 0.001).toInt()
        for (by in fromBlock downTo toBlock) {
            if (world.getBlock(blockX, by, blockZ) != BlockType.AIR) {
                log.debug("player {} landed at y={}", session.id.take(8), by + 1)
                session.vy = 0f
                return (by + 1).toFloat()
            }
        }
        return proposedY.coerceAtLeast(0f)
    }

    suspend fun onConnect(socket: DefaultWebSocketSession) {
        val id = UUID.randomUUID().toString()
        val spawn = Vec3(SPAWN_X, SPAWN_Y, SPAWN_Z)
        val state = PlayerState(id, "Player", spawn, Orientation(0f, 0f))
        val session = PlayerSession(id, socket, state)
        sessions[id] = session
        log.info("player connected: {} (total={})", id.take(8), sessions.size)

        session.send(ServerMessage.Welcome(id, spawn))
        log.info("Welcome sent to {}", id.take(8))

        var chunkCount = 0
        for (cx in -CHUNK_RADIUS..CHUNK_RADIUS) {
            for (cz in -CHUNK_RADIUS..CHUNK_RADIUS) {
                val chunk = world.getOrGenerate(ChunkPos(cx, cz))
                session.send(ServerMessage.ChunkData(chunk))
                chunkCount++
                log.debug("  chunk ({},{}) sent to {}", cx, cz, id.take(8))
            }
        }
        log.info("{} chunks sent to {}", chunkCount, id.take(8))

        val existingPlayers = sessions.values.filter { it.id != id }
        existingPlayers.forEach { other ->
            session.send(ServerMessage.PlayerUpdate(other.state))
            other.send(ServerMessage.PlayerUpdate(state))
        }

        try {
            socket.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val msg = runCatching {
                        Json.decodeFromString<ClientMessage>(frame.readText())
                    }.onFailure { e ->
                        log.warn("bad frame from {}: {}", id.take(8), e.message)
                    }.getOrNull() ?: return@consumeEach

                    when (msg) {
                        is ClientMessage.Disconnect -> return@consumeEach
                        else -> session.intents.trySend(msg)
                    }
                }
            }
        } finally {
            sessions.remove(id)
            log.info("player disconnected: {} (total={})", id.take(8), sessions.size)
            val left = ServerMessage.PlayerLeft(id)
            sessions.values.forEach { it.send(left) }
        }
    }
}
