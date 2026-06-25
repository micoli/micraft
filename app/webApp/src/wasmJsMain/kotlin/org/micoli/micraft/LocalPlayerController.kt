package org.micoli.micraft

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.*
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.player.height
import org.micoli.micraft.player.speed
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.ui.HudData
import org.micoli.micraft.ui.McUiState
import org.micoli.micraft.world.*

private const val PRED_DT = 16.0 / 1000.0
private const val FLY_VERTICAL_SPEED = 8f
private const val SNAP_THRESHOLD = 0.5
private const val CLIENT_GRAVITY = -20.0
private const val CLIENT_JUMP_SPEED = 8.5
private const val TICKS_PER_DAY_CLIENT = 72_000L

private enum class ViewMode {
    FIRST_PERSON,
    THIRD_PERSON
}

private data class RaycastResult(val target: BlockPos, val adjacent: BlockPos)

class LocalPlayerController(
    private val scene: JsAny,
    private val camera: JsAny,
    private val outMessages: Channel<ClientMessage>,
    private val chunkManager: ChunkManager,
    private val uiState: McUiState,
    private val networkStats: NetworkStats,
    private val serverHost: () -> String,
    private val serverPort: () -> Int,
) {
    var predX = 0.0
    var predY = 0.0
    var predZ = 0.0
    var predVy = 0.0
    var serverX = 0.0
    var serverY = 0.0
    var serverZ = 0.0
    var hasPrediction = false
    var localFlying = false
    var localStance = PlayerStance.STANDING
    var localSpeedMult = 1f
    var lastPlayerCx = Int.MIN_VALUE
    var lastPlayerCz = Int.MIN_VALUE
    private var viewMode = ViewMode.FIRST_PERSON
    var pendingFlyToggle = false
    var lastSentIntent: ClientMessage.MoveIntent? = null

    @OptIn(ExperimentalWasmJsInterop::class) var localPlayerModel: JsAny? = null
    @OptIn(ExperimentalWasmJsInterop::class) var fpArms: JsAny? = null

    private var breakTarget: BlockPos? = null
    private var hoverTarget: BlockPos? = null
    var selectedSlot: Int = 0
    val shortcutBar: Array<ItemType?> = arrayOfNulls(10)
    private var hasPlacedThisClick = false

    private var hudX = 0.0
    private var hudY = 0.0
    private var hudZ = 0.0
    private var hudStance = "STANDING"
    private var hudSpeed = 1.0
    private var hudBiome = ""
    var currentGameTicks = 0L

    private var fpsFrameCount = 0
    private var fpsWindowStart = jsNow()
    private var currentFps = 0
    private var currentKbIn = 0.0
    private var currentKbOut = 0.0

    fun updateFromServer(state: PlayerState, onChunkChanged: (Int, Int) -> Unit) {
        localFlying = state.flying
        localStance = state.stance
        localSpeedMult = state.speedMultiplier

        serverX = state.pos.x.toDouble()
        serverY = state.pos.y.toDouble()
        serverZ = state.pos.z.toDouble()

        if (!hasPrediction) {
            predX = serverX
            predZ = serverZ
            predY = serverY
            predVy = 0.0
            hasPrediction = true
        } else {
            val diffY = serverY - predY
            if (kotlin.math.abs(diffY) > 1.0) {
                predY = serverY
                predVy = 0.0
            } else predY += diffY * 0.2
        }

        val cx = state.pos.x.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
        val cz = state.pos.z.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
        if (cx != lastPlayerCx || cz != lastPlayerCz) {
            lastPlayerCx = cx
            lastPlayerCz = cz
            onChunkChanged(cx, cz)
        }

        hudX = state.pos.x.toDouble()
        hudY = state.pos.y.toDouble()
        hudZ = state.pos.z.toDouble()
        hudStance = if (state.flying) "FLYING" else state.stance.name
        hudSpeed = state.speedMultiplier.toDouble()
        hudBiome = state.biome
        chunkManager.applyBiomeGrassTint(state.biome)
    }

    fun selectSlot(index: Int) {
        selectedSlot = index
        hasPlacedThisClick = false
        syncShortcutBarToUi()
        if (breakTarget != null) {
            breakTarget = null
            jsHideBreakOverlay()
            outMessages.trySend(ClientMessage.BlockBreakStop)
        }
    }

    fun syncShortcutBarToUi() {
        val slots = shortcutBar.map { it?.name }
        val json =
            "{\"slots\":[${slots.joinToString(",") { if (it == null) "null" else "\"$it\"" }}],\"selected\":$selectedSlot}"
        jsUpdateShortcutBar(json)
        jsSetSelectedSlot(selectedSlot)
    }

    fun onBlockBroken(pos: BlockPos) {
        if (pos == breakTarget) {
            breakTarget = null
            jsHideBreakOverlay()
        }
        if (pos == hoverTarget) {
            hoverTarget = null
            jsHideTargetOutline()
        }
    }

    fun tick() {
        val consoleInput = jsConsumeConsoleInput()
        if (consoleInput.isNotEmpty()) {
            when (consoleInput.trim()) {
                "/keyreload" -> {
                    jsLoadBindings(serverHost(), serverPort())
                    jsShowNotification("Keybindings reloaded")
                }
                "/disconnect" -> {
                    outMessages.trySend(ClientMessage.Disconnect())
                    jsReload()
                }
                else ->
                    if (consoleInput.startsWith("/")) {
                        outMessages.trySend(ClientMessage.Command(consoleInput))
                    } else {
                        outMessages.trySend(
                            ClientMessage.ChatSend(jsGetActiveChannel(), consoleInput))
                    }
            }
        }
        if (jsIsConsoleOpen()) return

        val fwdX = jsGetCameraForwardX(camera).toFloat()
        val fwdZ = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ
        val rightZ = -fwdX

        val turnSpeed = (2.5f * PRED_DT).toFloat()
        if (jsIsActionDown("rotate_left")) jsRotateCameraYaw(camera, -turnSpeed)
        if (jsIsActionDown("rotate_right")) jsRotateCameraYaw(camera, turnSpeed)

        var dx = 0f
        var dz = 0f
        if (jsIsActionDown("forward")) {
            dx += fwdX
            dz += fwdZ
        }
        if (jsIsActionDown("backward")) {
            dx -= fwdX
            dz -= fwdZ
        }
        if (jsIsActionDown("strafe_right")) {
            dx += rightX
            dz += rightZ
        }
        if (jsIsActionDown("strafe_left")) {
            dx -= rightX
            dz -= rightZ
        }

        val isMovingXZ = dx != 0f || dz != 0f

        val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (len > 1f) {
            dx /= len
            dz /= len
        }

        val stance =
            when {
                !localFlying && jsIsActionDown("crawl") -> PlayerStance.CRAWLING
                !localFlying && jsIsActionDown("sneak") -> PlayerStance.SNEAKING
                else -> PlayerStance.STANDING
            }
        val speed = stance.speed * localSpeedMult * PRED_DT.toFloat()
        val solid = { bx: Int, by: Int, bz: Int ->
            chunkManager.getBlockAtWorld(bx, by, bz).isSolid
        }
        val h = stance.height
        val resolvedDx =
            AabbCollider.resolveX(
                solid,
                predX.toFloat(),
                predY.toFloat(),
                predZ.toFloat(),
                PlayerConstants.WIDTH,
                h,
                dx * speed)
        predX += resolvedDx.toDouble()
        val resolvedDz =
            AabbCollider.resolveZ(
                solid,
                predX.toFloat(),
                predY.toFloat(),
                predZ.toFloat(),
                PlayerConstants.WIDTH,
                h,
                dz * speed)
        predZ += resolvedDz.toDouble()

        if (localFlying) {
            val fwdY = jsGetCameraForwardY(camera).toFloat()
            var dy = 0f
            if (jsIsActionDown("ascend")) dy = 1f
            else if (jsIsActionDown("descend")) dy = -1f
            else {
                if (jsIsActionDown("forward")) dy += fwdY
                if (jsIsActionDown("backward")) dy -= fwdY
            }
            val flyDy = (dy * FLY_VERTICAL_SPEED * localSpeedMult * PRED_DT).toFloat()
            val resolvedFlyDy =
                AabbCollider.resolveY(
                    solid,
                    predX.toFloat(),
                    predY.toFloat(),
                    predZ.toFloat(),
                    PlayerConstants.WIDTH,
                    h,
                    flyDy)
            predY = (predY + resolvedFlyDy).coerceIn(0.0, WorldConstants.WORLD_MAX_Y.toDouble())
            predVy = 0.0
        } else {
            val solid2 = { bx: Int, by: Int, bz: Int ->
                chunkManager.getBlockAtWorld(bx, by, bz).isSolid
            }
            val h2 = localStance.height
            val grounded =
                AabbCollider.isGrounded(
                    solid2,
                    predX.toFloat(),
                    predY.toFloat(),
                    predZ.toFloat(),
                    PlayerConstants.WIDTH)
            if (grounded && predVy <= 0.0) {
                predVy = if (jsIsActionDown("ascend")) CLIENT_JUMP_SPEED else 0.0
            } else {
                predVy += CLIENT_GRAVITY * PRED_DT
                val dy = (predVy * PRED_DT).toFloat()
                val resolvedDy =
                    AabbCollider.resolveY(
                        solid2,
                        predX.toFloat(),
                        predY.toFloat(),
                        predZ.toFloat(),
                        PlayerConstants.WIDTH,
                        h2,
                        dy)
                if (resolvedDy != dy) predVy = 0.0
                predY = (predY + resolvedDy).coerceAtLeast(0.0)
            }
        }

        val diffX = serverX - predX
        val diffZ = serverZ - predZ
        if (kotlin.math.abs(diffX) > SNAP_THRESHOLD || kotlin.math.abs(diffZ) > SNAP_THRESHOLD) {
            predX = serverX
            predZ = serverZ
        } else {
            predX += diffX * 0.3
            predZ += diffZ * 0.3
        }

        val events = jsConsumeEvents()
        repeat(jsEventsLength(events)) { i ->
            when (jsEventsGet(events, i)) {
                "view_toggle" ->
                    viewMode =
                        if (viewMode == ViewMode.FIRST_PERSON) ViewMode.THIRD_PERSON
                        else ViewMode.FIRST_PERSON
                "inventory" -> jsToggleHotbar()
                "undo" -> outMessages.trySend(ClientMessage.Command("/undo 1"))
                "fly_toggle" -> pendingFlyToggle = true
                "slot_1" -> selectSlot(0)
                "slot_2" -> selectSlot(1)
                "slot_3" -> selectSlot(2)
                "slot_4" -> selectSlot(3)
                "slot_5" -> selectSlot(4)
                "slot_6" -> selectSlot(5)
                "slot_7" -> selectSlot(6)
                "slot_8" -> selectSlot(7)
                "slot_9" -> selectSlot(8)
                "slot_10" -> selectSlot(9)
            }
        }

        val layoutUpdateJson = jsConsumeLayoutUpdate()
        if (layoutUpdateJson.isNotEmpty()) {
            runCatching {
                val msg = Json.decodeFromString<ClientMessage.LayoutUpdate>(layoutUpdateJson)
                outMessages.trySend(msg)
            }
        }

        val preferencesUpdateJson = jsConsumePreferencesUpdate()
        if (preferencesUpdateJson.isNotEmpty()) {
            runCatching {
                val msg =
                    Json.decodeFromString<ClientMessage.PreferencesUpdate>(preferencesUpdateJson)
                outMessages.trySend(msg)
            }
        }

        val slotUpdateJson = jsConsumeSlotUpdate()
        if (slotUpdateJson.isNotEmpty()) {
            runCatching {
                val slotMatch =
                    Regex("\"slot\":(\\d+)").find(slotUpdateJson)?.groupValues?.get(1)?.toInt()
                val typeMatch =
                    Regex("\"itemType\":\"([A-Z_]+)\"").find(slotUpdateJson)?.groupValues?.get(1)
                if (slotMatch != null && slotMatch in 1..9) {
                    val itemType =
                        typeMatch?.let { name -> ItemType.entries.find { it.name == name } }
                    shortcutBar[slotMatch] = itemType
                    syncShortcutBarToUi()
                    outMessages.trySend(ClientMessage.ShortcutBarSet(slotMatch, itemType))
                }
            }
        }

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
                jsSetPlayerTransform(
                    it, predX, predY, predZ, yaw.toFloat(), pitch.toFloat(), isMovingXZ)
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

        val rayResult = raycastBlock()
        val target = rayResult?.target

        if (viewMode == ViewMode.THIRD_PERSON) {
            localPlayerModel?.let { jsSetPlayerAlpha(it, if (target != null) 0.35 else 1.0) }
        }

        if (target != hoverTarget) {
            hoverTarget = target
            if (target != null) {
                val breakable =
                    chunkManager.getBlockAtWorld(target.x, target.y, target.z) != BlockType.BEDROCK
                jsShowTargetOutline(scene, target.x, target.y, target.z, breakable)
            } else {
                jsHideTargetOutline()
            }
        }

        val isBreaking = jsIsBreaking()
        val selectedItem = if (selectedSlot > 0) shortcutBar[selectedSlot] else null
        val isPlaceMode = selectedItem != null && selectedItem.buildable

        if (isPlaceMode) {
            val adjacent = rayResult?.adjacent
            if (isBreaking && adjacent != null && !hasPlacedThisClick) {
                hasPlacedThisClick = true
                breakTarget = adjacent
                outMessages.trySend(ClientMessage.BlockPlace(adjacent, selectedItem))
            } else if (!isBreaking) {
                breakTarget = null
                hasPlacedThisClick = false
            }
        } else {
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
        }

        fpsFrameCount++
        val now = jsNow()
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1000.0) {
            val sec = elapsed / 1000.0
            currentFps = (fpsFrameCount / sec).toInt()
            currentKbIn = networkStats.bytesIn / 1024.0 / sec
            currentKbOut = networkStats.bytesOut / 1024.0 / sec
            fpsFrameCount = 0
            networkStats.bytesIn = 0
            networkStats.bytesOut = 0
            fpsWindowStart = now
        }

        val normalizedTime =
            (currentGameTicks % TICKS_PER_DAY_CLIENT).toDouble() / TICKS_PER_DAY_CLIENT
        jsUpdateSkyTime(scene, normalizedTime)
        jsUpdateWeather(scene, predX, predY, predZ)

        jsDrawMinimap(predX, predZ, yaw)

        val toDeg = 180.0 / kotlin.math.PI
        val targetBlockName =
            target?.let { chunkManager.getBlockAtWorld(it.x, it.y, it.z).name } ?: ""
        val gameTimeDisplay = ticksToHHMM(currentGameTicks)
        jsUpdateHUD(
            hudX,
            hudY,
            hudZ,
            yaw * toDeg,
            pitch * toDeg,
            hudStance,
            hudSpeed,
            currentFps,
            currentKbIn,
            currentKbOut,
            hudBiome,
            targetBlockName,
            gameTimeDisplay)
        uiState.hud =
            HudData(
                x = hudX,
                y = hudY,
                z = hudZ,
                yaw = yaw * toDeg,
                pitch = pitch * toDeg,
                stance = hudStance,
                speed = hudSpeed,
                fps = currentFps,
                kbIn = currentKbIn,
                kbOut = currentKbOut,
                biome = hudBiome,
                targetBlock = targetBlockName,
                gameTime = gameTimeDisplay,
            )
    }

    fun buildMoveIntent(): ClientMessage.MoveIntent {
        val fwdX = jsGetCameraForwardX(camera).toFloat()
        val fwdZ = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ
        val rightZ = -fwdX

        var dx = 0f
        var dz = 0f
        if (jsIsActionDown("forward")) {
            dx += fwdX
            dz += fwdZ
        }
        if (jsIsActionDown("backward")) {
            dx -= fwdX
            dz -= fwdZ
        }
        if (jsIsActionDown("strafe_right")) {
            dx += rightX
            dz += rightZ
        }
        if (jsIsActionDown("strafe_left")) {
            dx -= rightX
            dz -= rightZ
        }

        val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (len > 1f) {
            dx /= len
            dz /= len
        }

        val flyToggle = pendingFlyToggle.also { pendingFlyToggle = false }
        val speedUp = jsIsActionDown("speed_up")
        val speedDown = jsIsActionDown("speed_down")

        return if (localFlying) {
            val dy =
                when {
                    jsIsActionDown("ascend") -> 1f
                    jsIsActionDown("descend") -> -1f
                    else -> {
                        val fwdY = jsGetCameraForwardY(camera).toFloat()
                        var d = 0f
                        if (jsIsActionDown("forward")) d += fwdY
                        if (jsIsActionDown("backward")) d -= fwdY
                        d
                    }
                }
            ClientMessage.MoveIntent(
                dx = dx,
                dz = dz,
                dy = dy,
                yaw = jsGetCameraRotationY(camera).toFloat(),
                pitch = jsGetCameraRotationX(camera).toFloat(),
                stance = PlayerStance.STANDING,
                jump = false,
                flyToggle = flyToggle,
                speedUp = speedUp,
                speedDown = speedDown,
            )
        } else {
            val stance =
                when {
                    jsIsActionDown("crawl") -> PlayerStance.CRAWLING
                    jsIsActionDown("sneak") -> PlayerStance.SNEAKING
                    else -> PlayerStance.STANDING
                }
            ClientMessage.MoveIntent(
                dx = dx,
                dz = dz,
                yaw = jsGetCameraRotationY(camera).toFloat(),
                pitch = jsGetCameraRotationX(camera).toFloat(),
                stance = stance,
                jump = jsIsActionDown("ascend"),
                flyToggle = flyToggle,
                speedUp = speedUp,
                speedDown = speedDown,
            )
        }
    }

    fun reset() {
        hasPrediction = false
        predX = 0.0
        predY = 0.0
        predZ = 0.0
        predVy = 0.0
        serverX = 0.0
        serverY = 0.0
        serverZ = 0.0
        lastPlayerCx = Int.MIN_VALUE
        lastPlayerCz = Int.MIN_VALUE
        pendingFlyToggle = false
        lastSentIntent = null
        breakTarget = null
        hoverTarget = null
        jsHideBreakOverlay()
        jsHideTargetOutline()
        localPlayerModel?.let { jsDisposePlayerModel(it) }
        localPlayerModel = null
        fpArms?.let { jsDisposeFPArms(it) }
        fpArms = null
    }

    private fun raycastBlock(maxDist: Float = 5f): RaycastResult? {
        val ox = predX
        val oy = predY + localStance.eyeOffset.toDouble()
        val oz = predZ
        val dx = jsGetCameraDir3DX(camera).toFloat()
        val dy = jsGetCameraDir3DY(camera).toFloat()
        val dz = jsGetCameraDir3DZ(camera).toFloat()

        var bx = kotlin.math.round(ox).toInt()
        var by = kotlin.math.round(oy).toInt()
        var bz = kotlin.math.round(oz).toInt()
        var prevBx = bx
        var prevBy = by
        var prevBz = bz

        val sx = if (dx > 0) 1 else if (dx < 0) -1 else 0
        val sy = if (dy > 0) 1 else if (dy < 0) -1 else 0
        val sz = if (dz > 0) 1 else if (dz < 0) -1 else 0

        val tDeltaX = if (dx != 0f) kotlin.math.abs(1f / dx) else Float.MAX_VALUE
        val tDeltaY = if (dy != 0f) kotlin.math.abs(1f / dy) else Float.MAX_VALUE
        val tDeltaZ = if (dz != 0f) kotlin.math.abs(1f / dz) else Float.MAX_VALUE

        var tMaxX =
            if (dx > 0) ((bx + 0.5 - ox) / dx).toFloat()
            else if (dx < 0) ((bx - 0.5 - ox) / dx).toFloat() else Float.MAX_VALUE
        var tMaxY =
            if (dy > 0) ((by + 0.5 - oy) / dy).toFloat()
            else if (dy < 0) ((by - 0.5 - oy) / dy).toFloat() else Float.MAX_VALUE
        var tMaxZ =
            if (dz > 0) ((bz + 0.5 - oz) / dz).toFloat()
            else if (dz < 0) ((bz - 0.5 - oz) / dz).toFloat() else Float.MAX_VALUE

        while (true) {
            val t = minOf(tMaxX, tMaxY, tMaxZ)
            if (t > maxDist) break
            if (chunkManager.getBlockAtWorld(bx, by, bz) != BlockType.AIR) {
                if (by < 0 || by > WorldConstants.WORLD_MAX_Y) return null
                val adjY = prevBy.coerceIn(0, WorldConstants.WORLD_MAX_Y)
                return RaycastResult(BlockPos(bx, by, bz), BlockPos(prevBx, adjY, prevBz))
            }
            prevBx = bx
            prevBy = by
            prevBz = bz
            when {
                tMaxX < tMaxY && tMaxX < tMaxZ -> {
                    bx += sx
                    tMaxX += tDeltaX
                }
                tMaxY < tMaxZ -> {
                    by += sy
                    tMaxY += tDeltaY
                }
                else -> {
                    bz += sz
                    tMaxZ += tDeltaZ
                }
            }
        }
        return null
    }

    private fun ticksToHHMM(ticks: Long): String {
        val day = ticks % TICKS_PER_DAY_CLIENT
        val h = (day * 24 / TICKS_PER_DAY_CLIENT).toInt()
        val m = ((day * 24 * 60 / TICKS_PER_DAY_CLIENT) % 60).toInt()
        return h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
    }
}
