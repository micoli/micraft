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
import kotlinx.coroutines.channels.Channel
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants

private const val PRED_DT = 16.0 / 1000.0  // seconds per prediction frame (~60fps)
private const val FLY_VERTICAL_SPEED = 8f   // must match server GameLoop constant
private const val SNAP_THRESHOLD = 2.0      // blocks: snap if prediction diverges beyond this

private enum class ViewMode { FIRST_PERSON, THIRD_PERSON }

class GameClient(private val scene: JsAny, private val camera: JsAny) {
    private val playerModels = mutableMapOf<String, JsAny>()
    private val playerPrevPos = mutableMapOf<String, Triple<Double, Double, Double>>()
    private val loadedChunks = mutableSetOf<ChunkPos>()
    private val chunkData    = mutableMapOf<ChunkPos, Pair<Chunk, Int>>()
    private val scope        = CoroutineScope(Dispatchers.Default)
    private var localPlayerId: String? = null
    private var localFlying      = false
    private var localStance      = PlayerStance.STANDING
    private var localSpeedMult   = 1f
    private var lastPlayerCx     = Int.MIN_VALUE
    private var lastPlayerCz     = Int.MIN_VALUE
    private val pendingUnloads   = mutableListOf<ChunkPos>()
    private var viewMode         = ViewMode.FIRST_PERSON
    private var localPlayerModel: JsAny? = null
    private var fpArms: JsAny? = null

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
    private var hudStance = "STANDING"; private var hudSpeed = 1.0; private var hudBiome = ""

    // Block breaking state
    private var breakTarget: BlockPos? = null
    private var hoverTarget: BlockPos? = null
    private val outMessages = Channel<ClientMessage>(Channel.BUFFERED)

    init {
        jsOptimizeScene(scene)
        jsInitPlayerModel()
    }

    private val grassMat   = jsCreateGrassMaterial(scene)
    private val stoneMat   = jsCreateTextureMaterial("stone",   "/textures/blocks/stone.png",   scene)
    private val dirtMat    = jsCreateTextureMaterial("dirt",    "/textures/blocks/dirt.png",    scene)
    private val bedrockMat   = jsCreateTextureMaterial("bedrock",    "/textures/blocks/bedrock.png",    scene)
    private val sandMat      = jsCreateTextureMaterial("sand",       "/textures/blocks/sand.png",       scene)
    private val sandstoneMat = jsCreateTextureMaterial("sandstone",  "/textures/blocks/sandstone.png",  scene)
    private val gravelMat    = jsCreateTextureMaterial("gravel",     "/textures/blocks/gravel.png",     scene)
    private val snowMat      = jsCreateTextureMaterial("snow",       "/textures/blocks/snow.png",       scene)

    fun connect(host: String, port: Int) {
        // Prediction loop: runs at ~60fps, moves camera immediately without waiting for server
        scope.launch {
            while (isActive) {
                delay(16)
                if (hasPrediction) applyLocalPrediction()
            }
        }

        scope.launch {
            var retryDelay = 1000L
            while (isActive) {
                try {
                    jsHideDisconnectedOverlay()
                    val client = HttpClient(Js) { install(WebSockets) }
                    client.webSocket(host = host, port = port, path = "/game") {
                        retryDelay = 1000L

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

                        // Forward block-break messages from prediction loop
                        val breakJob = launch {
                            for (msg in outMessages) {
                                val text = Json.encodeToString<ClientMessage>(msg)
                                send(Frame.Text(text))
                                netBytesOut += text.length
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
                        breakJob.cancel()
                    }
                } catch (e: Throwable) {
                    jsError("WebSocket error: ${e::class.simpleName}: ${e.message}")
                }

                if (!isActive) break
                resetForReconnect()
                val retrySec = retryDelay / 1000
                jsShowDisconnectedOverlay("Reconnecting in ${retrySec}s…")
                delay(retryDelay)
                retryDelay = minOf(retryDelay * 2, 8000L)
            }
        }
    }

    private fun resetForReconnect() {
        localPlayerId  = null
        hasPrediction  = false
        predX = 0.0; predY = 0.0; predZ = 0.0
        serverX = 0.0; serverZ = 0.0
        lastPlayerCx = Int.MIN_VALUE; lastPlayerCz = Int.MIN_VALUE
        pendingUnloads.clear()
        breakTarget = null
        hoverTarget = null
        jsHideBreakOverlay()
        jsHideTargetOutline()
        playerModels.values.forEach(::jsDisposePlayerModel)
        playerModels.clear()
        playerPrevPos.clear()
        localPlayerModel?.let { jsDisposePlayerModel(it) }
        localPlayerModel = null
        fpArms?.let { jsDisposeFPArms(it) }
        fpArms = null
        loadedChunks.forEach { cp -> jsDisposeChunk("${cp.cx},${cp.cz}") }
        loadedChunks.clear()
        chunkData.clear()
    }

    private fun applyLocalPrediction() {
        // Console: forward submitted command, skip movement while open
        val consoleInput = jsConsumeConsoleInput()
        if (consoleInput.isNotEmpty()) outMessages.trySend(ClientMessage.Command(consoleInput))
        if (jsIsConsoleOpen()) return

        val fwdX  = jsGetCameraForwardX(camera).toFloat()
        val fwdZ  = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ
        val rightZ = -fwdX

        val turnSpeed = (2.5f * PRED_DT).toFloat()
        if (jsIsKeyDown("KeyQ")) jsRotateCameraYaw(camera, -turnSpeed)
        if (jsIsKeyDown("KeyE")) jsRotateCameraYaw(camera,  turnSpeed)

        var dx = 0f; var dz = 0f
        if (jsIsKeyDown("KeyW")     || jsIsKeyDown("ArrowUp"))    { dx += fwdX;   dz += fwdZ   }
        if (jsIsKeyDown("KeyS")     || jsIsKeyDown("ArrowDown"))  { dx -= fwdX;   dz -= fwdZ   }
        if (jsIsKeyDown("KeyD")     || jsIsKeyDown("ArrowRight")) { dx += rightX; dz += rightZ }
        if (jsIsKeyDown("KeyA")     || jsIsKeyDown("ArrowLeft"))  { dx -= rightX; dz -= rightZ }

        val isMovingXZ = dx != 0f || dz != 0f

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
            if (jsIsKeyDown("Space"))          dy = 1f
            else if (jsIsKeyDown("ShiftLeft")) dy = -1f
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

        // View mode toggle (F key)
        if (jsConsumeViewToggle()) {
            viewMode = if (viewMode == ViewMode.FIRST_PERSON) ViewMode.THIRD_PERSON else ViewMode.FIRST_PERSON
        }

        // Inventory toggle (I key)
        if (jsConsumeInventoryToggle()) jsToggleHotbar()

        // Lazy-create local player model and first-person arms when bbmodel is ready
        if (jsIsPlayerBbmodelReady()) {
            if (localPlayerModel == null) {
                localPlayerModel = jsCreatePlayerModelNow(scene)
                jsSetPlayerVisible(localPlayerModel!!, false)
            }
            if (fpArms == null) {
                fpArms = jsCreateFPArms(camera, scene)
            }
        }

        val yaw = jsGetCameraRotationY(camera)
        val pitch = jsGetCameraRotationX(camera)

        if (viewMode == ViewMode.THIRD_PERSON) {
            val dist = 3.0
            val camX = predX - kotlin.math.sin(yaw) * dist
            val camY = predY + localStance.eyeOffset.toDouble() + 0.3
            val camZ = predZ - kotlin.math.cos(yaw) * dist
            jsCameraSetPosition(camera, camX, camY, camZ)
            localPlayerModel?.let {
                jsSetPlayerTransform(it, predX, predY, predZ, yaw.toFloat(), pitch.toFloat(), isMovingXZ)
                jsSetPlayerVisible(it, true)
            }
            fpArms?.let { jsSetFPArmsVisible(it, false) }
        } else {
            jsCameraSetPosition(camera, predX, predY + localStance.eyeOffset.toDouble(), predZ)
            localPlayerModel?.let { jsSetPlayerVisible(it, false) }
            fpArms?.let {
                jsUpdateFPArms(it, isMovingXZ)
                jsSetFPArmsVisible(it, true)
            }
        }

        // Raycast once per frame — used for both hover outline and break logic
        val target = raycastBlock()

        // In third-person: ghost the local model when a block is targeted so the outline is visible
        if (viewMode == ViewMode.THIRD_PERSON) {
            localPlayerModel?.let { jsSetPlayerAlpha(it, if (target != null) 0.35 else 1.0) }
        }

        // Hover outline: update only when target changes
        if (target != hoverTarget) {
            hoverTarget = target
            if (target != null) {
                val breakable = getBlockAtWorld(target.x, target.y, target.z) != BlockType.BEDROCK
                jsShowTargetOutline(scene, target.x, target.y, target.z, breakable)
            } else {
                jsHideTargetOutline()
            }
        }

        // Block breaking: only when left button held AND mouse stationary (not rotating)
        val isBreaking = jsIsBreaking()
        if (isBreaking && target != null) {
            if (target != breakTarget) {
                breakTarget = target
                jsShowBreakOverlay(scene, target.x, target.y, target.z, 1.0)
                outMessages.trySend(ClientMessage.BlockBreakStart(target))
            }
        } else if (breakTarget != null) {
            breakTarget = null
            jsHideBreakOverlay()
            outMessages.trySend(ClientMessage.BlockBreakStop)
        }

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
            currentKbIn, currentKbOut, hudBiome,
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
                jsIsKeyDown("Space")     -> 1f
                jsIsKeyDown("ShiftLeft") -> -1f
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
                jsConsoleSetPlayer(msg.playerName)
            }
            is ServerMessage.ChunkData -> {
                renderChunk(Chunk.decodeWire(msg.pos, msg.topY, msg.wireBlocks), msg.topY)
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
                    hudBiome  = s.biome
                } else {
                    updatePlayerMesh(s.id, s.pos.x.toDouble(), s.pos.y.toDouble(), s.pos.z.toDouble(), s.orientation.yaw, s.orientation.pitch)
                }
            }
            is ServerMessage.PlayerLeft  -> removePlayer(msg.playerId)
            is ServerMessage.Notification -> {
                jsShowNotification(msg.message)
                jsAddServerLog(msg.message)
            }
            is ServerMessage.BlockBreakProgress -> {
                val alpha = 1.0 - msg.progress.toDouble() / msg.hardness.toDouble()
                jsShowBreakOverlay(scene, msg.pos.x, msg.pos.y, msg.pos.z, alpha)
            }
            is ServerMessage.InventoryUpdate -> jsUpdateHotbar(Json.encodeToString(msg.inventory))
            is ServerMessage.ItemsSpawned -> Unit
            is ServerMessage.ItemDespawned -> Unit
            is ServerMessage.WorldUpdate -> msg.changes.forEach { change ->
                val cx = change.pos.x.floorDiv(WorldConstants.CHUNK_SIZE)
                val cz = change.pos.z.floorDiv(WorldConstants.CHUNK_SIZE)
                val cp = ChunkPos(cx, cz)
                val (existing, existingTopY) = chunkData[cp] ?: return@forEach
                val lx = change.pos.x - cx * WorldConstants.CHUNK_SIZE
                val lz = change.pos.z - cz * WorldConstants.CHUNK_SIZE
                val updated = existing.withBlock(lx, change.pos.y, lz, change.type)
                val newTopY = if (change.type != BlockType.AIR) maxOf(existingTopY, change.pos.y) else existingTopY
                renderChunk(updated, newTopY)
                if (change.type == BlockType.AIR) {
                    if (change.pos == breakTarget) {
                        breakTarget = null
                        jsHideBreakOverlay()
                    }
                    if (change.pos == hoverTarget) {
                        hoverTarget = null
                        jsHideTargetOutline()
                    }
                }
            }
        }
    }

    private fun renderChunk(chunk: Chunk, topY: Int) {
        val chunkKey = "${chunk.pos.cx},${chunk.pos.cz}"
        jsDisposeChunk(chunkKey)
        chunkData[chunk.pos] = Pair(chunk, topY)

        val ox = chunk.pos.cx * WorldConstants.CHUNK_SIZE
        val oz = chunk.pos.cz * WorldConstants.CHUNK_SIZE
        val s  = WorldConstants.CHUNK_SIZE

        jsChunkBegin(chunk.pos.cx, chunk.pos.cz)
        for (x in 0 until s) {
            for (z in 0 until s) {
                for (y in 0..topY) {
                    val block = chunk.getBlock(x, y, z)
                    if (block == BlockType.AIR) continue
                    val t  = block.ordinal * 6
                    val wx = ox + x
                    val wz2 = oz + z
                    // Emit only exposed faces; chunk-edge faces are always exposed.
                    if (y >= WorldConstants.WORLD_MAX_Y || chunk.getBlock(x, y + 1, z) == BlockType.AIR) jsChunkFace(wx, y, wz2, t + 4)
                    if (y <= 0 || chunk.getBlock(x, y - 1, z) == BlockType.AIR)                          jsChunkFace(wx, y, wz2, t + 5)
                    if (z == s - 1 || chunk.getBlock(x, y, z + 1) == BlockType.AIR)                      jsChunkFace(wx, y, wz2, t + 0)
                    if (z == 0     || chunk.getBlock(x, y, z - 1) == BlockType.AIR)                      jsChunkFace(wx, y, wz2, t + 1)
                    if (x == s - 1 || chunk.getBlock(x + 1, y, z) == BlockType.AIR)                      jsChunkFace(wx, y, wz2, t + 2)
                    if (x == 0     || chunk.getBlock(x - 1, y, z) == BlockType.AIR)                      jsChunkFace(wx, y, wz2, t + 3)
                }
            }
        }
        jsChunkEnd(scene, grassMat, stoneMat, dirtMat, bedrockMat, sandMat, sandstoneMat, gravelMat, snowMat)
        loadedChunks.add(chunk.pos)
    }

    private fun unloadDistantChunks(playerCx: Int, playerCz: Int) {
        val r = WorldConstants.CLIENT_VIEW_RADIUS
        val toUnload = loadedChunks.filter { cp ->
            kotlin.math.abs(cp.cx - playerCx) > r || kotlin.math.abs(cp.cz - playerCz) > r
        }
        if (toUnload.isEmpty()) return
        toUnload.forEach { cp ->
            jsDisposeChunk("${cp.cx},${cp.cz}")
            loadedChunks.remove(cp)
            chunkData.remove(cp)
        }
        pendingUnloads.addAll(toUnload)
    }

    private fun updatePlayerMesh(id: String, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        if (!jsIsPlayerBbmodelReady()) return
        val model = playerModels.getOrPut(id) { jsCreatePlayerModelNow(scene) }
        val prev = playerPrevPos[id]
        val isWalking = prev != null && (
            kotlin.math.abs(x - prev.first) > 0.001 ||
            kotlin.math.abs(z - prev.third) > 0.001
        )
        playerPrevPos[id] = Triple(x, y, z)
        jsSetPlayerTransform(model, x, y, z, yaw, pitch, isWalking)
    }

    private fun removePlayer(id: String) {
        playerModels.remove(id)?.let(::jsDisposePlayerModel)
        playerPrevPos.remove(id)
    }

    private fun getBlockAtWorld(wx: Int, wy: Int, wz: Int): BlockType {
        if (wy < 0 || wy > WorldConstants.WORLD_MAX_Y) return BlockType.AIR
        val cx = wx.floorDiv(WorldConstants.CHUNK_SIZE)
        val cz = wz.floorDiv(WorldConstants.CHUNK_SIZE)
        val (chunk, _) = chunkData[ChunkPos(cx, cz)] ?: return BlockType.AIR
        val lx = wx - cx * WorldConstants.CHUNK_SIZE
        val lz = wz - cz * WorldConstants.CHUNK_SIZE
        return chunk.getBlock(lx, wy, lz)
    }

    private fun raycastBlock(maxDist: Float = 5f): BlockPos? {
        // Always ray-cast from the player's eye, not the camera (which is offset in third-person).
        val ox = predX
        val oy = predY + localStance.eyeOffset.toDouble()
        val oz = predZ
        val dx = jsGetCameraDir3DX(camera).toFloat()
        val dy = jsGetCameraDir3DY(camera).toFloat()
        val dz = jsGetCameraDir3DZ(camera).toFloat()

        // BabylonJS blocks are centred at integer coords (vertex offsets ±0.5),
        // so the block containing position p is round(p), not floor(p).
        var bx = kotlin.math.round(ox).toInt()
        var by = kotlin.math.round(oy).toInt()
        var bz = kotlin.math.round(oz).toInt()

        val sx = if (dx > 0) 1 else if (dx < 0) -1 else 0
        val sy = if (dy > 0) 1 else if (dy < 0) -1 else 0
        val sz = if (dz > 0) 1 else if (dz < 0) -1 else 0

        val tDeltaX = if (dx != 0f) kotlin.math.abs(1f / dx) else Float.MAX_VALUE
        val tDeltaY = if (dy != 0f) kotlin.math.abs(1f / dy) else Float.MAX_VALUE
        val tDeltaZ = if (dz != 0f) kotlin.math.abs(1f / dz) else Float.MAX_VALUE

        // Face distances from camera to the next ±0.5 boundary (block edge at n ± 0.5)
        var tMaxX = if (dx > 0) ((bx + 0.5 - ox) / dx).toFloat() else if (dx < 0) ((bx - 0.5 - ox) / dx).toFloat() else Float.MAX_VALUE
        var tMaxY = if (dy > 0) ((by + 0.5 - oy) / dy).toFloat() else if (dy < 0) ((by - 0.5 - oy) / dy).toFloat() else Float.MAX_VALUE
        var tMaxZ = if (dz > 0) ((bz + 0.5 - oz) / dz).toFloat() else if (dz < 0) ((bz - 0.5 - oz) / dz).toFloat() else Float.MAX_VALUE

        while (true) {
            val t = minOf(tMaxX, tMaxY, tMaxZ)
            if (t > maxDist) break
            if (getBlockAtWorld(bx, by, bz) != BlockType.AIR) {
                if (by < 0 || by > WorldConstants.WORLD_MAX_Y) return null
                return BlockPos(bx, by, bz)
            }
            when {
                tMaxX < tMaxY && tMaxX < tMaxZ -> { bx += sx; tMaxX += tDeltaX }
                tMaxY < tMaxZ                  -> { by += sy; tMaxY += tDeltaY }
                else                           -> { bz += sz; tMaxZ += tDeltaZ }
            }
        }
        return null
    }
}
