package org.micoli.micraft

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.*
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.player.speed
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants

private const val PRED_DT = 16.0 / 1000.0  // seconds per prediction frame (~60fps)
private const val FLY_VERTICAL_SPEED = 8f   // must match server GameLoop constant
private const val SNAP_THRESHOLD = 2.0      // blocks: snap if prediction diverges beyond this

class GameClient(private val scene: JsAny, private val camera: JsAny) {
    private val blockMeshes    = mutableMapOf<String, JsAny>()
    private val playerMeshes   = mutableMapOf<String, JsAny>()
    private val chunkBlockKeys = mutableMapOf<ChunkPos, MutableList<String>>()
    private val scope          = CoroutineScope(Dispatchers.Default)
    private var localPlayerId: String? = null
    private var localFlying      = false
    private var localStance      = PlayerStance.STANDING
    private var localSpeedMult   = 1f
    private var lastPlayerCx     = Int.MIN_VALUE
    private var lastPlayerCz     = Int.MIN_VALUE
    private val pendingUnloads   = mutableListOf<ChunkPos>()

    // Client-side prediction state
    private var predX = 0.0
    private var predY = 0.0
    private var predZ = 0.0
    private var serverX = 0.0
    private var serverZ = 0.0
    private var hasPrediction = false

    // FPS + network tracking (same 1s window)
    private var fpsFrameCount  = 0
    private var netBytesIn     = 0
    private var netBytesOut    = 0
    private var fpsWindowStart = jsNow()
    private var currentFps     = 0
    private var currentKbIn    = 0.0
    private var currentKbOut   = 0.0

    // Last server HUD data (updated on PlayerUpdate, displayed in prediction loop)
    private var hudX = 0.0; private var hudY = 0.0; private var hudZ = 0.0
    private var hudStance = "STANDING"; private var hudSpeed = 1.0

    private val grassMat   = jsCreateMaterial("grass",   scene).also { jsSetMaterialColor(it, 0.3,  0.7,  0.2)  }
    private val stoneMat   = jsCreateMaterial("stone",   scene).also { jsSetMaterialColor(it, 0.5,  0.5,  0.5)  }
    private val dirtMat    = jsCreateMaterial("dirt",    scene).also { jsSetMaterialColor(it, 0.55, 0.35, 0.15) }
    private val bedrockMat = jsCreateMaterial("bedrock", scene).also { jsSetMaterialColor(it, 0.1,  0.1,  0.1)  }
    private val playerMat  = jsCreateMaterial("player",  scene).also { jsSetMaterialColor(it, 0.8,  0.2,  0.2)  }

    fun connect(host: String, port: Int) {
        // Prediction loop: runs at ~60fps, moves camera immediately without waiting for server
        scope.launch {
            while (isActive) {
                delay(16)
                if (hasPrediction) applyLocalPrediction()
            }
        }

        scope.launch {
            try {
                val client = HttpClient(Js) { install(WebSockets) }
                client.webSocket(host = host, port = port, path = "/game") {
                    send(Frame.Text(Json.encodeToString<ClientMessage>(ClientMessage.Connect("Player"))))

                    // Send movement intents at server tick rate + pending chunk unloads
                    val inputJob = launch {
                        while (isActive) {
                            delay(50)
                            val intentText = Json.encodeToString<ClientMessage>(buildMoveIntent())
                            send(Frame.Text(intentText))
                            netBytesOut += intentText.length
                            if (pendingUnloads.isNotEmpty()) {
                                val batch = pendingUnloads.toList()
                                pendingUnloads.clear()
                                val unloadText = Json.encodeToString<ClientMessage>(ClientMessage.ChunkUnload(batch))
                                send(Frame.Text(unloadText))
                                netBytesOut += unloadText.length
                            }
                        }
                    }

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            netBytesIn += text.length
                            val msg = runCatching {
                                Json.decodeFromString<ServerMessage>(text)
                            }.onFailure { e ->
                                jsError("JSON parse error: ${e.message}")
                            }.getOrNull() ?: continue
                            handleMessage(msg)
                        }
                    }
                    inputJob.cancel()
                }
            } catch (e: Throwable) {
                jsError("WebSocket error: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    private fun applyLocalPrediction() {
        val fwdX  = jsGetCameraForwardX(camera).toFloat()
        val fwdZ  = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ
        val rightZ = -fwdX

        var dx = 0f; var dz = 0f
        if (jsIsKeyDown("KeyW")     || jsIsKeyDown("ArrowUp"))    { dx += fwdX;   dz += fwdZ   }
        if (jsIsKeyDown("KeyS")     || jsIsKeyDown("ArrowDown"))  { dx -= fwdX;   dz -= fwdZ   }
        if (jsIsKeyDown("KeyD")     || jsIsKeyDown("ArrowRight")) { dx += rightX; dz += rightZ }
        if (jsIsKeyDown("KeyA")     || jsIsKeyDown("ArrowLeft"))  { dx -= rightX; dz -= rightZ }

        val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (len > 1f) { dx /= len; dz /= len }

        val stance = when {
            !localFlying && jsIsKeyDown("ControlLeft") -> PlayerStance.CRAWLING
            !localFlying && jsIsKeyDown("ShiftLeft")   -> PlayerStance.SNEAKING
            else                                       -> PlayerStance.STANDING
        }
        val speed = stance.speed * localSpeedMult * PRED_DT.toFloat()
        predX += (dx * speed).toDouble()
        predZ += (dz * speed).toDouble()

        if (localFlying) {
            val fwdY = jsGetCameraForwardY(camera).toFloat()
            var dy = 0f
            if (jsIsKeyDown("Space"))                                dy = 1f
            else {
                if (jsIsKeyDown("KeyW") || jsIsKeyDown("ArrowUp"))   dy += fwdY
                if (jsIsKeyDown("KeyS") || jsIsKeyDown("ArrowDown")) dy -= fwdY
            }
            predY += (dy * FLY_VERTICAL_SPEED * localSpeedMult * PRED_DT).toDouble()
        }

        // Soft correction toward server-authoritative XZ position
        val diffX = serverX - predX
        val diffZ = serverZ - predZ
        if (kotlin.math.abs(diffX) > SNAP_THRESHOLD || kotlin.math.abs(diffZ) > SNAP_THRESHOLD) {
            predX = serverX; predZ = serverZ
        } else {
            predX += diffX * 0.08
            predZ += diffZ * 0.08
        }

        jsCameraSetPosition(camera, predX, predY + localStance.eyeOffset.toDouble(), predZ)

        // FPS + network rates: update once per second
        fpsFrameCount++
        val now = jsNow()
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1000.0) {
            val sec = elapsed / 1000.0
            currentFps    = (fpsFrameCount / sec).toInt()
            currentKbIn   = netBytesIn  / 1024.0 / sec
            currentKbOut  = netBytesOut / 1024.0 / sec
            fpsFrameCount = 0
            netBytesIn    = 0
            netBytesOut   = 0
            fpsWindowStart = now
        }

        val toDeg = 180.0 / kotlin.math.PI
        jsUpdateHUD(
            hudX, hudY, hudZ,
            jsGetCameraRotationY(camera) * toDeg,
            jsGetCameraRotationX(camera) * toDeg,
            hudStance, hudSpeed, currentFps,
            currentKbIn, currentKbOut,
        )
    }

    private fun buildMoveIntent(): ClientMessage.MoveIntent {
        val fwdX  = jsGetCameraForwardX(camera).toFloat()
        val fwdZ  = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ    // perpendicular in XZ plane
        val rightZ = -fwdX

        var dx = 0f; var dz = 0f
        if (jsIsKeyDown("KeyW")     || jsIsKeyDown("ArrowUp"))    { dx += fwdX;   dz += fwdZ   }
        if (jsIsKeyDown("KeyS")     || jsIsKeyDown("ArrowDown"))  { dx -= fwdX;   dz -= fwdZ   }
        if (jsIsKeyDown("KeyD")     || jsIsKeyDown("ArrowRight")) { dx += rightX; dz += rightZ }
        if (jsIsKeyDown("KeyA")     || jsIsKeyDown("ArrowLeft"))  { dx -= rightX; dz -= rightZ }

        // Normalize diagonal to avoid speed boost
        val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (len > 1f) { dx /= len; dz /= len }

        val flyToggle = jsConsumeFlyToggle()
        val speedUp   = jsIsKeyDown("KeyP")
        val speedDown = jsIsKeyDown("KeyO")

        return if (localFlying) {
            // In fly mode: Space = go up, W/S pitch-follows camera vertical component
            val dy = when {
                jsIsKeyDown("Space") -> 1f
                else -> {
                    val fwdY = jsGetCameraForwardY(camera).toFloat()
                    var d = 0f
                    if (jsIsKeyDown("KeyW")    || jsIsKeyDown("ArrowUp"))   d += fwdY
                    if (jsIsKeyDown("KeyS")    || jsIsKeyDown("ArrowDown")) d -= fwdY
                    d
                }
            }
            ClientMessage.MoveIntent(
                dx = dx, dz = dz, dy = dy,
                yaw = jsGetCameraRotationY(camera).toFloat(),
                pitch = jsGetCameraRotationX(camera).toFloat(),
                stance = PlayerStance.STANDING,
                jump = false,
                flyToggle = flyToggle,
                speedUp = speedUp, speedDown = speedDown,
            )
        } else {
            val stance = when {
                jsIsKeyDown("ControlLeft") -> PlayerStance.CRAWLING
                jsIsKeyDown("ShiftLeft")   -> PlayerStance.SNEAKING
                else                       -> PlayerStance.STANDING
            }
            ClientMessage.MoveIntent(
                dx = dx, dz = dz,
                yaw = jsGetCameraRotationY(camera).toFloat(),
                pitch = jsGetCameraRotationX(camera).toFloat(),
                stance = stance,
                jump = jsIsKeyDown("Space"),
                flyToggle = flyToggle,
                speedUp = speedUp, speedDown = speedDown,
            )
        }
    }

    private fun handleMessage(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.Welcome -> {
                localPlayerId = msg.playerId
            }
            is ServerMessage.ChunkData -> {
                renderChunk(Chunk.decodeWire(msg.pos, msg.topY, msg.wireBlocks))
            }
            is ServerMessage.PlayerUpdate -> {
                val s = msg.state
                if (s.id == localPlayerId) {
                    localFlying    = s.flying
                    localStance    = s.stance
                    localSpeedMult = s.speedMultiplier

                    // Server is authoritative for Y (gravity/collision) and XZ correction
                    predY   = s.pos.y.toDouble()
                    serverX = s.pos.x.toDouble()
                    serverZ = s.pos.z.toDouble()

                    if (!hasPrediction) {
                        // First update: initialise prediction from server position
                        predX = serverX
                        predZ = serverZ
                        hasPrediction = true
                    }

                    val cx = s.pos.x.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
                    val cz = s.pos.z.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
                    if (cx != lastPlayerCx || cz != lastPlayerCz) {
                        lastPlayerCx = cx; lastPlayerCz = cz
                        unloadDistantChunks(cx, cz)
                    }

                    hudX = s.pos.x.toDouble()
                    hudY = s.pos.y.toDouble()
                    hudZ = s.pos.z.toDouble()
                    hudStance = if (s.flying) "FLYING" else s.stance.name
                    hudSpeed  = s.speedMultiplier.toDouble()
                } else {
                    updatePlayerMesh(s.id, s.pos.x.toDouble(), s.pos.y.toDouble(), s.pos.z.toDouble())
                }
            }
            is ServerMessage.PlayerLeft  -> removePlayer(msg.playerId)
            is ServerMessage.WorldUpdate -> msg.changes.forEach { change ->
                val key = "${change.pos.x},${change.pos.y},${change.pos.z}"
                blockMeshes.remove(key)?.let(::jsDisposeMesh)
                if (change.type != BlockType.AIR) addBlock(change.pos.x, change.pos.y, change.pos.z, change.type)
                // Note: WorldUpdate blocks are not tracked in chunkBlockKeys (rare individual changes)
            }
        }
    }

    private fun renderChunk(chunk: Chunk) {
        // Dispose any existing meshes for this chunk (handles re-render after unload)
        chunkBlockKeys.remove(chunk.pos)?.forEach { key ->
            blockMeshes.remove(key)?.let(::jsDisposeMesh)
        }
        val keys = mutableListOf<String>()
        val ox = chunk.pos.cx * WorldConstants.CHUNK_SIZE
        val oz = chunk.pos.cz * WorldConstants.CHUNK_SIZE
        for (x in 0 until WorldConstants.CHUNK_SIZE) {
            for (z in 0 until WorldConstants.CHUNK_SIZE) {
                for (y in 0..WorldConstants.WORLD_MAX_Y) {
                    val block = chunk.getBlock(x, y, z)
                    if (block == BlockType.AIR) continue
                    if (isHidden(chunk, x, y, z)) continue
                    val key = addBlock(ox + x, y, oz + z, block)
                    if (key != null) keys.add(key)
                }
            }
        }
        chunkBlockKeys[chunk.pos] = keys
    }

    private fun unloadDistantChunks(playerCx: Int, playerCz: Int) {
        val r = WorldConstants.CLIENT_VIEW_RADIUS
        val toUnload = chunkBlockKeys.keys.filter { cp ->
            kotlin.math.abs(cp.cx - playerCx) > r || kotlin.math.abs(cp.cz - playerCz) > r
        }
        if (toUnload.isEmpty()) return
        toUnload.forEach { cp ->
            chunkBlockKeys.remove(cp)?.forEach { key ->
                blockMeshes.remove(key)?.let(::jsDisposeMesh)
            }
        }
        pendingUnloads.addAll(toUnload)
    }

    private fun isHidden(chunk: Chunk, x: Int, y: Int, z: Int): Boolean {
        val maxY = WorldConstants.WORLD_MAX_Y
        val s    = WorldConstants.CHUNK_SIZE
        if (y < maxY && chunk.getBlock(x, y + 1, z) == BlockType.AIR) return false
        if (y > 0    && chunk.getBlock(x, y - 1, z) == BlockType.AIR) return false
        if (x > 0    && chunk.getBlock(x - 1, y, z) == BlockType.AIR) return false
        if (x < s - 1 && chunk.getBlock(x + 1, y, z) == BlockType.AIR) return false
        if (z > 0    && chunk.getBlock(x, y, z - 1) == BlockType.AIR) return false
        if (z < s - 1 && chunk.getBlock(x, y, z + 1) == BlockType.AIR) return false
        return true
    }

    private fun addBlock(wx: Int, wy: Int, wz: Int, type: BlockType): String? {
        val key = "$wx,$wy,$wz"
        if (blockMeshes.containsKey(key)) return null
        val mat = when (type) {
            BlockType.GRASS   -> grassMat
            BlockType.STONE   -> stoneMat
            BlockType.DIRT    -> dirtMat
            BlockType.BEDROCK -> bedrockMat
            BlockType.AIR     -> return null
        }
        val mesh = jsCreateBox("b_$key", 1.0, scene)
        jsSetMeshPosition(mesh, wx.toDouble(), wy.toDouble(), wz.toDouble())
        jsSetMeshMaterial(mesh, mat)
        blockMeshes[key] = mesh
        return key
    }

    private fun updatePlayerMesh(id: String, x: Double, y: Double, z: Double) {
        val mesh = playerMeshes.getOrPut(id) {
            jsCreateBox("p_$id", 0.6, scene).also { jsSetMeshMaterial(it, playerMat) }
        }
        jsSetMeshPosition(mesh, x, y, z)
    }

    private fun removePlayer(id: String) {
        playerMeshes.remove(id)?.let(::jsDisposeMesh)
    }
}
