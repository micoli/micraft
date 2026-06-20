package org.micoli.micraft

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.height
import org.micoli.micraft.player.speed
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.PlayerConstants
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldPersistence
import org.micoli.micraft.world.WorldState
import org.micoli.micraft.world.hardness
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("GameLoop")

private const val TICK_MS      = 50L
private const val TICK_SECONDS = TICK_MS / 1000f
private val   DEBUG_WORLD      = System.getenv("MICRAFT_DEBUG_WORLD") == "1"
private const val SPAWN_X      = 8f
private val   SPAWN_Y: Float   = if (DEBUG_WORLD) 1f   else 200f
private val   SPAWN_Z: Float   = if (DEBUG_WORLD) 14f  else 8f
private const val GRAVITY            = -20f
private const val JUMP_SPEED        = 8.5f   // blocks/s upward — reaches ~1.8 blocks height
private const val FLY_VERTICAL_SPEED = 8f    // blocks/s up/down in fly mode
private const val SAVE_INTERVAL_TICKS = (30_000L / TICK_MS).toInt()

class GameLoop(
    private val world: WorldState,
    private val persistence: WorldPersistence? = null,
) {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()
    private var saveTickCounter = 0

    fun start(app: Application) {
        log.info("GameLoop starting (tick=${TICK_MS}ms, gravity=$GRAVITY)")
        app.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
                saveTickCounter++
                if (saveTickCounter >= SAVE_INTERVAL_TICKS) {
                    saveTickCounter = 0
                    world.flushDirty()
                }
            }
        }
    }

    fun shutdown() {
        world.flushDirty()
        sessions.values.forEach { session ->
            persistence?.savePlayerState(session.state.name, session.state)
        }
        log.info("World saved on shutdown")
    }

    private suspend fun tick() {
        sessions.values.forEach { session ->
            var pendingYaw      = session.state.orientation.yaw
            var pendingPitch    = session.state.orientation.pitch
            var localDx         = 0f
            var localDz         = 0f
            var localDy         = 0f
            var requestedStance = session.state.stance
            var jumpRequested      = false
            var flyToggleRequested = false
            var speedUpRequested   = false
            var speedDownRequested = false

            while (true) {
                val intent = session.intents.tryReceive().getOrNull() ?: break
                when (intent) {
                    is ClientMessage.MoveIntent -> {
                        localDx += intent.dx
                        localDz += intent.dz
                        localDy += intent.dy
                        pendingYaw      = intent.yaw
                        pendingPitch    = intent.pitch
                        requestedStance = intent.stance
                        if (intent.jump)      jumpRequested      = true
                        if (intent.flyToggle) flyToggleRequested = true
                        if (intent.speedUp)   speedUpRequested   = true
                        if (intent.speedDown) speedDownRequested = true
                    }
                    is ClientMessage.BlockBreakStart -> {
                        val bp = intent.pos
                        val block = world.getBlock(bp.x, bp.y, bp.z)
                        val eyeY = session.state.pos.y + session.state.stance.eyeOffset
                        val dist = kotlin.math.sqrt(
                            ((bp.x + 0.5f - session.state.pos.x) * (bp.x + 0.5f - session.state.pos.x) +
                             (bp.y + 0.5f - eyeY) * (bp.y + 0.5f - eyeY) +
                             (bp.z + 0.5f - session.state.pos.z) * (bp.z + 0.5f - session.state.pos.z)).toDouble()
                        )
                        log.debug("BlockBreakStart pos=$bp block=$block dist=${"%.2f".format(dist)}")
                        if (dist <= 6.0 && block != BlockType.AIR && block != BlockType.BEDROCK) {
                            session.breakTarget = bp
                            session.breakProgress = 0
                        }
                    }
                    is ClientMessage.BlockBreakStop -> {
                        session.breakTarget = null
                        session.breakProgress = 0
                    }
                    else -> {}
                }
            }

            // Progress block breaking each tick
            val bt = session.breakTarget
            if (bt != null) {
                val block = world.getBlock(bt.x, bt.y, bt.z)
                if (block == BlockType.AIR || block == BlockType.BEDROCK) {
                    session.breakTarget = null
                    session.breakProgress = 0
                } else {
                    session.breakProgress++
                    if (session.breakProgress >= block.hardness) {
                        val change = BlockChange(bt, BlockType.AIR)
                        world.applyChange(change)
                        sessions.values.forEach { it.send(ServerMessage.WorldUpdate(listOf(change))) }
                        session.breakTarget = null
                        session.breakProgress = 0
                    } else {
                        session.send(ServerMessage.BlockBreakProgress(bt, session.breakProgress, block.hardness))
                    }
                }
            }

            val old = session.state
            val w   = PlayerConstants.WIDTH
            val pos = old.pos

            // Speed multiplier: P = +0.5x, O = -0.5x, clamped [0.5, 5.0]
            val newSpeedMult = when {
                speedUpRequested   -> (old.speedMultiplier + 0.5f).coerceAtMost(5.0f)
                speedDownRequested -> (old.speedMultiplier - 0.5f).coerceAtLeast(0.5f)
                else               -> old.speedMultiplier
            }

            // Fly toggle: double-tap space on client
            val newFlying = if (flyToggleRequested) !old.flying else old.flying
            if (flyToggleRequested && newFlying) session.vy = 0f   // stop falling when entering fly

            // Stance: disabled in fly mode
            var newStance = if (!newFlying && AabbCollider.canAdoptStance(world, pos.x, pos.y, pos.z, w, requestedStance.height, old.stance.height))
                requestedStance else old.stance

            val h     = newStance.height
            val speed = newStance.speed * newSpeedMult * TICK_SECONDS

            // Normalize diagonal movement to avoid diagonal speed boost
            val len = kotlin.math.sqrt((localDx * localDx + localDz * localDz).toDouble()).toFloat()
            val nx = if (len > 0f) localDx / len else 0f
            val nz = if (len > 0f) localDz / len else 0f

            val attemptDx = nx * speed
            val attemptDz = nz * speed

            // Jump: only when grounded and not flying
            if (!newFlying && jumpRequested && session.vy == 0f && AabbCollider.isGrounded(world, pos.x, pos.y, pos.z, w)) {
                session.vy = JUMP_SPEED
                if (newStance != PlayerStance.STANDING) newStance = PlayerStance.STANDING
            }

            val resolvedDx = AabbCollider.resolveX(world, pos.x, pos.y, pos.z, w, h, attemptDx)
            val midX = pos.x + resolvedDx
            val resolvedDz = AabbCollider.resolveZ(world, midX, pos.y, pos.z, w, h, attemptDz)
            val newX = midX
            val newZ = pos.z + resolvedDz

            val newY = if (newFlying) {
                val flyDy = localDy * FLY_VERTICAL_SPEED * newSpeedMult * TICK_SECONDS
                val resolvedDy = AabbCollider.resolveY(world, newX, pos.y, newZ, w, h, flyDy)
                (pos.y + resolvedDy).coerceIn(0f, WorldConstants.WORLD_MAX_Y.toFloat())
            } else {
                applyGravity(session, newX, pos.y, newZ, newStance.height)
            }

            val changed = newX != pos.x || newY != pos.y || newZ != pos.z
                       || pendingYaw != old.orientation.yaw || pendingPitch != old.orientation.pitch
                       || newStance != old.stance || newFlying != old.flying
                       || newSpeedMult != old.speedMultiplier
            if (changed) {
                session.state = old.copy(
                    pos             = Vec3(newX, newY, newZ),
                    orientation     = Orientation(pendingYaw, pendingPitch),
                    stance          = newStance,
                    flying          = newFlying,
                    speedMultiplier = newSpeedMult,
                )
                val update = ServerMessage.PlayerUpdate(session.state)
                sessions.values.forEach { it.send(update) }
            }

            // Stream new chunks if the player crossed a chunk boundary
            val newCp = ChunkPos(
                Math.floorDiv(session.state.pos.x.toInt(), WorldConstants.CHUNK_SIZE),
                Math.floorDiv(session.state.pos.z.toInt(), WorldConstants.CHUNK_SIZE),
            )
            if (newCp != session.lastChunkPos) {
                session.lastChunkPos = newCp
                sendChunksAround(session, newCp.cx, newCp.cz)
            }
        }
    }

    private suspend fun sendChunksAround(session: PlayerSession, cx: Int, cz: Int) {
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

    private fun applyGravity(session: PlayerSession, cx: Float, cy: Float, cz: Float, h: Float): Float {
        val w = PlayerConstants.WIDTH

        // Already grounded: no need to simulate gravity
        if (session.vy <= 0f && AabbCollider.isGrounded(world, cx, cy, cz, w)) {
            session.vy = 0f
            return cy
        }

        session.vy += GRAVITY * TICK_SECONDS
        val dy = session.vy * TICK_SECONDS
        val resolvedDy = AabbCollider.resolveY(world, cx, cy, cz, w, h, dy)

        if (resolvedDy != dy) {
            if (session.vy < 0f) log.debug("player {} landed at y={}", session.id.take(8), cy + resolvedDy)
            session.vy = 0f
        }

        return (cy + resolvedDy).coerceAtLeast(0f)
    }

    suspend fun onConnect(socket: DefaultWebSocketSession) {
        val id = UUID.randomUUID().toString()

        // Receive the Connect handshake to get the player name before sending Welcome
        val playerName = runCatching {
            val firstFrame = socket.incoming.receive()
            if (firstFrame is Frame.Text) {
                val msg = Json.decodeFromString<ClientMessage>(firstFrame.readText())
                if (msg is ClientMessage.Connect) msg.playerName else "Player"
            } else "Player"
        }.getOrDefault("Player")

        val saved = persistence?.loadPlayerState(playerName)
        val spawn = saved?.pos ?: Vec3(SPAWN_X, SPAWN_Y, SPAWN_Z)
        val state = PlayerState(
            id = id,
            name = playerName,
            pos = spawn,
            orientation = saved?.orientation ?: Orientation(0f, 0f),
            stance = saved?.stance ?: PlayerStance.STANDING,
            flying = saved?.flying ?: DEBUG_WORLD,
            speedMultiplier = saved?.speedMultiplier ?: 1f,
        )
        val session = PlayerSession(id, socket, state)
        sessions[id] = session
        log.info("player connected: {} name={} (total={})", id.take(8), playerName, sessions.size)

        session.send(ServerMessage.Welcome(id, spawn))

        val spawnCp = ChunkPos(
            Math.floorDiv(spawn.x.toInt(), WorldConstants.CHUNK_SIZE),
            Math.floorDiv(spawn.z.toInt(), WorldConstants.CHUNK_SIZE),
        )
        session.lastChunkPos = spawnCp
        sendChunksAround(session, spawnCp.cx, spawnCp.cz)
        log.info("{} chunks sent to {}", session.loadedChunks.size, id.take(8))

        sessions.values.filter { it.id != id }.forEach { other ->
            session.send(ServerMessage.PlayerUpdate(other.state))
            other.send(ServerMessage.PlayerUpdate(state))
        }

        try {
            socket.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    runCatching { Json.decodeFromString<ClientMessage>(frame.readText()) }
                        .onFailure { log.warn("bad frame from {}: {}", id.take(8), it.message) }
                        .getOrNull()
                        ?.let { msg ->
                            when (msg) {
                                is ClientMessage.Disconnect -> return@consumeEach
                                is ClientMessage.ChunkUnload -> {
                                    msg.positions.forEach { session.loadedChunks.remove(it) }
                                    log.debug("{} chunks unloaded by {}", msg.positions.size, session.id.take(8))
                                }
                                else -> session.intents.trySend(msg)
                            }
                        }
                }
            }
        } finally {
            sessions.remove(id)
            persistence?.savePlayerState(session.state.name, session.state)
            log.info("player disconnected: {} name={} (total={})", id.take(8), session.state.name, sessions.size)
            val left = ServerMessage.PlayerLeft(id)
            sessions.values.forEach { it.send(left) }
        }
    }
}
