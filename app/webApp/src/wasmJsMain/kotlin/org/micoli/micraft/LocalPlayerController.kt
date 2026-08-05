package org.micoli.micraft

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.*
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.NetworkStats
import org.micoli.micraft.game.NpcManager
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.PlayerConstants
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.player.height
import org.micoli.micraft.player.speed
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.ui.HudData
import org.micoli.micraft.ui.McUiState

private const val PRED_DT = 16.0 / 1000.0
private const val SNAP_THRESHOLD = 0.5
private const val FLY_VERTICAL_SPEED = 8f
private const val DEFAULT_RECONCILE_TOLERANCE_XZ = 0.5
private const val DEFAULT_RECONCILE_TOLERANCE_Y = 0.99
private const val STATS_WINDOW = 1000
private const val JITTER_SNAPSHOT_WINDOW = 1000
private const val CLIENT_GRAVITY = -20.0
private const val CLIENT_JUMP_SPEED = 8.5
private const val TICKS_PER_DAY_CLIENT = 72_000L
private const val MAX_AUTO_TARGET_RANGE_SQ = 30.0 * 30.0

private enum class ViewMode {
    FIRST_PERSON,
    THIRD_PERSON,
    FIRST_PERSON_NO_ARMS;

    fun next(): ViewMode = entries[(ordinal + 1) % entries.size]
}

private data class RaycastResult(
    val target: BlockPos,
    val adjacent: BlockPos,
    val hitX: Float = 0f,
    val hitZ: Float = 0f
)

class LocalPlayerController(
    private val scene: JsAny,
    private val camera: JsAny,
    private val outMessages: Channel<ClientMessage>,
    private val chunkManager: ChunkManager,
    private val uiState: McUiState,
    private val networkStats: NetworkStats,
    private val serverHost: () -> String,
    private val serverPort: () -> Int,
    private val playerName: () -> String,
    private val playerId: () -> String,
    private val npcManager: NpcManager,
) {
    var predX = 0.0
    var predY = 0.0
    var predZ = 0.0
    var predVy = 0.0
    var serverX = 0.0
    var serverY = 0.0
    var serverZ = 0.0
    var hasPrediction = false
    private var prevPredX = 0.0
    private var prevPredY = 0.0
    private var prevPredZ = 0.0
    private var prevEyeOffset = 0.0
    var localFlying = false
    var localStance = PlayerStance.STANDING
    var localSpeedMult = 1f
    var localSkin = "player"
    var localArmors: List<String> = emptyList()
    var localArmorsAttached: List<String> = emptyList()
    var lastPlayerCx = Int.MIN_VALUE
    var lastPlayerCz = Int.MIN_VALUE
    private var viewMode: ViewMode = ViewMode.FIRST_PERSON
    var pendingFlyToggle = false
    var autoAdvance = false
    var lastSentIntent: ClientMessage.MoveIntent? = null
    var disconnectRequested = false

    @OptIn(ExperimentalWasmJsInterop::class) var localPlayerModel: JsAny? = null

    var currentCombatTargetId: String? = null
    var autoTargetEnabled: Boolean = true
    private var breakTarget: BlockPos? = null
    private var hoverTarget: BlockPos? = null
    var selectedSlot: Int = 0
    var currentPage: Int = 0
    val shortcutBarPages: Array<Array<ShortcutSlot?>> = Array(10) { arrayOfNulls(10) }
    private var hasPlacedThisClick = false
    var placementRotation: Int = 0 // 0-3, cycled with R key
    private var ghostAdjacentPos: BlockPos? = null
    private var lastGhostRotation: Int = -1
    private var lastGhostColorIdx: Int = -1
    private var lastGhostXOffset: Int = -1
    private var lastGhostZOffset: Int = -1
    private var lastMinimapPlacementRot: Int = -2

    private var hudX = 0.0
    private var hudY = 0.0
    private var hudZ = 0.0
    private var hudStance = "STANDING"
    private var hudSpeed = 1.0
    private var hudBiome = ""
    private var hudWeather = ""
    private var hudZoneLevel = 0
    var currentGameTicks = 0L

    var maxInteractionDistance: Float = 7f

    private var reconcileToleranceXz = DEFAULT_RECONCILE_TOLERANCE_XZ
    private var reconcileToleranceY = DEFAULT_RECONCILE_TOLERANCE_Y
    private var reconcileCountXz = 0
    private var reconcileCountY = 0
    private var totalClientTicks = 0
    private var totalServerUpdates = 0
    private val xzDistances = ArrayDeque<Double>()
    private val yDistances = ArrayDeque<Double>()

    private var fpsFrameCount = 0
    private var fpsWindowStart = jsNow()
    private var hudTickCounter = 0
    private var currentFps = 0
    private var currentKbIn = 0.0
    private var currentKbOut = 0.0
    private var lastTickMs = 0.0
    private val tickIntervals = ArrayDeque<Double>()
    private val jitterSnapshots = ArrayDeque<Double>()
    var chunkDownloading = 0
    var chunkMeshing = 0

    fun setReconcileTolerances(xz: Double, y: Double) {
        reconcileToleranceXz = xz
        reconcileToleranceY = y
    }

    private fun ArrayDeque<Double>.addCapped(value: Double) {
        addLast(value)
        if (size > STATS_WINDOW) removeFirst()
    }

    private fun ArrayDeque<Double>.tickJitter(): Double {
        if (size < 2) return 0.0
        val mean = average()
        return kotlin.math.sqrt(sumOf { (it - mean) * (it - mean) } / size)
    }

    private fun ArrayDeque<Double>.addJitterSnapshot(value: Double) {
        addLast(value)
        if (size > JITTER_SNAPSHOT_WINDOW) removeFirst()
    }

    private fun Double.r3(): String {
        val v = (kotlin.math.round(this * 1000) / 1000.0).toString()
        val dot = v.indexOf('.')
        return if (dot < 0) "$v.000" else v.padEnd(dot + 4, '0').take(dot + 4)
    }

    private fun reconcileStats(distances: ArrayDeque<Double>, count: Int, total: Int): String {
        val pct = if (total > 0) count * 100 / total else 0
        if (distances.isEmpty()) return "$count/$total ($pct%)"
        var sum = 0.0
        for (d in distances) sum += d
        val avg = sum / distances.size
        var sumSq = 0.0
        for (d in distances) sumSq += (d - avg) * (d - avg)
        val std = kotlin.math.sqrt(sumSq / distances.size)
        return "$count/$total ($pct%) avg=${avg.r3()} ±${std.r3()}"
    }

    /**
     * Height of the camera above the feet. Uses the skin's eye anchor
     * (`resources/skins/<skin>/<skin>.yaml`) when the skin declares one — the camera then sits at
     * the middle of the head, at eye level — scaled by the stance so sneaking and crawling still
     * lower the view. Falls back to the stance eye offset for skins without a yaml.
     */
    private fun cameraEyeOffset(): Double {
        val skinEyeHeight =
            if (jsIsSkinConfigReady(localSkin)) jsGetSkinEyeHeight(localSkin) else 0.0
        if (skinEyeHeight <= 0.0) return localStance.eyeOffset.toDouble()
        return skinEyeHeight * (localStance.eyeOffset / PlayerConstants.EYE_OFFSET_STANDING)
    }

    fun setViewMode(mode: String) {
        viewMode = ViewMode.entries.firstOrNull { it.name == mode } ?: ViewMode.FIRST_PERSON
    }

    fun updateFromServer(state: PlayerState, onChunkChanged: (Int, Int) -> Unit) {
        localFlying = state.flying
        localStance = state.stance
        localSpeedMult = state.speedMultiplier
        if (state.skin != localSkin) {
            localSkin = state.skin
            jsInitPlayerModel(localSkin)
            jsInitSkinConfig(localSkin)
            localPlayerModel?.let { jsDisposePlayerModel(it) }
            localPlayerModel = null
            localArmorsAttached = emptyList()
        }
        if (state.armors != localArmors) {
            localArmors = state.armors
            localArmors.forEach { jsInitArmorModel(it) }
        }

        serverX = state.pos.x.toDouble()
        serverY = state.pos.y.toDouble()
        serverZ = state.pos.z.toDouble()

        if (!hasPrediction) {
            predX = serverX
            predZ = serverZ
            predY = serverY
            predVy = 0.0
            prevPredX = serverX
            prevPredY = serverY
            prevPredZ = serverZ
            prevEyeOffset = cameraEyeOffset()
            hasPrediction = true
            jsSetCameraRotationY(camera, state.orientation.yaw.toDouble())
            jsSetCameraRotationX(camera, state.orientation.pitch.toDouble())
        } else {
            totalServerUpdates++
            val diffY = serverY - predY
            val absY = kotlin.math.abs(diffY)
            when {
                absY > 1.0 -> {
                    predY = serverY
                    predVy = 0.0
                }
                absY > reconcileToleranceY -> {
                    predY += diffY * 0.2
                    reconcileCountY++
                    yDistances.addCapped(absY)
                }
            }
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
        hudZoneLevel = state.zoneLevel
        chunkManager.applyBiomeGrassTint(state.biome)
    }

    fun cyclePage(direction: Int) {
        val nonEmpty = (0..9).filter { p -> shortcutBarPages[p].drop(1).any { it != null } }
        if (nonEmpty.isEmpty()) return
        val idx = nonEmpty.indexOf(currentPage).coerceAtLeast(0)
        currentPage = nonEmpty[(idx + direction + nonEmpty.size) % nonEmpty.size]
        syncShortcutBarToUi()
    }

    fun goToPage(page: Int) {
        currentPage = page.coerceIn(0, 9)
        syncShortcutBarToUi()
    }

    fun activateSlot(index: Int) {
        val slot = shortcutBarPages[currentPage].getOrNull(index)
        if (slot is ShortcutSlot.Macro) {
            outMessages.trySend(ClientMessage.RunMacro(slot.macroName))
        } else if (slot is ShortcutSlot.Spell) {
            val (tx, ty, tz) = computeAoeTarget(15f)
            outMessages.trySend(
                ClientMessage.CastAoeSpell(
                    spellId = slot.spellId,
                    targetX = tx,
                    targetY = ty,
                    targetZ = tz,
                ))
        } else if (slot is ShortcutSlot.Item) {
            val def = ItemRegistry.get(slot.itemType)
            if (def.consumable) {
                outMessages.trySend(ClientMessage.UseItem(slot.itemType))
            } else {
                selectSlot(index)
            }
        } else {
            selectSlot(index)
            if (slot is ShortcutSlot.Attack) {
                val targetId = currentCombatTargetId ?: return
                outMessages.trySend(
                    ClientMessage.AttackTarget(
                        targetId = targetId,
                        isNpc = true,
                        attackId = slot.attackId,
                        attackLevel = slot.level))
            }
        }
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
        val currentSlots = shortcutBarPages[currentPage]
        val slotsJson =
            currentSlots.joinToString(",") { slot ->
                when (slot) {
                    is ShortcutSlot.Item -> """{"kind":"item","id":"${slot.itemType.id}"}"""
                    is ShortcutSlot.Attack ->
                        """{"kind":"attack","id":"${slot.attackId}:${slot.level}"}"""
                    is ShortcutSlot.Macro -> """{"kind":"macro","id":"${slot.macroName}"}"""
                    is ShortcutSlot.Spell -> """{"kind":"spell","id":"${slot.spellId}"}"""
                    null -> "null"
                }
            }
        val nonEmptyPages = (0..9).filter { p -> shortcutBarPages[p].drop(1).any { it != null } }
        val nonEmptyJson = nonEmptyPages.joinToString(",")
        jsUpdateShortcutBar(
            "{\"slots\":[$slotsJson],\"selected\":$selectedSlot,\"page\":$currentPage,\"nonEmptyPages\":[$nonEmptyJson]}")
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
        if (totalClientTicks == 0) {
            jsConsoleLog("[debug] build $BUILD_TIMESTAMP (wasm)")
            jsSetWasmBuildTimestamp(BUILD_TIMESTAMP)
        }
        totalClientTicks++
        val nowMs = jsNow()
        val actualDt =
            if (lastTickMs == 0.0) PRED_DT
            else ((nowMs - lastTickMs) / 1000.0).coerceIn(0.008, 0.05)
        if (lastTickMs != 0.0) tickIntervals.addCapped(nowMs - lastTickMs)
        lastTickMs = nowMs
        val consoleInput = jsConsumeConsoleInput()
        if (consoleInput.isNotEmpty()) {
            when (consoleInput.trim()) {
                "/keyreload" -> {
                    jsLoadBindings(serverHost(), serverPort(), playerName())
                    jsShowNotification("Keybindings reloaded")
                }
                "/disconnect" -> disconnectRequested = true
                else ->
                    if (consoleInput.startsWith("/")) {
                        outMessages.trySend(ClientMessage.Command(enrichCommand(consoleInput)))
                    } else {
                        outMessages.trySend(
                            ClientMessage.ChatSend(jsGetActiveChannel(), consoleInput))
                    }
            }
        }
        if (jsIsConsoleInputFocused()) return

        val fwdX = jsGetCameraForwardX(camera).toFloat()
        val fwdZ = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ
        val rightZ = -fwdX

        val turnSpeed = (2.5f * actualDt).toFloat()
        if (jsIsActionDown("rotate_left")) jsRotateCameraYaw(camera, -turnSpeed)
        if (jsIsActionDown("rotate_right")) jsRotateCameraYaw(camera, turnSpeed)

        if (jsIsActionDown("backward")) autoAdvance = false

        var dx = 0f
        var dz = 0f
        if (jsIsActionDown("forward") || autoAdvance) {
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
        val speed = stance.speed * localSpeedMult * actualDt.toFloat()
        val solid = { bx: Int, by: Int, bz: Int ->
            chunkManager.getBlockAtWorld(bx, by, bz).isSolid
        }
        val h = stance.height
        val startX = predX
        val startZ = predZ
        val midDx =
            AabbCollider.resolveX(
                solid,
                startX.toFloat(),
                predY.toFloat(),
                startZ.toFloat(),
                PlayerConstants.WIDTH,
                h,
                dx * speed)
        val midX = startX + midDx
        val resolvedDz =
            AabbCollider.resolveZ(
                solid,
                midX.toFloat(),
                predY.toFloat(),
                startZ.toFloat(),
                PlayerConstants.WIDTH,
                h,
                dz * speed)
        predZ = startZ + resolvedDz
        val resolvedDx =
            AabbCollider.resolveX(
                solid,
                startX.toFloat(),
                predY.toFloat(),
                predZ.toFloat(),
                PlayerConstants.WIDTH,
                h,
                dx * speed)
        predX = startX + resolvedDx

        if (autoAdvance && (dx != 0f || dz != 0f)) {
            val intendedSq = (dx * speed) * (dx * speed) + (dz * speed) * (dz * speed)
            val resolvedSq = resolvedDx * resolvedDx + resolvedDz * resolvedDz
            if (resolvedSq < intendedSq * 0.01f) autoAdvance = false
        }

        if (localFlying) {
            val fwdY = jsGetCameraForwardY(camera).toFloat()
            var dy = 0f
            if (jsIsActionDown("ascend")) dy = 1f
            else if (jsIsActionDown("descend")) dy = -1f
            else {
                if (jsIsActionDown("forward") || autoAdvance) dy += fwdY
                if (jsIsActionDown("backward")) dy -= fwdY
            }
            val flyDy = (dy * FLY_VERTICAL_SPEED * localSpeedMult * actualDt).toFloat()
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
                predVy += CLIENT_GRAVITY * actualDt
                val dy = (predVy * actualDt).toFloat()
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
        val distXZ = kotlin.math.sqrt(diffX * diffX + diffZ * diffZ)
        when {
            distXZ > SNAP_THRESHOLD && !isMovingXZ -> {
                predX = serverX
                predZ = serverZ
            }
            distXZ > 5.0 -> {
                predX = serverX
                predZ = serverZ
            }
            !isMovingXZ && distXZ > reconcileToleranceXz -> {
                predX += diffX * 0.3
                predZ += diffZ * 0.3
                reconcileCountXz++
                xzDistances.addCapped(distXZ)
            }
            isMovingXZ && distXZ > reconcileToleranceXz -> {
                predX += diffX * 0.15
                predZ += diffZ * 0.15
                reconcileCountXz++
                xzDistances.addCapped(distXZ)
            }
        }

        val events = jsConsumeEvents()
        repeat(jsEventsLength(events)) { i ->
            val event = jsEventsGet(events, i)
            when {
                event == "view_toggle" -> {
                    viewMode = viewMode.next()
                    outMessages.trySend(ClientMessage.ViewModeUpdate(viewMode.name))
                }
                event == "inventory" -> jsToggleHotbar()
                event == "screenshot" -> jsTakeScreenshot(scene, camera, playerId())
                event == "undo" -> outMessages.trySend(ClientMessage.Command("/undo 1"))
                event == "fly_toggle" -> pendingFlyToggle = true
                event == "auto_forward" -> autoAdvance = !autoAdvance
                event == "place_rotate" -> placementRotation = (placementRotation + 1) % 4
                event == "slot_1" -> activateSlot(0)
                event == "slot_2" -> activateSlot(1)
                event == "slot_3" -> activateSlot(2)
                event == "slot_4" -> activateSlot(3)
                event == "slot_5" -> activateSlot(4)
                event == "slot_6" -> activateSlot(5)
                event == "slot_7" -> activateSlot(6)
                event == "slot_8" -> activateSlot(7)
                event == "slot_9" -> activateSlot(8)
                event == "slot_10" -> activateSlot(9)
                event == "shortcut_page_prev" -> cyclePage(-1)
                event == "shortcut_page_next" -> cyclePage(1)
                event.startsWith("shortcut_page_") -> {
                    val n = event.removePrefix("shortcut_page_").toIntOrNull()
                    if (n != null && n in 1..10) goToPage(n - 1)
                }
                event == "combat_target_cycle" -> {
                    val next =
                        npcManager.cycleNearestNpc(
                            predX,
                            predY,
                            predZ,
                            jsGetCameraRotationY(camera),
                            currentCombatTargetId)
                    currentCombatTargetId = next
                    npcManager.setHighlightTarget(next)
                    outMessages.trySend(ClientMessage.SetCombatTarget(next, isNpc = true))
                }
                event == "combat_attack" -> {
                    val targetId = currentCombatTargetId ?: return@repeat
                    val slot =
                        shortcutBarPages[currentPage].getOrNull(selectedSlot)
                            as? ShortcutSlot.Attack
                    outMessages.trySend(
                        ClientMessage.AttackTarget(
                            targetId = targetId,
                            isNpc = true,
                            attackId = slot?.attackId ?: "basic_attack",
                            attackLevel = slot?.level ?: 1))
                }
                event.startsWith("cmd:") ->
                    outMessages.trySend(ClientMessage.Command(event.removePrefix("cmd:")))
                event.startsWith("macro:") ->
                    outMessages.trySend(ClientMessage.RunMacro(event.removePrefix("macro:")))
                event.startsWith("attack:") -> {
                    val targetId = currentCombatTargetId ?: return@repeat
                    val rest = event.removePrefix("attack:")
                    val lastColon = rest.lastIndexOf(':')
                    val attackId = if (lastColon > 0) rest.substring(0, lastColon) else rest
                    val attackLevel =
                        if (lastColon > 0) rest.substring(lastColon + 1).toIntOrNull() ?: 1 else 1
                    outMessages.trySend(
                        ClientMessage.AttackTarget(
                            targetId = targetId,
                            isNpc = true,
                            attackId = attackId,
                            attackLevel = attackLevel))
                }
                event.startsWith("spell:") -> {
                    val spellId = event.removePrefix("spell:")
                    outMessages.trySend(ClientMessage.UseSpell(spellId = spellId))
                }
            }
        }

        val targetId = currentCombatTargetId
        if (targetId != null) {
            val dist2 = npcManager.npcDistanceSquared(targetId, predX, predY, predZ)
            val inRange = dist2 != null && dist2 <= MAX_AUTO_TARGET_RANGE_SQ
            if (!inRange && !npcManager.isAggroOnPlayer(targetId)) {
                currentCombatTargetId = null
                npcManager.setHighlightTarget(null)
                outMessages.trySend(ClientMessage.SetCombatTarget(null, isNpc = true))
            }
        }

        if (autoTargetEnabled && currentCombatTargetId == null) {
            val nearest = npcManager.nearestAggroNpc(predX, predY, predZ)
            if (nearest != null) {
                currentCombatTargetId = nearest
                npcManager.setHighlightTarget(nearest)
                outMessages.trySend(ClientMessage.SetCombatTarget(nearest, isNpc = true))
            }
        }

        val layoutUpdateJson = jsConsumeLayoutUpdate()
        if (layoutUpdateJson.isNotEmpty()) {
            runCatching {
                val msg = Json.decodeFromString<ClientMessage.LayoutUpdate>(layoutUpdateJson)
                outMessages.trySend(msg)
            }
        }

        val runMacroScript = jsConsumeRunMacroScript()
        if (runMacroScript.isNotEmpty()) {
            outMessages.trySend(ClientMessage.RunMacroContent(runMacroScript))
        }

        val preferencesUpdateJson = jsConsumePreferencesUpdate()
        if (preferencesUpdateJson.isNotEmpty()) {
            runCatching {
                    val msg =
                        Json.decodeFromString<ClientMessage.PreferencesUpdate>(
                            preferencesUpdateJson)
                    outMessages.trySend(msg)
                }
                .onFailure { e ->
                    jsError(
                        "PreferencesUpdate decode failed: ${e.message} | json=$preferencesUpdateJson")
                }
        }

        val slotUpdateJson = jsConsumeSlotUpdate()
        if (slotUpdateJson.isNotEmpty()) {
            runCatching {
                val slotMatch =
                    Regex("\"slot\":(\\d+)").find(slotUpdateJson)?.groupValues?.get(1)?.toInt()
                if (slotMatch != null && slotMatch in 1..9) {
                    val kind =
                        Regex("\"kind\":\"([^\"]+)\"").find(slotUpdateJson)?.groupValues?.get(1)
                    val id = Regex("\"id\":\"([^\"]+)\"").find(slotUpdateJson)?.groupValues?.get(1)
                    val content: ShortcutSlot? =
                        when {
                            kind == "item" && id != null ->
                                ItemRegistry.keys()
                                    .find { it.id == id }
                                    ?.let { ShortcutSlot.Item(it) }
                            kind == "attack" && id != null -> {
                                val colon = id.lastIndexOf(':')
                                if (colon > 0)
                                    ShortcutSlot.Attack(
                                        id.substring(0, colon),
                                        id.substring(colon + 1).toIntOrNull() ?: 1)
                                else ShortcutSlot.Attack(id)
                            }
                            kind == "macro" && id != null -> ShortcutSlot.Macro(id)
                            kind == "spell" && id != null -> ShortcutSlot.Spell(id)
                            else -> null
                        }
                    shortcutBarPages[currentPage][slotMatch] = content
                    syncShortcutBarToUi()
                    outMessages.trySend(
                        ClientMessage.ShortcutBarSet(
                            page = currentPage, slot = slotMatch, content = content))
                }
            }
        }

        if (jsIsPlayerBbmodelReady(localSkin) && localPlayerModel == null) {
            localPlayerModel = jsCreatePlayerModelNow(scene, localSkin)
            jsSetPlayerVisible(localPlayerModel!!, false)
        }
        localPlayerModel?.let { model ->
            if (localArmors != localArmorsAttached) {
                (localArmorsAttached - localArmors.toSet()).forEach { jsDetachArmor(model, it) }
                val toAttach = localArmors - localArmorsAttached.toSet()
                val readyToAttach = toAttach.filter { jsIsArmorModelReady(it) }
                readyToAttach.forEach { jsAttachArmor(model, it, scene) }
                localArmorsAttached = localArmorsAttached - toAttach.toSet() + readyToAttach
            }
        }

        val yaw = jsGetCameraRotationY(camera)
        val pitch = jsGetCameraRotationX(camera)

        val eyeOffset = cameraEyeOffset()
        if (viewMode == ViewMode.THIRD_PERSON) {
            val dist = 3.0
            val camX = predX - kotlin.math.sin(yaw) * dist
            val camY = predY + eyeOffset + 0.3
            val camZ = predZ - kotlin.math.cos(yaw) * dist
            jsClearCameraInterpolation()
            jsCameraSetPosition(camera, camX, camY, camZ)
            localPlayerModel?.let {
                jsSetPlayerTransform(
                    it, predX, predY, predZ, yaw.toFloat(), pitch.toFloat(), isMovingXZ)
                jsSetPlayerFirstPerson(it, localSkin, false)
                jsSetPlayerVisible(it, true)
            }
        } else {
            // Camera sits at the middle of the head, at the skin's eye height.
            jsSetCameraInterpolationState(
                prevPredX,
                prevPredY + prevEyeOffset,
                prevPredZ,
                predX,
                predY + eyeOffset,
                predZ,
                lastTickMs)
            val showBody = viewMode == ViewMode.FIRST_PERSON
            localPlayerModel?.let {
                if (showBody) {
                    jsSetPlayerTransform(
                        it, predX, predY, predZ, yaw.toFloat(), pitch.toFloat(), isMovingXZ)
                    jsSetPlayerFirstPerson(it, localSkin, true)
                }
                jsSetPlayerVisible(it, showBody)
            }
        }
        prevPredX = predX
        prevPredY = predY
        prevPredZ = predZ
        prevEyeOffset = eyeOffset

        val rayResult = raycastBlock(maxInteractionDistance)
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
        val selectedSlotContent =
            if (selectedSlot > 0) shortcutBarPages[currentPage][selectedSlot] else null
        val selectedItem = (selectedSlotContent as? ShortcutSlot.Item)?.itemType
        val isPlaceMode = selectedItem != null && selectedItem.buildable

        if (isPlaceMode) {
            val rawAdjacent = rayResult?.adjacent
            val isFractionalItem =
                selectedItem.placesBlock?.let { BlockRegistry.get(it).heightFraction < 1.0f }
                    ?: false
            // Redirect ghost for fractional plates only on lateral clicks (satellite → master).
            // Above-stud clicks (rawAdjacent.y > target.y) keep ghost at y+1 so it appears
            // above the existing plates; the server handles the actual sub-voxel stacking.
            val adjacent =
                if (isFractionalItem &&
                    rawAdjacent != null &&
                    target != null &&
                    rawAdjacent.y == target.y)
                    chunkManager.resolveFractionalPlacementPos(rawAdjacent)
                else rawAdjacent

            val ghostColorIdx =
                if (selectedItem.placesBlock?.let { BlockRegistry.get(it).plainColorable } == true)
                    PlainColorRegistry.indexOf(ItemRegistry.get(selectedItem).plainColor)
                else 0

            // Compute XZ sub-voxel slot from hit position within adjacent voxel
            val blockDef = selectedItem.placesBlock?.let { BlockRegistry.get(it) }
            val brickSizeX = blockDef?.brickSize?.getOrElse(0) { 1f } ?: 1f
            val brickSizeZ = blockDef?.brickSize?.getOrElse(2) { 1f } ?: 1f
            val effectiveFracX = if (placementRotation % 2 == 0) brickSizeX else brickSizeZ
            val effectiveFracZ = if (placementRotation % 2 == 0) brickSizeZ else brickSizeX
            val ghostXOffset: Int
            val ghostZOffset: Int
            val studStepX =
                when {
                    effectiveFracX < 1.0f -> effectiveFracX
                    effectiveFracX > 1.0f -> 0.5f // multi-voxel LEGO: 1-stud (0.5-block) precision
                    else -> 0f
                }
            val studStepZ =
                when {
                    effectiveFracZ < 1.0f -> effectiveFracZ
                    effectiveFracZ > 1.0f -> 0.5f
                    else -> 0f
                }
            if (adjacent != null && (studStepX > 0f || studStepZ > 0f)) {
                val hitX = rayResult?.hitX ?: 0f
                val hitZ = rayResult?.hitZ ?: 0f
                val fracX = (hitX - adjacent.x).coerceIn(0f, 0.9999f)
                val fracZ = (hitZ - adjacent.z).coerceIn(0f, 0.9999f)
                ghostXOffset =
                    if (studStepX > 0f) kotlin.math.floor(fracX / studStepX).toInt().coerceIn(0, 1)
                    else 0
                ghostZOffset =
                    if (studStepZ > 0f) kotlin.math.floor(fracZ / studStepZ).toInt().coerceIn(0, 1)
                    else 0
            } else {
                ghostXOffset = 0
                ghostZOffset = 0
            }

            if (adjacent != ghostAdjacentPos ||
                placementRotation != lastGhostRotation ||
                ghostColorIdx != lastGhostColorIdx ||
                ghostXOffset != lastGhostXOffset ||
                ghostZOffset != lastGhostZOffset) {
                ghostAdjacentPos = adjacent
                lastGhostRotation = placementRotation
                lastGhostColorIdx = ghostColorIdx
                lastGhostXOffset = ghostXOffset
                lastGhostZOffset = ghostZOffset
                if (adjacent != null) {
                    val typeOrd =
                        selectedItem.placesBlock?.let { BlockRegistry.wireIndex(it) } ?: -1
                    if (typeOrd < 0)
                        jsWarn(
                            "Ghost: block '${selectedItem.placesBlock?.id}' not in registry (wireIndex=-1)")
                    jsShowBlockPreview(
                        scene,
                        adjacent.x,
                        adjacent.y,
                        adjacent.z,
                        typeOrd,
                        placementRotation,
                        ghostColorIdx,
                        ghostXOffset,
                        ghostZOffset,
                    )
                    // Show placement wireframe at adjacent+offset (arch-shaped, not 1×1 target
                    // cube)
                    if (blockDef?.brickSize?.let { bs -> bs[0] != 1f || bs[2] != 1f } == true) {
                        jsShowTargetOutline(
                            scene,
                            adjacent.x,
                            adjacent.y,
                            adjacent.z,
                            true,
                            typeOrd,
                            placementRotation,
                            ghostXOffset,
                            ghostZOffset,
                        )
                    }
                } else {
                    jsHideBlockPreview()
                }
            }

            if (isBreaking && adjacent != null && !hasPlacedThisClick) {
                hasPlacedThisClick = true
                breakTarget = adjacent
                outMessages.trySend(
                    ClientMessage.BlockPlace(
                        adjacent,
                        selectedItem,
                        placementRotation.toByte(),
                        ghostXOffset.toByte(),
                        ghostZOffset.toByte()))
            } else if (!isBreaking) {
                breakTarget = null
                hasPlacedThisClick = false
            }
        } else {
            if (ghostAdjacentPos != null) {
                ghostAdjacentPos = null
                jsHideBlockPreview()
            }
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

        val newMinimapRot = if (isPlaceMode) placementRotation else -1
        if (newMinimapRot != lastMinimapPlacementRot) {
            lastMinimapPlacementRot = newMinimapRot
            jsSetPlacementRotation(newMinimapRot)
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
        hudWeather = jsGetCurrentWeather()
        updateCaveLighting()

        jsDrawMinimap(predX, predZ, yaw)

        val toDeg = 180.0 / kotlin.math.PI
        val tickJitter = tickIntervals.tickJitter()
        jitterSnapshots.addJitterSnapshot(tickJitter)
        hudTickCounter++
        if (hudTickCounter >= 10) {
            hudTickCounter = 0
            val targetBlockName =
                target?.let { chunkManager.getBlockAtWorld(it.x, it.y, it.z).id } ?: ""
            val gameTimeDisplay = ticksToHHMM(currentGameTicks)
            val tickAvg = tickIntervals.average().takeIf { it.isFinite() } ?: 0.0
            val tickMin = if (tickIntervals.isEmpty()) 0.0 else tickIntervals.min()
            val tickMax = if (tickIntervals.isEmpty()) 0.0 else tickIntervals.max()
            val jitterMin = if (jitterSnapshots.isEmpty()) 0.0 else jitterSnapshots.min()
            val jitterMax = if (jitterSnapshots.isEmpty()) 0.0 else jitterSnapshots.max()
            val reconcileXz = reconcileStats(xzDistances, reconcileCountXz, totalClientTicks)
            val reconcileY = reconcileStats(yDistances, reconcileCountY, totalServerUpdates)
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
                gameTimeDisplay,
                reconcileXz,
                reconcileY,
                tickAvg,
                tickJitter,
                tickMin,
                tickMax,
                jitterMin,
                jitterMax,
                chunkDownloading,
                chunkMeshing,
                hudWeather,
                hudZoneLevel,
            )
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
                    reconcileXzStats = reconcileXz,
                    reconcileYStats = reconcileY,
                    tickDtMs = tickAvg,
                    tickJitterMs = tickJitter,
                    tickDtMinMs = tickMin,
                    tickDtMaxMs = tickMax,
                    tickJitterMinMs = jitterMin,
                    tickJitterMaxMs = jitterMax,
                    chunkDownloading = chunkDownloading,
                    chunkMeshing = chunkMeshing,
                    weather = hudWeather,
                    zoneLevel = hudZoneLevel,
                )
            val debugCx = hudX.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
            val debugCz = hudZ.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
            jsUpdateChunkDebug(
                chunkManager.getChunkDebugJson(
                    debugCx, debugCz, WorldConstants.FORWARD_VIEW_RADIUS, yaw))
        }
    }

    private var caveLightTarget = 1.0
    private var caveLightCurrent = 1.0
    private var lastLoggedUnderground: Boolean? = null
    var lightBoostEnabled = false
    var nearestRemoteLightBoost: (() -> Triple<Double, Double, Double>?)? = null

    private fun updateCaveLighting() {
        val eyeYInt = (predY + cameraEyeOffset()).toInt()
        val wx = predX.toInt()
        val wz = predZ.toInt()
        val underground =
            (1..60).any { dy ->
                val b = chunkManager.getBlockAtWorld(wx, eyeYInt + dy, wz)
                b != BlockType.AIR && b != BlockType.WATER
            }
        if (underground != lastLoggedUnderground) {
            lastLoggedUnderground = underground
            jsLog(
                "caveFactor: underground=$underground eyeY=$eyeYInt wx=$wx wz=$wz target=${if (underground) 0.3 else 1.0}")
        }
        caveLightTarget = if (underground) 0.3 else 1.0
        caveLightCurrent += (caveLightTarget - caveLightCurrent) * 0.05
        jsSetCaveFactor(caveLightCurrent)
        val eyeY = predY + cameraEyeOffset()
        val remoteBoost = if (!lightBoostEnabled) nearestRemoteLightBoost?.invoke() else null
        val lightX = remoteBoost?.first ?: predX
        val lightY = remoteBoost?.second ?: eyeY
        val lightZ = remoteBoost?.third ?: predZ
        val lightIntensity =
            if (lightBoostEnabled || remoteBoost != null) 2.0
            else (1.0 - caveLightCurrent).coerceAtLeast(0.0)
        jsSetPlayerLight(scene, lightX, lightY, lightZ, lightIntensity)
    }

    fun buildMoveIntent(): ClientMessage.MoveIntent {
        val fwdX = jsGetCameraForwardX(camera).toFloat()
        val fwdZ = jsGetCameraForwardZ(camera).toFloat()
        val rightX = fwdZ
        val rightZ = -fwdX

        var dx = 0f
        var dz = 0f
        if (jsIsActionDown("forward") || autoAdvance) {
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
                        if (jsIsActionDown("forward") || autoAdvance) d += fwdY
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
        prevPredX = 0.0
        prevPredY = 0.0
        prevPredZ = 0.0
        prevEyeOffset = 0.0
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
    }

    private fun computeAoeTarget(maxDist: Float): Triple<Float, Float, Float> {
        val ox = predX
        val oy = predY + cameraEyeOffset()
        val oz = predZ
        val dirX = jsGetCameraDir3DX(camera).toFloat()
        val dirY = jsGetCameraDir3DY(camera).toFloat()
        val dirZ = jsGetCameraDir3DZ(camera).toFloat()
        val hit = raycastBlock(maxDist)
        return if (hit != null) {
            Triple(hit.target.x + 0.5f, hit.target.y + 1.0f, hit.target.z + 0.5f)
        } else {
            Triple(
                (ox + dirX * maxDist).toFloat(),
                (oy + dirY * maxDist).toFloat(),
                (oz + dirZ * maxDist).toFloat(),
            )
        }
    }

    private fun raycastBlock(maxDist: Float = 5f): RaycastResult? {
        val ox = predX
        val oy = predY + cameraEyeOffset()
        val oz = predZ
        val dx = jsGetCameraDir3DX(camera).toFloat()
        val dy = jsGetCameraDir3DY(camera).toFloat()
        val dz = jsGetCameraDir3DZ(camera).toFloat()

        var bx = kotlin.math.floor(ox).toInt()
        var by = kotlin.math.floor(oy).toInt()
        var bz = kotlin.math.floor(oz).toInt()
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
            if (dx > 0) ((bx + 1 - ox) / dx).toFloat()
            else if (dx < 0) ((bx - ox) / dx).toFloat() else Float.MAX_VALUE
        var tMaxY =
            if (dy > 0) ((by + 1 - oy) / dy).toFloat()
            else if (dy < 0) ((by - oy) / dy).toFloat() else Float.MAX_VALUE
        var tMaxZ =
            if (dz > 0) ((bz + 1 - oz) / dz).toFloat()
            else if (dz < 0) ((bz - oz) / dz).toFloat() else Float.MAX_VALUE

        while (true) {
            val t = minOf(tMaxX, tMaxY, tMaxZ)
            if (t > maxDist) break
            if (chunkManager.getBlockAtWorld(bx, by, bz) != BlockType.AIR) {
                if (by < 0 || by > WorldConstants.WORLD_MAX_Y) return null
                val adjY = prevBy.coerceIn(0, WorldConstants.WORLD_MAX_Y)
                val hitX: Float
                val hitZ: Float
                val oxf = ox.toFloat()
                val oyf = oy.toFloat()
                val ozf = oz.toFloat()
                when {
                    bx != prevBx -> {
                        val faceX = if (bx > prevBx) bx.toFloat() else (bx + 1).toFloat()
                        val tHit = if (dx != 0f) (faceX - oxf) / dx else 0f
                        hitX = faceX
                        hitZ = ozf + dz * tHit
                    }
                    bz != prevBz -> {
                        val faceZ = if (bz > prevBz) bz.toFloat() else (bz + 1).toFloat()
                        val tHit = if (dz != 0f) (faceZ - ozf) / dz else 0f
                        hitX = oxf + dx * tHit
                        hitZ = faceZ
                    }
                    else -> {
                        val faceY = if (by > prevBy) by.toFloat() else (by + 1).toFloat()
                        val tHit = if (dy != 0f) (faceY - oyf) / dy else 0f
                        hitX = oxf + dx * tHit
                        hitZ = ozf + dz * tHit
                    }
                }
                return RaycastResult(
                    BlockPos(bx, by, bz), BlockPos(prevBx, adjY, prevBz), hitX, hitZ)
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

    private fun enrichCommand(cmd: String): String {
        val trimmed = cmd.trim()
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size == 2 && parts[0] == "/spawn") {
            val t = hoverTarget ?: return cmd
            return "/spawn ${parts[1]} ${t.x} ${t.y + 1} ${t.z}"
        }
        if (parts.size != 1) return cmd
        return when (parts[0]) {
            "/water" -> {
                val t = hoverTarget
                if (t != null) "/water ${t.x} ${t.y + 1} ${t.z}" else cmd
            }
            "/pump" -> {
                val t = hoverTarget
                if (t != null) "/pump ${t.x} ${t.y} ${t.z}" else cmd
            }
            else -> cmd
        }
    }

    private fun ticksToHHMM(ticks: Long): String {
        val day = ticks % TICKS_PER_DAY_CLIENT
        val h = (day * 24 / TICKS_PER_DAY_CLIENT).toInt()
        val m = ((day * 24 * 60 / TICKS_PER_DAY_CLIENT) % 60).toInt()
        return h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
    }
}
