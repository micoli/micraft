package org.micoli.micraft

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.*
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.player.height
import org.micoli.micraft.player.speed
import kotlinx.coroutines.channels.Channel
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.ui.HudData
import org.micoli.micraft.ui.McUiState
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.PlayerConstants
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.isSolid

private const val PRED_DT = 16.0 / 1000.0  // seconds per prediction frame (~60fps)
private const val FLY_VERTICAL_SPEED = 8f   // must match server GameLoop constant
private const val SNAP_THRESHOLD = 2.0      // blocks: snap if prediction diverges beyond this

// Sky color (matches fog and clearColor)
private const val SKY_R = 0.53
private const val SKY_G = 0.81
private const val SKY_B = 0.98

// AO neighbor offsets: [face][vertex][neighbor(s1,s2,corner)][axis(dx,dy,dz)]
// For each exposed face vertex, these offsets point to the 3 blocks that could occlude it.
private val AO_NEIGHBORS: Array<Array<Array<IntArray>>> = arrayOf(
    // fd=0: +Z (south)
    arrayOf(
        arrayOf(intArrayOf(-1,0,1), intArrayOf(0,-1,1), intArrayOf(-1,-1,1)),
        arrayOf(intArrayOf(1,0,1),  intArrayOf(0,-1,1), intArrayOf(1,-1,1)),
        arrayOf(intArrayOf(1,0,1),  intArrayOf(0,1,1),  intArrayOf(1,1,1)),
        arrayOf(intArrayOf(-1,0,1), intArrayOf(0,1,1),  intArrayOf(-1,1,1)),
    ),
    // fd=1: -Z (north)
    arrayOf(
        arrayOf(intArrayOf(1,0,-1),  intArrayOf(0,-1,-1), intArrayOf(1,-1,-1)),
        arrayOf(intArrayOf(-1,0,-1), intArrayOf(0,-1,-1), intArrayOf(-1,-1,-1)),
        arrayOf(intArrayOf(-1,0,-1), intArrayOf(0,1,-1),  intArrayOf(-1,1,-1)),
        arrayOf(intArrayOf(1,0,-1),  intArrayOf(0,1,-1),  intArrayOf(1,1,-1)),
    ),
    // fd=2: +X (east)
    arrayOf(
        arrayOf(intArrayOf(1,0,1),  intArrayOf(1,-1,0), intArrayOf(1,-1,1)),
        arrayOf(intArrayOf(1,0,-1), intArrayOf(1,-1,0), intArrayOf(1,-1,-1)),
        arrayOf(intArrayOf(1,0,-1), intArrayOf(1,1,0),  intArrayOf(1,1,-1)),
        arrayOf(intArrayOf(1,0,1),  intArrayOf(1,1,0),  intArrayOf(1,1,1)),
    ),
    // fd=3: -X (west)
    arrayOf(
        arrayOf(intArrayOf(-1,0,-1), intArrayOf(-1,-1,0), intArrayOf(-1,-1,-1)),
        arrayOf(intArrayOf(-1,0,1),  intArrayOf(-1,-1,0), intArrayOf(-1,-1,1)),
        arrayOf(intArrayOf(-1,0,1),  intArrayOf(-1,1,0),  intArrayOf(-1,1,1)),
        arrayOf(intArrayOf(-1,0,-1), intArrayOf(-1,1,0),  intArrayOf(-1,1,-1)),
    ),
    // fd=4: +Y (top)
    arrayOf(
        arrayOf(intArrayOf(-1,1,0), intArrayOf(0,1,1),  intArrayOf(-1,1,1)),
        arrayOf(intArrayOf(1,1,0),  intArrayOf(0,1,1),  intArrayOf(1,1,1)),
        arrayOf(intArrayOf(1,1,0),  intArrayOf(0,1,-1), intArrayOf(1,1,-1)),
        arrayOf(intArrayOf(-1,1,0), intArrayOf(0,1,-1), intArrayOf(-1,1,-1)),
    ),
    // fd=5: -Y (bottom)
    arrayOf(
        arrayOf(intArrayOf(-1,-1,0), intArrayOf(0,-1,-1), intArrayOf(-1,-1,-1)),
        arrayOf(intArrayOf(1,-1,0),  intArrayOf(0,-1,-1), intArrayOf(1,-1,-1)),
        arrayOf(intArrayOf(1,-1,0),  intArrayOf(0,-1,1),  intArrayOf(1,-1,1)),
        arrayOf(intArrayOf(-1,-1,0), intArrayOf(0,-1,1),  intArrayOf(-1,-1,1)),
    ),
)

private enum class ViewMode { FIRST_PERSON, THIRD_PERSON }

class GameClient(private val scene: JsAny, private val camera: JsAny, private val uiState: McUiState) {
    private val playerModels = mutableMapOf<String, JsAny>()
    private val playerPrevPos = mutableMapOf<String, Triple<Double, Double, Double>>()
    private val playerNames = mutableMapOf<String, String>()  // id → name, for autocomplete
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
    private var pendingFlyToggle = false
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

    private var serverHost = ""
    private var serverPort = 0

    // Block breaking state
    private var breakTarget: BlockPos? = null
    private var hoverTarget: BlockPos? = null
    private val outMessages = Channel<ClientMessage>(Channel.BUFFERED)

    private var blockMaterials: JsAny? = null
    private var shadersEnabled: Boolean = true

    // In-game time (ticks received from server)
    private var currentGameTicks = 0L
    private val TICKS_PER_DAY_CLIENT = 72_000L  // must match server GameConstants.TICKS_PER_DAY

    init {
        jsOptimizeScene(scene)
        jsSetupFog(scene, SKY_R, SKY_G, SKY_B)
        jsInitPlayerModel()
        jsInitBlockDefs()
    }

    private fun getBlockMaterials(): JsAny? {
        if (blockMaterials == null && jsIsBlockDefsReady()) {
            blockMaterials = jsCreateBlockMaterials(scene)
            jsSetShadersEnabled(scene, shadersEnabled)
        }
        return blockMaterials
    }

    fun connect(host: String, port: Int, username: String, playerName: String, preferredLanguage: String = "en") {
        serverHost = host
        serverPort = port
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
                    uiState.disconnectMessage = null
                    jsLog("WS connecting to ws://$host:$port/game")
                    val client = HttpClient(Js) { install(WebSockets) }
                    client.webSocket(host = host, port = port, path = "/game") {
                        retryDelay = 1000L
                        jsLog("WS connected, sending Connect(playerName=$playerName, userName=$username)")

                        send(Frame.Text(Json.encodeToString<ClientMessage>(ClientMessage.Connect(playerName = playerName, userName = username, preferredLanguage = preferredLanguage))))

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

                        var frameCount = 0
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                netBytesIn += text.length
                                frameCount++
                                if (frameCount <= 3) jsLog("WS frame #$frameCount (${text.length}B): ${text.take(200)}")
                                val msg = runCatching {
                                    Json.decodeFromString<ServerMessage>(text)
                                }.onFailure { e ->
                                    jsError("JSON parse error on frame #$frameCount: ${e.message} | raw=${text.take(300)}")
                                }.getOrNull() ?: continue
                                handleMessage(msg)
                            } else {
                                jsLog("WS non-text frame: ${frame::class.simpleName}")
                            }
                        }
                        jsLog("WS incoming loop ended after $frameCount frames (closeReason=${closeReason.await()})")
                        inputJob.cancel()
                        breakJob.cancel()
                    }
                    jsLog("WS session closed normally")
                } catch (e: Throwable) {
                    jsError("WS error: ${e::class.simpleName}: ${e.message}")
                }

                if (!isActive) break
                jsLog("WS resetForReconnect, retryDelay=${retryDelay}ms")
                resetForReconnect()
                val retrySec = retryDelay / 1000
                uiState.disconnectMessage = "Reconnecting in ${retrySec}s…"
                delay(retryDelay)
                retryDelay = minOf(retryDelay * 2, 8000L)
            }
        }
    }

    private fun resetForReconnect() {
        uiState.inventory = emptyMap()
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
        playerNames.clear()
        jsSetConnectedPlayers("[]")
        localPlayerModel?.let { jsDisposePlayerModel(it) }
        localPlayerModel = null
        fpArms?.let { jsDisposeFPArms(it) }
        fpArms = null
        loadedChunks.forEach { cp -> jsDisposeChunk("${cp.cx},${cp.cz}") }
        loadedChunks.clear()
        chunkData.clear()
    }

    private fun updateConnectedPlayersAutocomplete() {
        val json = "[" + playerNames.values.joinToString(",") { "\"$it\"" } + "]"
        jsSetConnectedPlayers(json)
    }

    private fun applyLocalPrediction() {
        // Console: forward submitted command, skip movement while open
        val consoleInput = jsConsumeConsoleInput()
        if (consoleInput.isNotEmpty()) {
            when (consoleInput.trim()) {
                "/keyreload" -> {
                    jsLoadBindings(serverHost, serverPort)
                    jsShowNotification("Keybindings reloaded")
                }
                "/disconnect" -> {
                    outMessages.trySend(ClientMessage.Disconnect())
                    jsReload()
                }
                else -> outMessages.trySend(ClientMessage.Command(consoleInput))
            }
        }
        if (jsIsConsoleOpen()) return

        val fwdX  = jsGetCameraForwardX(camera).toFloat()
        val fwdZ  = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ
        val rightZ = -fwdX

        val turnSpeed = (2.5f * PRED_DT).toFloat()
        if (jsIsActionDown("rotate_left"))  jsRotateCameraYaw(camera, -turnSpeed)
        if (jsIsActionDown("rotate_right")) jsRotateCameraYaw(camera,  turnSpeed)

        var dx = 0f; var dz = 0f
        if (jsIsActionDown("forward"))      { dx += fwdX;   dz += fwdZ   }
        if (jsIsActionDown("backward"))     { dx -= fwdX;   dz -= fwdZ   }
        if (jsIsActionDown("strafe_right")) { dx += rightX; dz += rightZ }
        if (jsIsActionDown("strafe_left"))  { dx -= rightX; dz -= rightZ }

        val isMovingXZ = dx != 0f || dz != 0f

        val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (len > 1f) { dx /= len; dz /= len }

        val stance = when {
            !localFlying && jsIsActionDown("crawl") -> PlayerStance.CRAWLING
            !localFlying && jsIsActionDown("sneak") -> PlayerStance.SNEAKING
            else                                    -> PlayerStance.STANDING
        }
        val speed = stance.speed * localSpeedMult * PRED_DT.toFloat()
        val solid = { bx: Int, by: Int, bz: Int -> getBlockAtWorld(bx, by, bz).isSolid }
        val h = stance.height
        val desiredDx = dx * speed
        val resolvedDx = AabbCollider.resolveX(solid, predX.toFloat(), predY.toFloat(), predZ.toFloat(), PlayerConstants.WIDTH, h, desiredDx)
        predX += resolvedDx.toDouble()
        val desiredDz = dz * speed
        val resolvedDz = AabbCollider.resolveZ(solid, predX.toFloat(), predY.toFloat(), predZ.toFloat(), PlayerConstants.WIDTH, h, desiredDz)
        predZ += resolvedDz.toDouble()

        if (localFlying) {
            val fwdY = jsGetCameraForwardY(camera).toFloat()
            var dy = 0f
            if (jsIsActionDown("ascend"))       dy = 1f
            else if (jsIsActionDown("descend")) dy = -1f
            else {
                if (jsIsActionDown("forward"))  dy += fwdY
                if (jsIsActionDown("backward")) dy -= fwdY
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

        // Drain one-shot events pushed by JS keyboard handler
        val events = jsConsumeEvents()
        repeat(jsEventsLength(events)) { i ->
            when (jsEventsGet(events, i)) {
                "view_toggle" -> viewMode = if (viewMode == ViewMode.FIRST_PERSON) ViewMode.THIRD_PERSON else ViewMode.FIRST_PERSON
                "inventory"   -> jsToggleHotbar()
                "undo"        -> outMessages.trySend(ClientMessage.Command("/undo 1"))
                "fly_toggle"  -> pendingFlyToggle = true
            }
        }

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

        val normalizedTime = (currentGameTicks % TICKS_PER_DAY_CLIENT).toDouble() / TICKS_PER_DAY_CLIENT
        jsUpdateSkyTime(scene, normalizedTime)

        jsDrawMinimap(predX, predZ)

        val toDeg = 180.0 / kotlin.math.PI
        val targetBlockName = target?.let { getBlockAtWorld(it.x, it.y, it.z).name } ?: ""
        val gameTimeDisplay = ticksToHHMM(currentGameTicks)
        jsUpdateHUD(hudX, hudY, hudZ, yaw * toDeg, pitch * toDeg, hudStance, hudSpeed,
            currentFps, currentKbIn, currentKbOut, hudBiome, targetBlockName, gameTimeDisplay)
        uiState.hud = HudData(
            x = hudX, y = hudY, z = hudZ,
            yaw = yaw * toDeg, pitch = pitch * toDeg,
            stance = hudStance, speed = hudSpeed,
            fps = currentFps, kbIn = currentKbIn, kbOut = currentKbOut,
            biome = hudBiome, targetBlock = targetBlockName,
            gameTime = gameTimeDisplay,
        )
    }

    private fun ticksToHHMM(ticks: Long): String {
        val day = ticks % TICKS_PER_DAY_CLIENT
        val h = (day * 24 / TICKS_PER_DAY_CLIENT).toInt()
        val m = ((day * 24 * 60 / TICKS_PER_DAY_CLIENT) % 60).toInt()
        return h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
    }

    private fun buildMoveIntent(): ClientMessage.MoveIntent {
        val fwdX  = jsGetCameraForwardX(camera).toFloat()
        val fwdZ  = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ    // perpendicular in XZ plane
        val rightZ = -fwdX

        var dx = 0f; var dz = 0f
        if (jsIsActionDown("forward"))      { dx += fwdX;   dz += fwdZ   }
        if (jsIsActionDown("backward"))     { dx -= fwdX;   dz -= fwdZ   }
        if (jsIsActionDown("strafe_right")) { dx += rightX; dz += rightZ }
        if (jsIsActionDown("strafe_left"))  { dx -= rightX; dz -= rightZ }

        // Normalize diagonal to avoid speed boost
        val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (len > 1f) { dx /= len; dz /= len }

        val flyToggle = pendingFlyToggle.also { pendingFlyToggle = false }
        val speedUp   = jsIsActionDown("speed_up")
        val speedDown = jsIsActionDown("speed_down")

        return if (localFlying) {
            // In fly mode: ascend = go up, forward/backward pitch-follows camera vertical component
            val dy = when {
                jsIsActionDown("ascend")  -> 1f
                jsIsActionDown("descend") -> -1f
                else -> {
                    val fwdY = jsGetCameraForwardY(camera).toFloat()
                    var d = 0f
                    if (jsIsActionDown("forward"))  d += fwdY
                    if (jsIsActionDown("backward")) d -= fwdY
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
                jsIsActionDown("crawl") -> PlayerStance.CRAWLING
                jsIsActionDown("sneak") -> PlayerStance.SNEAKING
                else                    -> PlayerStance.STANDING
            }
            ClientMessage.MoveIntent(
                dx = dx, dz = dz,
                yaw = jsGetCameraRotationY(camera).toFloat(),
                pitch = jsGetCameraRotationX(camera).toFloat(),
                stance = stance,
                jump = jsIsActionDown("ascend"),
                flyToggle = flyToggle,
                speedUp = speedUp, speedDown = speedDown,
            )
        }
    }

    private fun handleMessage(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.Welcome -> {
                localPlayerId = msg.playerId
                uiState.consolePlayerName = msg.playerName
                // Re-fetch with server's authoritative language (may differ from login preference for returning players)
                jsFetchI18n(msg.language)
                jsFetchBiomeColors()
                shadersEnabled = msg.shadersEnabled
                jsSetShadersEnabled(scene, msg.shadersEnabled)
            }
            is ServerMessage.ShadersUpdate -> {
                shadersEnabled = msg.enabled
                jsSetShadersEnabled(scene, msg.enabled)
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
                    if (blockMaterials != null) {
                        jsApplyBiomeGrassTint(s.biome)
                    }
                } else {
                    playerNames[s.id] = s.name
                    updateConnectedPlayersAutocomplete()
                    updatePlayerMesh(s.id, s.pos.x.toDouble(), s.pos.y.toDouble(), s.pos.z.toDouble(), s.orientation.yaw, s.orientation.pitch)
                }
            }
            is ServerMessage.PlayerLeft  -> {
                playerNames.remove(msg.playerId)
                updateConnectedPlayersAutocomplete()
                removePlayer(msg.playerId)
            }
            is ServerMessage.Notification -> {
                uiState.pushNotification(msg.message)
                uiState.pushLog(msg.message)
            }
            is ServerMessage.BlockBreakProgress -> {
                val alpha = 1.0 - msg.progress.toDouble() / msg.hardness.toDouble()
                jsShowBreakOverlay(scene, msg.pos.x, msg.pos.y, msg.pos.z, alpha)
            }
            is ServerMessage.InventoryUpdate -> { uiState.inventory = msg.inventory }
            is ServerMessage.TimeUpdate -> { currentGameTicks = msg.gameTicks }
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

    private fun computeFaceAO(chunk: Chunk, lx: Int, ly: Int, lz: Int, fd: Int): Int {
        val nbrs = AO_NEIGHBORS[fd]
        val s = WorldConstants.CHUNK_SIZE
        var packed = 0
        for (v in 0..3) {
            var solid = 0
            for (n in 0..2) {
                val off = nbrs[v][n]
                val nx = lx + off[0]; val ny = ly + off[1]; val nz = lz + off[2]
                if (nx < 0 || nx >= s || nz < 0 || nz >= s) continue
                if (ny < 0 || ny > WorldConstants.WORLD_MAX_Y) continue
                if (chunk.getBlock(nx, ny, nz).isSolid) solid++
            }
            packed = packed or ((solid * 5).coerceAtMost(15) shl (v * 4))
        }
        return packed
    }

    private fun renderChunk(chunk: Chunk, topY: Int) {
        val mats = getBlockMaterials() ?: return // block defs not yet loaded
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
                    if (y >= WorldConstants.WORLD_MAX_Y || !chunk.getBlock(x, y + 1, z).isSolid) jsChunkFace(wx, y, wz2, t + 4, computeFaceAO(chunk, x, y, z, 4))
                    if (y <= 0 || !chunk.getBlock(x, y - 1, z).isSolid)                          jsChunkFace(wx, y, wz2, t + 5, computeFaceAO(chunk, x, y, z, 5))
                    if (z == s - 1 || !chunk.getBlock(x, y, z + 1).isSolid)                      jsChunkFace(wx, y, wz2, t + 0, computeFaceAO(chunk, x, y, z, 0))
                    if (z == 0     || !chunk.getBlock(x, y, z - 1).isSolid)                      jsChunkFace(wx, y, wz2, t + 1, computeFaceAO(chunk, x, y, z, 1))
                    if (x == s - 1 || !chunk.getBlock(x + 1, y, z).isSolid)                      jsChunkFace(wx, y, wz2, t + 2, computeFaceAO(chunk, x, y, z, 2))
                    if (x == 0     || !chunk.getBlock(x - 1, y, z).isSolid)                      jsChunkFace(wx, y, wz2, t + 3, computeFaceAO(chunk, x, y, z, 3))
                }
            }
        }
        jsChunkEnd(scene, mats)
        loadedChunks.add(chunk.pos)
        pushMinimapChunk(chunk, topY)
    }

    private fun pushMinimapChunk(chunk: Chunk, topY: Int) {
        val topYParts = IntArray(WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE)
        val topBlockParts = IntArray(WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE)
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                val idx = lx * WorldConstants.CHUNK_SIZE + lz
                for (y in topY downTo 0) {
                    val block = chunk.getBlock(lx, y, lz)
                    if (block != BlockType.AIR) {
                        topYParts[idx] = y
                        topBlockParts[idx] = block.ordinal
                        break
                    }
                }
            }
        }
        jsSetMinimapChunk(
            chunk.pos.cx, chunk.pos.cz,
            "[${topYParts.joinToString(",")}]",
            "[${topBlockParts.joinToString(",")}]",
        )
    }

    private fun unloadDistantChunks(playerCx: Int, playerCz: Int) {
        val r = WorldConstants.CLIENT_VIEW_RADIUS
        val toUnload = loadedChunks.filter { cp ->
            kotlin.math.abs(cp.cx - playerCx) > r || kotlin.math.abs(cp.cz - playerCz) > r
        }
        if (toUnload.isEmpty()) return
        toUnload.forEach { cp ->
            jsDisposeChunk("${cp.cx},${cp.cz}")
            jsClearMinimapChunk(cp.cx, cp.cz)
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
