package org.micoli.micraft

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.*
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.NetworkStats
import org.micoli.micraft.game.NpcManager
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.PlayerConstants
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.placeable.PlaceableRegistry
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.player.height
import org.micoli.micraft.player.speed
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.ui.HudData
import org.micoli.micraft.ui.McUiState

private const val PRED_DT = 16.0 / 1000.0
private const val SNAP_THRESHOLD = 0.5
// Base (speedMult=1) distance beyond which XZ reconciliation hard-snaps instead of smoothly
// lerping — scaled by localSpeedMult at the call site so it stays proportionally far above
// movingToleranceXz regardless of speed.
private const val HARD_SNAP_DISTANCE_XZ = 5.0
private const val FLY_VERTICAL_SPEED = 8f
private const val DEFAULT_RECONCILE_TOLERANCE_XZ = 0.5
private const val DEFAULT_RECONCILE_TOLERANCE_Y = 0.99
private const val STATS_WINDOW_MS = 20_000.0
// Ring buffer of recent per-HUD-tick-block subsystem-timing snapshots, flushed to the browser
// console when a block's frame time exceeds SPIKE_THRESHOLD_MS — lets the user grab a timeline
// of what was happening (mesh/GPU/network ms, faces, bytes) right when FPS dropped, without
// having to eyeball the HUD counters live.
private const val SPIKE_RING_SIZE = 300
// Default only — live-tunable from the browser console via
// `window.mcState.spikeThresholdMs = 200` (see jsGetSpikeThresholdMs), since the default 50ms
// was firing too often to be useful while investigating a known-slow session.
private const val DEFAULT_SPIKE_THRESHOLD_MS = 50.0
private const val SPIKE_LOG_ENTRIES = 20
private const val CLIENT_GRAVITY = -20.0
private const val CLIENT_JUMP_SPEED = 8.5
private const val TICKS_PER_DAY_CLIENT = 72_000L
private const val MAX_AUTO_TARGET_RANGE_SQ = 30.0 * 30.0
// Gap kept between the chase camera and a blocking wall so it never clips inside the face.
private const val CAMERA_COLLISION_MARGIN = 0.2f

private enum class ViewMode {
    FIRST_PERSON,
    THIRD_PERSON,
    FIRST_PERSON_NO_ARMS,
    // Third-person camera that orbits the player with the mouse while the heading is driven
    // "tank style" by the turn keys (Arrow L/R), independent of where the camera looks.
    THIRD_PERSON_ORBIT,
    // Same chase camera as THIRD_PERSON_ORBIT but with a free pointer: no lock, the OS cursor
    // aims block interaction (mouse pick), plain left-click breaks/places, Alt+left-drag orbits.
    THIRD_PERSON_ORBIT_CURSOR;

    fun next(): ViewMode = entries[(ordinal + 1) % entries.size]

    val label: String
        get() =
            when (this) {
                FIRST_PERSON -> "First person"
                THIRD_PERSON -> "Third person"
                FIRST_PERSON_NO_ARMS -> "First person (no arms)"
                THIRD_PERSON_ORBIT -> "Third person orbit"
                THIRD_PERSON_ORBIT_CURSOR -> "Third person orbit (cursor)"
            }

    val isThirdPerson: Boolean
        get() =
            this == THIRD_PERSON || this == THIRD_PERSON_ORBIT || this == THIRD_PERSON_ORBIT_CURSOR
}

// Stats window uses wall-clock time rather than a fixed sample count so it stays a
// consistent ~20s regardless of frame rate or reconcile-event frequency.
private data class TimedValue(val ts: Double, val value: Double)

// One entry per HUD-tick-block (every 10 ticks) — primitives only, no per-frame allocation.
// physicsMs/interactionMs/auxMs are coarse-grained tick() subsystem timings (movement+collision,
// events+raycast+break/place+npc targeting, sky/weather/minimap) added in etape 1.5 after the
// first spike samples showed meshDrainMs+gpuUploadMs (~110ms) didn't explain blockMs (~300ms) —
// see conversation: the bottleneck wasn't the chunk pipeline, so this widens the search to the
// rest of tick(). wsDecodeMs is a snapshot of the running (not-yet-reset) chunk-decode average,
// since that only resets once per second while this pushes every ~10 ticks.
private data class FrameSnapshot(
    val ts: Double,
    val blockMs: Double,
    val meshDrainMs: Double,
    val gpuUploadMs: Double,
    val facesProcessed: Int,
    val bytesIn: Int,
    val physicsMs: Double,
    val interactionMs: Double,
    val auxMs: Double,
    val wsDecodeMs: Double,
    val renderMs: Double,
    val renderFrames: Int,
    val renderMsMax: Double,
    val totalMeshes: Int,
    val activeMeshes: Int,
    val otherTickMs: Double,
)

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
    private val isVehicleTarget: (String) -> Boolean = { false },
    private val vehiclePositionOf: (String) -> Vec3? = { null },
    private val isPlaceableTarget: (String) -> Boolean = { false },
    // Reproduces server SiegeWeaponManager.computeMuzzleAndVelocity for the not-yet-fired
    // trajectory preview (Phase D) — returns null when the placeableId isn't a linked siege
    // weapon or its definition isn't known yet.
    private val siegeWeaponMuzzleAndVelocityOf: (String) -> Pair<Vec3, Vec3>? = { null },
) {
    var isMounted: Boolean = false
    var mountedVehicleId: String? = null
    var predX = 0.0
    var predY = 0.0
    var predZ = 0.0
    var predVy = 0.0
    var serverX = 0.0
    var serverY = 0.0
    var serverZ = 0.0
    // Reconciliation target for the XZ correction in tick() — usually serverX/serverZ
    // corrected by replaying still-unconfirmed sent intents (see updateFromServer), so it
    // doesn't lag behind by a full RTT+tick like the raw server snapshot would.
    private var reconcileTargetX = 0.0
    private var reconcileTargetZ = 0.0

    // (seq, predX, predZ) at the moment each MoveIntent was sent — used to diff the
    // now-confirmed server position against what the client predicted at that same instant,
    // instead of against its current (much further along) prediction.
    private data class SentIntentSnapshot(val seq: Long, val predX: Double, val predZ: Double)

    private val sentIntentHistory = ArrayDeque<SentIntentSnapshot>()
    var hasPrediction = false
    private var prevPredX = 0.0
    private var prevPredY = 0.0
    private var prevPredZ = 0.0
    private var prevEyeOffset = 0.0
    var localFlying = false
    var localStance = PlayerStance.STANDING
    var localSpeedMult = 1f
    var localSkin = "articulated"
    var localArmors: List<String> = emptyList()
    var localArmorsAttached: List<String> = emptyList()
    var localRightHandItem: String? = null
    var localLeftHandItem: String? = null
    var localRightHandAttached: String? = null
    var localLeftHandAttached: String? = null
    var lastPlayerCx = Int.MIN_VALUE
    var lastPlayerCz = Int.MIN_VALUE
    private var viewMode: ViewMode = ViewMode.FIRST_PERSON
    // Client-authoritative heading in THIRD_PERSON_ORBIT: rotated by the turn keys, sent as
    // MoveIntent.yaw and used to orient the body — the camera yaw is a free mouse-driven orbit.
    private var playerYaw: Float = 0f
    private val isOrbit: Boolean
        get() =
            viewMode == ViewMode.THIRD_PERSON_ORBIT ||
                viewMode == ViewMode.THIRD_PERSON_ORBIT_CURSOR

    // Orbit sub-mode with a free (unlocked) pointer: block aim follows the OS cursor via a mouse
    // pick, plain left-click breaks/places, Alt+left-drag orbits the camera.
    private val isOrbitCursor: Boolean
        get() = viewMode == ViewMode.THIRD_PERSON_ORBIT_CURSOR

    var pendingFlyToggle = false
    private var pendingBlockInteract = false
    var autoAdvance = false
    var lastSentIntent: ClientMessage.MoveIntent? = null
    var disconnectRequested = false

    @OptIn(ExperimentalWasmJsInterop::class) var localPlayerModel: JsAny? = null

    var currentCombatTargetId: String? = null
    var autoTargetEnabled: Boolean = true
    var continuousBreak: Boolean = false
    private var breakArmed = true
    private var wasMouseDown = false
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
    private var ghostPlaceablePos: BlockPos? = null
    private var ghostPlaceableType: String? = null

    // View modes the player hid in preferences — skipped by the view-toggle cycle. `/view_mode`
    // ignores this filter. FIRST_PERSON is never disableable, so the cycle always terminates.
    var disabledViewModes: Set<String> = emptySet()

    // Keyboard rotation speed multipliers (deg-scale * dt), configurable in preferences.
    var turnSpeedHorizontal: Float = 2.5f
    var turnSpeedVertical: Float = 1.2f

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
    private var totalClientTicks = 0
    private var totalServerUpdates = 0
    private val xzDistances = ArrayDeque<TimedValue>()
    private val yDistances = ArrayDeque<TimedValue>()
    // Windowed opportunity counts (denominators) for reconcileXz/Y — must share the same
    // 20s window as xzDistances/yDistances (the numerators), or the percentage drifts.
    private val clientTickTimestamps = ArrayDeque<Double>()
    private val serverUpdateTimestamps = ArrayDeque<Double>()

    private var fpsFrameCount = 0
    private var fpsWindowStart = jsNow()
    private var hudTickCounter = 0
    private var currentFps = 0
    private var currentKbIn = 0.0
    private var currentKbOut = 0.0
    private var lastTickMs = 0.0
    private val tickIntervals = ArrayDeque<TimedValue>()
    private val jitterSnapshots = ArrayDeque<TimedValue>()
    private val fpsSamples = ArrayDeque<TimedValue>()
    var chunkDownloading = 0
    var chunkMeshing = 0

    // npcManager.tick()/vehicleManager.tick()/remotePlayerManager.tick() run in GameClient's
    // loop right after localController.tick() returns, inside the same 16ms iteration —
    // wall-clock (blockMs) includes them but no per-subsystem timer did, leaving a gap between
    // blockMs and the sum of physicsMs/interactionMs/auxMs/meshDrainMs/gpuUploadMs/renderMs.
    // GameClient sets this each loop iteration (see GameClient.kt); sampled into the rolling
    // window/spike buffer the same way chunkManager.lastFaceScanMs etc are.
    var otherTickMs: Double = 0.0

    private val meshDrainMsSamples = ArrayDeque<TimedValue>()
    private val gpuUploadMsSamples = ArrayDeque<TimedValue>()
    private val wsDecodeMsSamples = ArrayDeque<TimedValue>()
    private var lastHudBlockTs = jsNow()
    private var blockMeshDrainMsSum = 0.0
    private var blockGpuUploadMsSum = 0.0
    private var blockFacesProcessedSum = 0
    private var blockTickMaxMs = 0.0
    private var blockPhysicsMsSum = 0.0
    private var blockInteractionMsSum = 0.0
    private var blockAuxMsSum = 0.0
    private val otherTickMsSamples = ArrayDeque<TimedValue>()
    private var blockOtherTickMsSum = 0.0
    private val frameSpikeBuffer = ArrayDeque<FrameSnapshot>()

    private fun ArrayDeque<FrameSnapshot>.pushCapped(snapshot: FrameSnapshot) {
        addLast(snapshot)
        while (size > SPIKE_RING_SIZE) removeFirst()
    }

    // Flushes the last SPIKE_LOG_ENTRIES ring-buffer entries to the browser console — only
    // called when a HUD tick-block's worst single tick exceeds SPIKE_THRESHOLD_MS, so this is
    // rarely on the hot path. Manual string building (not kotlinx.serialization) to avoid
    // reflection overhead on a path that must stay cheap even though it's rare.
    private fun logFrameSpike() {
        val entries = frameSpikeBuffer.takeLast(SPIKE_LOG_ENTRIES)
        val json =
            entries.joinToString(",", prefix = "[", postfix = "]") { s ->
                "{\"ts\":${s.ts},\"blockMs\":${s.blockMs},\"meshDrainMs\":${s.meshDrainMs}," +
                    "\"gpuUploadMs\":${s.gpuUploadMs},\"facesProcessed\":${s.facesProcessed}," +
                    "\"bytesIn\":${s.bytesIn},\"physicsMs\":${s.physicsMs}," +
                    "\"interactionMs\":${s.interactionMs},\"auxMs\":${s.auxMs}," +
                    "\"wsDecodeMs\":${s.wsDecodeMs},\"renderMs\":${s.renderMs}," +
                    "\"renderFrames\":${s.renderFrames},\"renderMsMax\":${s.renderMsMax}," +
                    "\"totalMeshes\":${s.totalMeshes},\"activeMeshes\":${s.activeMeshes}," +
                    "\"otherTickMs\":${s.otherTickMs}}"
            }
        jsLogSpike(json)
    }

    fun setReconcileTolerances(xz: Double, y: Double) {
        reconcileToleranceXz = xz
        reconcileToleranceY = y
    }

    private fun ArrayDeque<TimedValue>.addCapped(now: Double, value: Double) {
        addLast(TimedValue(now, value))
        while (isNotEmpty() && now - first().ts > STATS_WINDOW_MS) removeFirst()
    }

    private fun ArrayDeque<Double>.addTimestamp(now: Double) {
        addLast(now)
        while (isNotEmpty() && now - first() > STATS_WINDOW_MS) removeFirst()
    }

    private fun ArrayDeque<TimedValue>.tickJitter(): Double {
        if (size < 2) return 0.0
        val mean = sumOf { it.value } / size
        return kotlin.math.sqrt(sumOf { (it.value - mean) * (it.value - mean) } / size)
    }

    private fun Double.r3(): String {
        val v = (kotlin.math.round(this * 1000) / 1000.0).toString()
        val dot = v.indexOf('.')
        return if (dot < 0) "$v.000" else v.padEnd(dot + 4, '0').take(dot + 4)
    }

    private fun reconcileStats(distances: ArrayDeque<TimedValue>, total: Int): String {
        val count = distances.size
        val pct = if (total > 0) count * 100 / total else 0
        if (distances.isEmpty()) return "$count/$total ($pct%)"
        var sum = 0.0
        for (d in distances) sum += d.value
        val avg = sum / distances.size
        var sumSq = 0.0
        for (d in distances) sumSq += (d.value - avg) * (d.value - avg)
        val std = kotlin.math.sqrt(sumSq / distances.size)
        return "$count/$total ($pct%) avg=${avg.r3()} ±${std.r3()}"
    }

    /**
     * Height of the camera above the feet. Uses the skin's eye anchor
     * (`resources/models/<skin>/<skin>.yaml`) when the skin declares one — the camera then sits at
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
        jsSetFreeCursor(isOrbitCursor)
    }

    private fun nextEnabledViewMode(): ViewMode {
        var m = viewMode
        repeat(ViewMode.entries.size) {
            m = m.next()
            if (m.name !in disabledViewModes) return m
        }
        return ViewMode.FIRST_PERSON
    }

    private fun switchViewMode(target: ViewMode) {
        viewMode = target
        jsSetFreeCursor(isOrbitCursor)
        jsShowNotification("View: ${target.label}")
        outMessages.trySend(ClientMessage.ViewModeUpdate(target.name))
    }

    private fun applyViewModeCommand(arg: String) {
        val target = ViewMode.entries.firstOrNull { it.name.equals(arg, ignoreCase = true) }
        if (target == null) {
            jsShowNotification(
                "Usage: /view_mode <" + ViewMode.entries.joinToString("|") { it.name } + ">")
            return
        }
        switchViewMode(target)
    }

    private data class MoveBasis(
        val fwdX: Float,
        val fwdZ: Float,
        val strafeLeft: Boolean,
        val strafeRight: Boolean,
    )

    /**
     * Movement basis for the current view mode. In THIRD_PERSON_ORBIT "forward" follows the
     * tank-style [playerYaw] and strafing is on the turn keys (rotate_left/right = Ctrl+Arrow);
     * every other mode keeps camera-relative motion with strafing on Arrow L/R.
     */
    private fun moveBasis(): MoveBasis =
        if (isOrbit)
            MoveBasis(
                kotlin.math.sin(playerYaw),
                kotlin.math.cos(playerYaw),
                jsIsActionDown("rotate_left"),
                jsIsActionDown("rotate_right"))
        else
            MoveBasis(
                jsGetCameraForwardX(camera).toFloat(),
                jsGetCameraForwardZ(camera).toFloat(),
                jsIsActionDown("strafe_left"),
                jsIsActionDown("strafe_right"))

    private fun lerpAngle(from: Float, to: Float, t: Float): Float {
        var diff = to - from
        val twoPi = (2.0 * kotlin.math.PI).toFloat()
        while (diff > kotlin.math.PI.toFloat()) diff -= twoPi
        while (diff < -kotlin.math.PI.toFloat()) diff += twoPi
        return from + diff * t
    }

    fun updateFromServer(
        state: PlayerState,
        lastProcessedSeq: Long,
        onChunkChanged: (Int, Int) -> Unit
    ) {
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
            localRightHandAttached = null
            localLeftHandAttached = null
        }
        if (state.armors != localArmors) {
            localArmors = state.armors
            localArmors.forEach { jsInitArmorModel(it) }
        }
        if (state.rightHandItem != localRightHandItem) {
            localRightHandItem = state.rightHandItem
            localRightHandItem?.let { jsInitWeaponModel(it) }
        }
        if (state.leftHandItem != localLeftHandItem) {
            localLeftHandItem = state.leftHandItem
            localLeftHandItem?.let { jsInitWeaponModel(it) }
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
            reconcileTargetX = serverX
            reconcileTargetZ = serverZ
            sentIntentHistory.clear()
            jsSetCameraRotationY(camera, state.orientation.yaw.toDouble())
            jsSetCameraRotationX(camera, state.orientation.pitch.toDouble())
            playerYaw = state.orientation.yaw
        } else {
            totalServerUpdates++
            serverUpdateTimestamps.addTimestamp(jsNow())
            // Diff serverX/Z against what the client predicted at the same instant the
            // now-confirmed intent was sent (not the current, further-along prediction) — the
            // resulting error reflects genuine prediction divergence, not network latency, so
            // it can be applied as a correction to the CURRENT prediction without lagging.
            val snapshot = sentIntentHistory.lastOrNull { it.seq <= lastProcessedSeq }
            sentIntentHistory.removeAll { it.seq <= lastProcessedSeq }
            if (snapshot != null) {
                reconcileTargetX = predX + (serverX - snapshot.predX)
                reconcileTargetZ = predZ + (serverZ - snapshot.predZ)
            } else {
                reconcileTargetX = serverX
                reconcileTargetZ = serverZ
            }
            val diffY = serverY - predY
            val absY = kotlin.math.abs(diffY)
            when {
                absY > 1.0 -> {
                    predY = serverY
                    predVy = 0.0
                }
                absY > reconcileToleranceY -> {
                    predY += diffY * 0.2
                    yDistances.addCapped(jsNow(), absY)
                }
            }
            // In orbit mode the heading is client-owned via the turn keys; bleed off drift by
            // nudging toward the server value. Other modes have no local heading state to keep.
            if (isOrbit) playerYaw = lerpAngle(playerYaw, state.orientation.yaw, 0.1f)
            else playerYaw = state.orientation.yaw
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

    // Phase D: continuous predicted-trajectory preview while a siege weapon is targeted, before
    // firing — recomputed from live state every frame (rather than only on pitch/power/rotation
    // change events) since SiegeWeaponUpdate/PlaceableUpdate already keep the underlying managers
    // current; this stays in sync for free.
    private fun updateSiegeTrajectoryPreview() {
        val targetId = currentCombatTargetId
        val muzzleAndVelocity =
            targetId?.takeIf(isPlaceableTarget)?.let(siegeWeaponMuzzleAndVelocityOf)
        if (muzzleAndVelocity == null) {
            jsHideTrajectoryPreview()
            return
        }
        val (muzzle, velocity) = muzzleAndVelocity
        jsShowTrajectoryPreview(
            scene,
            muzzle.x.toDouble(),
            muzzle.y.toDouble(),
            muzzle.z.toDouble(),
            velocity.x.toDouble(),
            velocity.y.toDouble(),
            velocity.z.toDouble(),
            CLIENT_GRAVITY)
    }

    fun tick() {
        if (totalClientTicks == 0) {
            jsConsoleLog("[debug] build $BUILD_TIMESTAMP (wasm)")
            jsSetWasmBuildTimestamp(BUILD_TIMESTAMP)
        }
        totalClientTicks++
        val nowMs = jsNow()
        clientTickTimestamps.addTimestamp(nowMs)
        val actualDt =
            if (lastTickMs == 0.0) PRED_DT
            else ((nowMs - lastTickMs) / 1000.0).coerceIn(0.008, 0.05)
        if (lastTickMs != 0.0) {
            val tickIntervalMs = nowMs - lastTickMs
            tickIntervals.addCapped(nowMs, tickIntervalMs)
            blockTickMaxMs = maxOf(blockTickMaxMs, tickIntervalMs)
        }
        lastTickMs = nowMs
        val consoleInput = jsConsumeConsoleInput()
        if (consoleInput.isNotEmpty()) {
            val trimmed = consoleInput.trim()
            when {
                trimmed == "/keyreload" -> {
                    jsLoadBindings(serverHost(), serverPort(), playerName())
                    jsShowNotification("Keybindings reloaded")
                }
                trimmed == "/disconnect" -> disconnectRequested = true
                trimmed == "/view_mode" || trimmed.startsWith("/view_mode ") ->
                    applyViewModeCommand(trimmed.removePrefix("/view_mode").trim())
                trimmed.startsWith("/") ->
                    outMessages.trySend(ClientMessage.Command(enrichCommand(consoleInput)))
                else ->
                    outMessages.trySend(ClientMessage.ChatSend(jsGetActiveChannel(), consoleInput))
            }
        }
        if (jsIsConsoleInputFocused()) return

        val physicsT0 = jsNow()
        val basis = moveBasis()
        val fwdX = basis.fwdX
        val fwdZ = basis.fwdZ
        val rightX = fwdZ
        val rightZ = -fwdX

        val yawStep = (turnSpeedHorizontal * actualDt).toFloat()
        val pitchStep = (turnSpeedVertical * actualDt).toFloat()
        if (isOrbit) {
            if (jsIsActionDown("strafe_left")) playerYaw -= yawStep
            if (jsIsActionDown("strafe_right")) playerYaw += yawStep
            if (jsIsActionDown("rotate_up")) jsRotateCameraPitch(camera, -pitchStep)
            if (jsIsActionDown("rotate_down")) jsRotateCameraPitch(camera, pitchStep)
        } else {
            if (jsIsActionDown("rotate_left")) jsRotateCameraYaw(camera, -yawStep)
            if (jsIsActionDown("rotate_right")) jsRotateCameraYaw(camera, yawStep)
        }

        if (jsIsActionDown("backward")) autoAdvance = false

        val animClip: String
        if (isMounted) {
            // Riding: translation is entirely server-driven (VehicleManager.tick() writes
            // the rider's PlayerState.pos every tick) — snap straight to the vehicle's
            // interpolated position instead of running WASD prediction/reconciliation, which
            // would otherwise fight the server's authoritative pos with visible lag/jitter.
            mountedVehicleId?.let(vehiclePositionOf)?.let { vpos ->
                predX = vpos.x.toDouble()
                predY = vpos.y.toDouble()
                predZ = vpos.z.toDouble()
            }
            predVy = 0.0
            animClip = "sitting"
        } else {
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
            if (basis.strafeRight) {
                dx += rightX
                dz += rightZ
            }
            if (basis.strafeLeft) {
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

            // Priority: flying > crawling > sneaking > backward > forward > strafe > idle.
            animClip =
                when {
                    localFlying -> "jump_idle"
                    stance == PlayerStance.CRAWLING -> "crawling"
                    stance == PlayerStance.SNEAKING -> "sneaking"
                    !isMovingXZ -> "idle"
                    jsIsActionDown("backward") -> "walking_backward"
                    jsIsActionDown("forward") || autoAdvance -> "walking_forward"
                    basis.strafeLeft -> "strafe_left"
                    basis.strafeRight -> "strafe_right"
                    else -> "walking_forward"
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

            val diffX = reconcileTargetX - predX
            val diffZ = reconcileTargetZ - predZ
            val distXZ = kotlin.math.sqrt(diffX * diffX + diffZ * diffZ)
            val speedMultClamped = localSpeedMult.coerceAtLeast(1f).toDouble()
            // Flying has no collision-driven divergence and covers ground fast, so the same
            // tolerance as ground movement fires the soft-correction every frame during a long
            // straight flight, reading as a repeated speed drag — widen it while flying.
            val movingToleranceXz =
                reconcileToleranceXz * speedMultClamped * (if (localFlying) 3.0 else 1.0)
            // Scales with speed like movingToleranceXz above — a fixed absolute distance here
            // would sit only ~2x movingToleranceXz at high speed multipliers, so ordinary
            // prediction jitter would repeatedly cross it and teleport the player, reading as
            // speed pulsing.
            val hardSnapThresholdXz = HARD_SNAP_DISTANCE_XZ * speedMultClamped
            when {
                distXZ > SNAP_THRESHOLD && !isMovingXZ -> {
                    predX = reconcileTargetX
                    predZ = reconcileTargetZ
                }
                distXZ > hardSnapThresholdXz -> {
                    predX = reconcileTargetX
                    predZ = reconcileTargetZ
                }
                !isMovingXZ && distXZ > reconcileToleranceXz -> {
                    predX += diffX * 0.3
                    predZ += diffZ * 0.3
                    xzDistances.addCapped(jsNow(), distXZ)
                }
                isMovingXZ && distXZ > movingToleranceXz -> {
                    predX += diffX * 0.15
                    predZ += diffZ * 0.15
                    xzDistances.addCapped(jsNow(), distXZ)
                }
            }
        }

        blockPhysicsMsSum += jsNow() - physicsT0
        val interactionT0 = jsNow()
        val events = jsConsumeEvents()
        repeat(jsEventsLength(events)) { i ->
            val event = jsEventsGet(events, i)
            when {
                event == "view_toggle" -> switchViewMode(nextEnabledViewMode())
                event == "inventory" -> jsToggleHotbar()
                event == "screenshot" -> jsTakeScreenshot(scene, camera, playerId())
                event == "undo" -> outMessages.trySend(ClientMessage.Command("/undo 1"))
                event == "fly_toggle" -> pendingFlyToggle = true
                event == "block_interact" -> pendingBlockInteract = true
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
                event == "npc_interact" -> {
                    val targetId = currentCombatTargetId ?: return@repeat
                    outMessages.trySend(
                        if (isVehicleTarget(targetId)) ClientMessage.VehicleInteract(targetId)
                        else if (isPlaceableTarget(targetId))
                            ClientMessage.PlaceableInteract(targetId)
                        else ClientMessage.NpcInteract(targetId))
                }
                event == "vehicle_mount" -> outMessages.trySend(ClientMessage.Command("/mount"))
                event == "siege_weapon_rotate" -> {
                    val targetId = currentCombatTargetId ?: return@repeat
                    if (isPlaceableTarget(targetId))
                        outMessages.trySend(ClientMessage.PlaceableRotate(targetId))
                }
                event == "siege_weapon_pitch" -> {
                    val targetId = currentCombatTargetId ?: return@repeat
                    if (isPlaceableTarget(targetId))
                        outMessages.trySend(ClientMessage.SiegeWeaponNudgePitch(targetId))
                }
                event == "siege_weapon_power" -> {
                    val targetId = currentCombatTargetId ?: return@repeat
                    if (isPlaceableTarget(targetId))
                        outMessages.trySend(ClientMessage.SiegeWeaponNudgePower(targetId))
                }
                event == "siege_weapon_fire" -> {
                    val targetId = currentCombatTargetId ?: return@repeat
                    if (isPlaceableTarget(targetId))
                        outMessages.trySend(ClientMessage.SiegeWeaponFire(targetId))
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
                event.startsWith("mail_send:") ->
                    runCatching {
                        outMessages.trySend(
                            Json.decodeFromString<ClientMessage.SendMail>(
                                event.removePrefix("mail_send:")))
                    }
                event.startsWith("mail_seen:") ->
                    outMessages.trySend(
                        ClientMessage.MarkMailSeen(event.removePrefix("mail_seen:")))
                event.startsWith("mail_delete:") ->
                    outMessages.trySend(
                        ClientMessage.DeleteMail(event.removePrefix("mail_delete:")))
                event.startsWith("mail_claim:") ->
                    outMessages.trySend(
                        ClientMessage.ClaimMailAttachments(event.removePrefix("mail_claim:")))
                event.startsWith("auction_create:") ->
                    runCatching {
                            outMessages.trySend(
                                Json.decodeFromString<ClientMessage.AuctionCreateListing>(
                                    event.removePrefix("auction_create:")))
                        }
                        .onFailure { jsError("auction_create decode failed: $it") }
                event.startsWith("auction_bid:") ->
                    runCatching {
                            outMessages.trySend(
                                Json.decodeFromString<ClientMessage.AuctionPlaceBid>(
                                    event.removePrefix("auction_bid:")))
                        }
                        .onFailure { jsError("auction_bid decode failed: $it") }
                event.startsWith("auction_buynow:") ->
                    outMessages.trySend(
                        ClientMessage.AuctionBuyNow(event.removePrefix("auction_buynow:")))
                event.startsWith("auction_cancel:") ->
                    outMessages.trySend(
                        ClientMessage.AuctionCancelListing(event.removePrefix("auction_cancel:")))
                event.startsWith("auction_set_filter:") ->
                    runCatching {
                            outMessages.trySend(
                                Json.decodeFromString<ClientMessage.AuctionSetFilter>(
                                    event.removePrefix("auction_set_filter:")))
                        }
                        .onFailure { jsError("auction_set_filter decode failed: $it") }
                event.startsWith("claim_create:") ->
                    runCatching {
                            outMessages.trySend(
                                Json.decodeFromString<ClientMessage.ClaimCreate>(
                                    event.removePrefix("claim_create:")))
                        }
                        .onFailure { jsError("claim_create decode failed: $it") }
                event.startsWith("claim_abandon:") ->
                    outMessages.trySend(
                        ClientMessage.ClaimAbandon(event.removePrefix("claim_abandon:")))
                event.startsWith("claim_set_trusted:") ->
                    runCatching {
                            outMessages.trySend(
                                Json.decodeFromString<ClientMessage.ClaimSetTrusted>(
                                    event.removePrefix("claim_set_trusted:")))
                        }
                        .onFailure { jsError("claim_set_trusted decode failed: $it") }
                event == "group_create" -> outMessages.trySend(ClientMessage.GroupCreate)
                event.startsWith("group_invite:") ->
                    outMessages.trySend(
                        ClientMessage.GroupInvite(event.removePrefix("group_invite:")))
                event.startsWith("group_respond:") -> {
                    val (gid, acc) = event.removePrefix("group_respond:").split("\t")
                    outMessages.trySend(ClientMessage.GroupInviteRespond(gid, acc == "1"))
                }
                event == "group_leave" -> outMessages.trySend(ClientMessage.GroupLeave)
                event.startsWith("group_kick:") ->
                    outMessages.trySend(ClientMessage.GroupKick(event.removePrefix("group_kick:")))
                event.startsWith("group_transfer:") ->
                    outMessages.trySend(
                        ClientMessage.GroupTransfer(event.removePrefix("group_transfer:")))
                event == "group_disband" -> outMessages.trySend(ClientMessage.GroupDisband)
                event.startsWith("guild_create:") -> {
                    val (n, tg) = event.removePrefix("guild_create:").split("\t")
                    outMessages.trySend(ClientMessage.GuildCreate(n, tg))
                }
                event.startsWith("guild_invite:") ->
                    outMessages.trySend(
                        ClientMessage.GuildInvite(event.removePrefix("guild_invite:")))
                event.startsWith("guild_respond:") -> {
                    val (gid, acc) = event.removePrefix("guild_respond:").split("\t")
                    outMessages.trySend(ClientMessage.GuildInviteRespond(gid, acc == "1"))
                }
                event == "guild_leave" -> outMessages.trySend(ClientMessage.GuildLeave)
                event.startsWith("guild_kick:") ->
                    outMessages.trySend(ClientMessage.GuildKick(event.removePrefix("guild_kick:")))
                event.startsWith("guild_motd:") ->
                    outMessages.trySend(
                        ClientMessage.GuildSetMotd(event.removePrefix("guild_motd:")))
                event.startsWith("guild_setrank:") -> {
                    val (pid, rank) = event.removePrefix("guild_setrank:").split("\t")
                    outMessages.trySend(ClientMessage.GuildSetRank(pid, rank))
                }
                event.startsWith("guild_rank_upsert:") ->
                    runCatching {
                            outMessages.trySend(
                                Json.decodeFromString<ClientMessage.GuildRankUpsert>(
                                    event.removePrefix("guild_rank_upsert:")))
                        }
                        .onFailure { jsError("guild_rank_upsert decode failed: $it") }
                event.startsWith("guild_rank_delete:") ->
                    outMessages.trySend(
                        ClientMessage.GuildRankDelete(event.removePrefix("guild_rank_delete:")))
                event.startsWith("guild_transfer:") ->
                    outMessages.trySend(
                        ClientMessage.GuildTransferOwner(event.removePrefix("guild_transfer:")))
                event == "guild_disband" -> outMessages.trySend(ClientMessage.GuildDisband)
                event.startsWith("guild_bank_deposit:") -> {
                    val (item, n) = event.removePrefix("guild_bank_deposit:").split("\t")
                    outMessages.trySend(
                        ClientMessage.GuildBankDeposit(
                            org.micoli.micraft.game.world.ItemType(item), n.toIntOrNull() ?: 0))
                }
                event.startsWith("guild_bank_withdraw:") -> {
                    val (item, n) = event.removePrefix("guild_bank_withdraw:").split("\t")
                    outMessages.trySend(
                        ClientMessage.GuildBankWithdraw(
                            org.micoli.micraft.game.world.ItemType(item), n.toIntOrNull() ?: 0))
                }
                event.startsWith("faction_set:") ->
                    outMessages.trySend(
                        ClientMessage.FactionSetAffiliation(
                            event.removePrefix("faction_set:").ifBlank { null }))
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
                event.startsWith("creative_place:") -> {
                    val parts = event.removePrefix("creative_place:").split(",")
                    val x = parts.getOrNull(0)?.toIntOrNull()
                    val y = parts.getOrNull(1)?.toIntOrNull()
                    val z = parts.getOrNull(2)?.toIntOrNull()
                    val itemId = parts.getOrNull(3)
                    val rotation = parts.getOrNull(4)?.toIntOrNull() ?: 0
                    if (x != null && y != null && z != null && !itemId.isNullOrEmpty()) {
                        outMessages.trySend(
                            ClientMessage.BlockPlace(
                                BlockPos(x, y, z), ItemType(itemId), rotation.toByte()))
                    }
                }
                event.startsWith("creative_focus:") -> {
                    val parts = event.removePrefix("creative_focus:").split(",")
                    val x = parts.getOrNull(0)?.toFloatOrNull()
                    val z = parts.getOrNull(1)?.toFloatOrNull()
                    if (x != null && z != null) {
                        outMessages.trySend(ClientMessage.CreativeCameraFocus(x, z))
                    }
                }
                event.startsWith("creative_break:") -> {
                    val parts = event.removePrefix("creative_break:").split(",")
                    val x = parts.getOrNull(0)?.toIntOrNull()
                    val y = parts.getOrNull(1)?.toIntOrNull()
                    val z = parts.getOrNull(2)?.toIntOrNull()
                    if (x != null && y != null && z != null) {
                        outMessages.trySend(ClientMessage.BlockBreakStart(BlockPos(x, y, z)))
                    }
                }
                event.startsWith("scene_preview_request:") ->
                    outMessages.trySend(
                        ClientMessage.RequestScenePreview(
                            event.removePrefix("scene_preview_request:")))
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

        updateSiegeTrajectoryPreview()

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
            if (localRightHandAttached != localRightHandItem) {
                localRightHandAttached?.let { jsDetachWeapon(model, "RIGHT") }
                localRightHandAttached = null
                localRightHandItem?.let {
                    if (jsIsWeaponModelReady(it)) {
                        jsAttachWeapon(model, it, scene, "RIGHT")
                        localRightHandAttached = it
                    }
                }
            }
            if (localLeftHandAttached != localLeftHandItem) {
                localLeftHandAttached?.let { jsDetachWeapon(model, "LEFT") }
                localLeftHandAttached = null
                localLeftHandItem?.let {
                    if (jsIsWeaponModelReady(it)) {
                        jsAttachWeapon(model, it, scene, "LEFT")
                        localLeftHandAttached = it
                    }
                }
            }
        }

        val yaw = jsGetCameraRotationY(camera)
        val pitch = jsGetCameraRotationX(camera)

        val eyeOffset = cameraEyeOffset()
        if (viewMode.isThirdPerson) {
            // Camera orbits along its own yaw (mouse); the body faces playerYaw in orbit mode,
            // otherwise the camera yaw is the heading.
            val bodyYaw = if (isOrbit) playerYaw.toDouble() else yaw
            val desiredDist = if (isOrbit) jsGetOrbitZoomDist() else 3.0
            val pivotY = predY + eyeOffset + 0.3
            // Cursor-orbit lifts the camera over the player with the pitch (Alt+drag up); the other
            // third-person modes keep a level chase camera. Clamp so it never dives underground.
            val orbitPitch = if (isOrbitCursor) pitch.coerceIn(-0.35, 1.45) else 0.0
            val cp = kotlin.math.cos(orbitPitch)
            val dirX = (-kotlin.math.sin(yaw) * cp).toFloat()
            val dirY = kotlin.math.sin(orbitPitch).toFloat()
            val dirZ = (-kotlin.math.cos(yaw) * cp).toFloat()
            // Pull the camera in when a solid block sits between it and the player: the greedy
            // chunk
            // mesh has no inward-facing polygons, so a camera inside geometry sees through the
            // world.
            val dist =
                cameraObstructionDist(predX, pivotY, predZ, dirX, dirY, dirZ, desiredDist.toFloat())
                    .toDouble()
            val camX = predX + dirX * dist
            val camY = pivotY + dirY * dist
            val camZ = predZ + dirZ * dist
            jsClearCameraInterpolation()
            jsCameraSetPosition(camera, camX, camY, camZ)
            localPlayerModel?.let {
                jsSetPlayerTransform(
                    it, predX, predY, predZ, bodyYaw.toFloat(), pitch.toFloat(), animClip)
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
                        it, predX, predY, predZ, yaw.toFloat(), pitch.toFloat(), animClip)
                    jsSetPlayerFirstPerson(it, localSkin, true)
                }
                jsSetPlayerVisible(it, showBody)
            }
        }
        prevPredX = predX
        prevPredY = predY
        prevPredZ = predZ
        prevEyeOffset = eyeOffset

        // In cursor-orbit the ray starts at the camera, so extend the budget by the chase distance
        // to keep the same effective reach around the player.
        val rayReach =
            if (isOrbitCursor) maxInteractionDistance + jsGetOrbitZoomDist().toFloat()
            else maxInteractionDistance
        val rayResult = raycastBlock(rayReach)
        val target = rayResult?.target

        if (pendingBlockInteract) {
            pendingBlockInteract = false
            target?.let { outMessages.trySend(ClientMessage.BlockInteract(it)) }
        }

        if (viewMode.isThirdPerson) {
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

        val mouseDownNow = jsIsMouseDown()
        if (mouseDownNow && !wasMouseDown) breakArmed = true
        wasMouseDown = mouseDownNow
        // Plain left-click does nothing in mouse-look orbit — only Ctrl+click (block_interact)
        // acts.
        // Cursor-orbit is exempt: there the click aims at the cursor, so it breaks/places normally.
        val isBreaking = jsIsBreaking() && (!isOrbit || isOrbitCursor)
        val selectedSlotContent =
            if (selectedSlot > 0) shortcutBarPages[currentPage][selectedSlot] else null
        val selectedItem = (selectedSlotContent as? ShortcutSlot.Item)?.itemType
        val isPlaceMode = selectedItem != null && selectedItem.buildable
        val placeableEntityType = selectedItem?.let { ItemRegistry.get(it).spawnsEntity }

        if (isPlaceMode &&
            placeableEntityType != null &&
            PlaceableRegistry.get(placeableEntityType) != null) {
            if (ghostAdjacentPos != null) {
                ghostAdjacentPos = null
                jsHideBlockPreview()
            }
            val adjacent = rayResult?.adjacent
            if (adjacent != ghostPlaceablePos || placeableEntityType.id != ghostPlaceableType) {
                ghostPlaceablePos = adjacent
                ghostPlaceableType = placeableEntityType.id
                if (adjacent != null) {
                    jsShowPlaceablePreview(
                        scene, placeableEntityType.id, adjacent.x, adjacent.y, adjacent.z)
                } else {
                    jsHidePlaceablePreview()
                }
            }
            if (isBreaking && adjacent != null && !hasPlacedThisClick) {
                hasPlacedThisClick = true
                breakTarget = adjacent
                outMessages.trySend(
                    ClientMessage.BlockPlace(
                        adjacent, selectedItem, placementRotation.toByte(), 0, 0))
            } else if (!isBreaking) {
                breakTarget = null
                hasPlacedThisClick = false
            }
        } else if (isPlaceMode) {
            if (ghostPlaceablePos != null) {
                ghostPlaceablePos = null
                ghostPlaceableType = null
                jsHidePlaceablePreview()
            }
            val rawAdjacent = rayResult?.adjacent
            val isFractionalItem =
                selectedItem.placesBlock?.let { BlockRegistry.get(it).brickSize[1] < 2.0f } ?: false
            // Redirect ghost for fractional blocks only on lateral clicks (side of an existing
            // fractional entity → its own cell, so a second XZ slot or Y stack level can be added
            // in the SAME voxel instead of pushing into the empty neighbor cell). Above-stud
            // clicks (rawAdjacent.y > target.y) keep ghost at y+1 so it appears above the existing
            // plates; the server handles the actual sub-voxel stacking.
            val adjacent =
                if (isFractionalItem &&
                    rawAdjacent != null &&
                    target != null &&
                    rawAdjacent.y == target.y &&
                    chunkManager.getFractionalInfoAt(target.x, target.y, target.z) != null)
                    target
                else rawAdjacent

            val ghostColorIdx =
                if (selectedItem.placesBlock?.let { BlockRegistry.get(it).plainColorable } == true)
                    PlainColorRegistry.indexOf(ItemRegistry.get(selectedItem).plainColor)
                else 0

            // Compute XZ sub-voxel slot from hit position within adjacent voxel. brickSize is in
            // half-voxel units (2f = 1 full voxel) — divide by 2 to get a 0..1 voxel fraction.
            val blockDef = selectedItem.placesBlock?.let { BlockRegistry.get(it) }
            val brickSizeX = blockDef?.brickSize?.getOrElse(0) { 2f } ?: 2f
            val brickSizeZ = blockDef?.brickSize?.getOrElse(2) { 2f } ?: 2f
            val effectiveFracX = (if (placementRotation % 2 == 0) brickSizeX else brickSizeZ) / 2f
            val effectiveFracZ = (if (placementRotation % 2 == 0) brickSizeZ else brickSizeX) / 2f
            // Fine-snap: even a full-voxel item (studStep would otherwise be 0, collapsing onto
            // the coarse grid) must snap onto the 1/4-voxel grid when it lands next to an
            // already-offset lego neighbor — mirrors BlockPlacer.placeAt's neighborForcesFineSnap.
            val neighborForcesFineSnap =
                effectiveFracX >= 1.0f &&
                    effectiveFracZ >= 1.0f &&
                    adjacent != null &&
                    chunkManager.hasMisalignedNeighborAt(adjacent.x, adjacent.y, adjacent.z)
            val ghostXOffset: Int
            val ghostZOffset: Int
            val studStepX =
                when {
                    effectiveFracX < 1.0f -> effectiveFracX
                    effectiveFracX > 1.0f -> 0.5f // multi-voxel LEGO: 1-stud (0.5-block) precision
                    neighborForcesFineSnap -> 0.25f // forced 1/4-voxel snap next to offset neighbor
                    else -> 0f
                }
            val studStepZ =
                when {
                    effectiveFracZ < 1.0f -> effectiveFracZ
                    effectiveFracZ > 1.0f -> 0.5f
                    neighborForcesFineSnap -> 0.25f
                    else -> 0f
                }
            if (adjacent != null && (studStepX > 0f || studStepZ > 0f)) {
                val hitX = rayResult?.hitX ?: 0f
                val hitZ = rayResult?.hitZ ?: 0f
                val fracX = (hitX - adjacent.x).coerceIn(0f, 0.9999f)
                val fracZ = (hitZ - adjacent.z).coerceIn(0f, 0.9999f)
                val slotsX =
                    if (studStepX > 0f) kotlin.math.floor(1f / studStepX).toInt().coerceAtLeast(1)
                    else 1
                val slotsZ =
                    if (studStepZ > 0f) kotlin.math.floor(1f / studStepZ).toInt().coerceAtLeast(1)
                    else 1
                // A lateral face's normal axis carries no positional info in the raycast hit
                // point (it's pinned to the face boundary, see raycastBlock's bx!=prevBx /
                // bz!=prevBz cases) — hit-derived frac on that axis is meaningless. Snap it to an
                // already-placed neighbor's slot in the target cell instead of the degenerate
                // face-boundary value.
                val xAxisDegenerate =
                    target != null && rawAdjacent != null && target.x != rawAdjacent.x
                val zAxisDegenerate =
                    target != null && rawAdjacent != null && target.z != rawAdjacent.z
                val usedSlots =
                    if (xAxisDegenerate || zAxisDegenerate)
                        chunkManager.getUsedXZOffsetsAt(adjacent.x, adjacent.y, adjacent.z)
                    else emptySet()
                ghostXOffset =
                    if (xAxisDegenerate) usedSlots.firstOrNull()?.first ?: 0
                    else if (studStepX > 0f)
                        kotlin.math.floor(fracX / studStepX).toInt().coerceIn(0, slotsX - 1)
                    else 0
                ghostZOffset =
                    if (zAxisDegenerate) usedSlots.firstOrNull()?.second ?: 0
                    else if (studStepZ > 0f)
                        kotlin.math.floor(fracZ / studStepZ).toInt().coerceIn(0, slotsZ - 1)
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
                    if (blockDef?.brickSize?.let { bs -> bs[0] != 2f || bs[2] != 2f } == true) {
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
            if (ghostPlaceablePos != null) {
                ghostPlaceablePos = null
                ghostPlaceableType = null
                jsHidePlaceablePreview()
            }
            if (isBreaking && target != null) {
                if (target != breakTarget && (continuousBreak || breakArmed)) {
                    breakArmed = false
                    breakTarget = target
                    val targetType = chunkManager.getBlockAtWorld(target.x, target.y, target.z)
                    val targetDef = BlockRegistry.get(targetType)
                    val targetOrd = BlockRegistry.wireIndex(targetType)
                    val targetRotation =
                        BlockState.rotation(
                            chunkManager.getStateAtWorld(target.x, target.y, target.z))
                    val breakStudStepX =
                        (targetDef.brickSize.getOrElse(0) { 2f } / 2f).let {
                            if (it < 1.0f) it else 0f
                        }
                    val breakStudStepZ =
                        (targetDef.brickSize.getOrElse(2) { 2f } / 2f).let {
                            if (it < 1.0f) it else 0f
                        }
                    val breakXOffset: Int
                    val breakZOffset: Int
                    if (breakStudStepX > 0f || breakStudStepZ > 0f) {
                        val hitX = rayResult.hitX
                        val hitZ = rayResult.hitZ
                        val fracX = (hitX - target.x).coerceIn(0f, 0.9999f)
                        val fracZ = (hitZ - target.z).coerceIn(0f, 0.9999f)
                        val slotsBreakX =
                            if (breakStudStepX > 0f)
                                kotlin.math.floor(1f / breakStudStepX).toInt().coerceAtLeast(1)
                            else 1
                        val slotsBreakZ =
                            if (breakStudStepZ > 0f)
                                kotlin.math.floor(1f / breakStudStepZ).toInt().coerceAtLeast(1)
                            else 1
                        // Same degenerate-axis issue as the placement ghost (see above): the
                        // clicked face's normal axis carries no positional info in the hit point.
                        val breakXDegenerate = rayResult.adjacent.x != target.x
                        val breakZDegenerate = rayResult.adjacent.z != target.z
                        val breakUsedSlots =
                            if (breakXDegenerate || breakZDegenerate)
                                chunkManager.getUsedXZOffsetsAt(target.x, target.y, target.z)
                            else emptySet()
                        breakXOffset =
                            if (breakXDegenerate) breakUsedSlots.firstOrNull()?.first ?: 0
                            else if (breakStudStepX > 0f)
                                kotlin.math
                                    .floor(fracX / breakStudStepX)
                                    .toInt()
                                    .coerceIn(0, slotsBreakX - 1)
                            else 0
                        breakZOffset =
                            if (breakZDegenerate) breakUsedSlots.firstOrNull()?.second ?: 0
                            else if (breakStudStepZ > 0f)
                                kotlin.math
                                    .floor(fracZ / breakStudStepZ)
                                    .toInt()
                                    .coerceIn(0, slotsBreakZ - 1)
                            else 0
                    } else {
                        breakXOffset = 0
                        breakZOffset = 0
                    }
                    // Footprint-aware outline (mirrors showTargetOutline) so breaking a multi-cell
                    // or offset lego entity highlights its real extent, not just the pointed cell.
                    jsShowBreakOverlay(
                        scene,
                        target.x,
                        target.y,
                        target.z,
                        1.0,
                        targetOrd,
                        targetRotation,
                        breakXOffset,
                        breakZOffset)
                    outMessages.trySend(
                        ClientMessage.BlockBreakStart(
                            target, breakXOffset.toByte(), breakZOffset.toByte()))
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

        blockInteractionMsSum += jsNow() - interactionT0
        fpsFrameCount++
        val now = jsNow()
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1000.0) {
            val sec = elapsed / 1000.0
            currentFps = (fpsFrameCount / sec).toInt()
            currentKbIn = networkStats.bytesIn / 1024.0 / sec
            currentKbOut = networkStats.bytesOut / 1024.0 / sec
            fpsSamples.addCapped(now, currentFps.toDouble())
            if (jsIsPerfInstrumentationEnabled()) {
                val wsDecodeMsAvg =
                    if (networkStats.chunkDecodeCount > 0)
                        networkStats.chunkDecodeMsAccum / networkStats.chunkDecodeCount
                    else 0.0
                wsDecodeMsSamples.addCapped(now, wsDecodeMsAvg)
            }
            networkStats.chunkDecodeMsAccum = 0.0
            networkStats.chunkDecodeCount = 0
            fpsFrameCount = 0
            networkStats.bytesIn = 0
            networkStats.bytesOut = 0
            fpsWindowStart = now
        }

        // Per-tick mesh/GPU timing sampled straight from ChunkManager's last drainPendingChunks()
        // call (see ChunkManager.kt) — same rolling-window pattern as
        // tickIntervals/jitterSnapshots. Gated by the perf-instrumentation master switch (see
        // jsIsPerfInstrumentationEnabled) — off by default, enable via
        // `window.mcState.perfInstrumentationEnabled = true` in the console.
        if (jsIsPerfInstrumentationEnabled()) {
            val meshDrainMsThisTick = chunkManager.lastFaceScanMs + chunkManager.lastFaceProcessMs
            val gpuUploadMsThisTick = chunkManager.lastGpuUploadMs
            meshDrainMsSamples.addCapped(now, meshDrainMsThisTick)
            gpuUploadMsSamples.addCapped(now, gpuUploadMsThisTick)
            blockMeshDrainMsSum += meshDrainMsThisTick
            blockGpuUploadMsSum += gpuUploadMsThisTick
            blockFacesProcessedSum += chunkManager.lastFacesProcessedThisDrain
            otherTickMsSamples.addCapped(now, otherTickMs)
            blockOtherTickMsSum += otherTickMs
        }

        val auxT0 = jsNow()
        val normalizedTime =
            (currentGameTicks % TICKS_PER_DAY_CLIENT).toDouble() / TICKS_PER_DAY_CLIENT
        jsUpdateSkyTime(scene, normalizedTime)
        jsUpdateWeather(scene, predX, predY, predZ)
        hudWeather = jsGetCurrentWeather()
        updateCaveLighting()

        jsDrawMinimap(predX, predZ, yaw)
        blockAuxMsSum += jsNow() - auxT0

        val toDeg = 180.0 / kotlin.math.PI
        val tickJitter = tickIntervals.tickJitter()
        jitterSnapshots.addCapped(now, tickJitter)
        hudTickCounter++
        if (hudTickCounter >= 10) {
            hudTickCounter = 0
            val targetBlockName =
                target?.let { chunkManager.getBlockAtWorld(it.x, it.y, it.z).id } ?: ""
            val gameTimeDisplay = ticksToHHMM(currentGameTicks)
            val tickAvg =
                (tickIntervals.sumOf { it.value } / tickIntervals.size).takeIf { it.isFinite() }
                    ?: 0.0
            val tickMin = if (tickIntervals.isEmpty()) 0.0 else tickIntervals.minOf { it.value }
            val tickMax = if (tickIntervals.isEmpty()) 0.0 else tickIntervals.maxOf { it.value }
            val jitterMin =
                if (jitterSnapshots.isEmpty()) 0.0 else jitterSnapshots.minOf { it.value }
            val jitterMax =
                if (jitterSnapshots.isEmpty()) 0.0 else jitterSnapshots.maxOf { it.value }
            val fpsMin = if (fpsSamples.isEmpty()) 0 else fpsSamples.minOf { it.value }.toInt()
            val fpsMax = if (fpsSamples.isEmpty()) 0 else fpsSamples.maxOf { it.value }.toInt()
            val reconcileXz = reconcileStats(xzDistances, clientTickTimestamps.size)
            val reconcileY = reconcileStats(yDistances, serverUpdateTimestamps.size)
            // Everything below (rolling-window avg/min/max, the spike ring buffer, mesh-count
            // queries) is gated by the perf-instrumentation master switch — off by default, see
            // jsIsPerfInstrumentationEnabled. When disabled, the HUD's extra timing fields just
            // read 0.0 rather than the real (unsampled) values.
            val meshDrainMsAvg: Double
            val meshDrainMsMin: Double
            val meshDrainMsMax: Double
            val gpuUploadMsAvg: Double
            val gpuUploadMsMin: Double
            val gpuUploadMsMax: Double
            val wsDecodeMsAvg: Double
            if (jsIsPerfInstrumentationEnabled()) {
                meshDrainMsAvg =
                    (meshDrainMsSamples.sumOf { it.value } / meshDrainMsSamples.size).takeIf {
                        it.isFinite()
                    } ?: 0.0
                meshDrainMsMin =
                    if (meshDrainMsSamples.isEmpty()) 0.0 else meshDrainMsSamples.minOf { it.value }
                meshDrainMsMax =
                    if (meshDrainMsSamples.isEmpty()) 0.0 else meshDrainMsSamples.maxOf { it.value }
                gpuUploadMsAvg =
                    (gpuUploadMsSamples.sumOf { it.value } / gpuUploadMsSamples.size).takeIf {
                        it.isFinite()
                    } ?: 0.0
                gpuUploadMsMin =
                    if (gpuUploadMsSamples.isEmpty()) 0.0 else gpuUploadMsSamples.minOf { it.value }
                gpuUploadMsMax =
                    if (gpuUploadMsSamples.isEmpty()) 0.0 else gpuUploadMsSamples.maxOf { it.value }
                wsDecodeMsAvg =
                    if (wsDecodeMsSamples.isEmpty()) 0.0
                    else wsDecodeMsSamples.sumOf { it.value } / wsDecodeMsSamples.size

                val renderMsSum = jsGetRenderMsAccum()
                val renderFrameCount = jsGetRenderFrameCount()
                val renderMsMaxThisBlock = jsGetRenderMsMax()
                jsResetRenderStats()
                frameSpikeBuffer.pushCapped(
                    FrameSnapshot(
                        ts = now,
                        blockMs = now - lastHudBlockTs,
                        meshDrainMs = blockMeshDrainMsSum,
                        gpuUploadMs = blockGpuUploadMsSum,
                        facesProcessed = blockFacesProcessedSum,
                        bytesIn = networkStats.bytesIn,
                        physicsMs = blockPhysicsMsSum,
                        interactionMs = blockInteractionMsSum,
                        auxMs = blockAuxMsSum,
                        wsDecodeMs = wsDecodeMsAvg,
                        renderMs = renderMsSum,
                        renderFrames = renderFrameCount,
                        renderMsMax = renderMsMaxThisBlock,
                        totalMeshes = jsGetTotalMeshCount(scene),
                        activeMeshes = jsGetActiveMeshCount(scene),
                        otherTickMs = blockOtherTickMsSum,
                    ))
                if (blockTickMaxMs > jsGetSpikeThresholdMs(DEFAULT_SPIKE_THRESHOLD_MS))
                    logFrameSpike()
            } else {
                meshDrainMsAvg = 0.0
                meshDrainMsMin = 0.0
                meshDrainMsMax = 0.0
                gpuUploadMsAvg = 0.0
                gpuUploadMsMin = 0.0
                gpuUploadMsMax = 0.0
                wsDecodeMsAvg = 0.0
            }
            lastHudBlockTs = now
            blockMeshDrainMsSum = 0.0
            blockGpuUploadMsSum = 0.0
            blockFacesProcessedSum = 0
            blockTickMaxMs = 0.0
            blockPhysicsMsSum = 0.0
            blockInteractionMsSum = 0.0
            blockAuxMsSum = 0.0
            blockOtherTickMsSum = 0.0

            jsUpdateHUD(
                hudX,
                hudY,
                hudZ,
                yaw * toDeg,
                pitch * toDeg,
                hudStance,
                hudSpeed,
                currentFps,
                fpsMin,
                fpsMax,
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
                chunkManager.fullMeshedChunkCount,
                chunkManager.impostorChunkCount,
                hudWeather,
                hudZoneLevel,
                meshDrainMsAvg,
                meshDrainMsMin,
                meshDrainMsMax,
                gpuUploadMsAvg,
                gpuUploadMsMin,
                gpuUploadMsMax,
                wsDecodeMsAvg,
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
                    fpsMin = fpsMin,
                    fpsMax = fpsMax,
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
                    fullMeshedChunks = chunkManager.fullMeshedChunkCount,
                    impostorMeshedChunks = chunkManager.impostorChunkCount,
                    weather = hudWeather,
                    zoneLevel = hudZoneLevel,
                    meshDrainMsAvg = meshDrainMsAvg,
                    meshDrainMsMin = meshDrainMsMin,
                    meshDrainMsMax = meshDrainMsMax,
                    gpuUploadMsAvg = gpuUploadMsAvg,
                    gpuUploadMsMin = gpuUploadMsMin,
                    gpuUploadMsMax = gpuUploadMsMax,
                    wsDecodeMsAvg = wsDecodeMsAvg,
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

    // Snapshots the current prediction under `seq` right after that intent is actually sent —
    // see the reconciliation lookup in updateFromServer. Capped so a stalled connection can't
    // grow this unboundedly.
    fun recordSentIntent(seq: Long) {
        sentIntentHistory.addLast(SentIntentSnapshot(seq, predX, predZ))
        while (sentIntentHistory.size > 100) sentIntentHistory.removeFirst()
    }

    fun buildMoveIntent(): ClientMessage.MoveIntent {
        val basis = moveBasis()
        val fwdX = basis.fwdX
        val fwdZ = basis.fwdZ
        val rightX = fwdZ
        val rightZ = -fwdX
        val sentYaw = if (isOrbit) playerYaw else jsGetCameraRotationY(camera).toFloat()

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
        if (basis.strafeRight) {
            dx += rightX
            dz += rightZ
        }
        if (basis.strafeLeft) {
            dx -= rightX
            dz -= rightZ
        }

        val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (len > 1f) {
            dx /= len
            dz /= len
        }

        // Mounted: translation is server-driven regardless (see MovementProcessor), zeroing
        // here purely avoids the local prediction jittering against the vehicle-glued position.
        if (isMounted) {
            dx = 0f
            dz = 0f
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
                yaw = sentYaw,
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
                yaw = sentYaw,
                pitch = jsGetCameraRotationX(camera).toFloat(),
                stance = stance,
                jump = !isMounted && jsIsActionDown("ascend"),
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
        reconcileTargetX = 0.0
        reconcileTargetZ = 0.0
        sentIntentHistory.clear()
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

    // Voxel march from the chase-camera pivot toward the desired camera position (unit direction).
    // Returns the distance to the first solid block (minus a small margin), else [maxDist].
    private fun cameraObstructionDist(
        px: Double,
        py: Double,
        pz: Double,
        dx: Float,
        dy: Float,
        dz: Float,
        maxDist: Float,
    ): Float {
        if (dx == 0f && dy == 0f && dz == 0f) return maxDist
        var bx = kotlin.math.floor(px).toInt()
        var by = kotlin.math.floor(py).toInt()
        var bz = kotlin.math.floor(pz).toInt()
        val sx = if (dx > 0) 1 else if (dx < 0) -1 else 0
        val sy = if (dy > 0) 1 else if (dy < 0) -1 else 0
        val sz = if (dz > 0) 1 else if (dz < 0) -1 else 0
        val tDeltaX = if (dx != 0f) kotlin.math.abs(1f / dx) else Float.MAX_VALUE
        val tDeltaY = if (dy != 0f) kotlin.math.abs(1f / dy) else Float.MAX_VALUE
        val tDeltaZ = if (dz != 0f) kotlin.math.abs(1f / dz) else Float.MAX_VALUE
        var tMaxX =
            if (dx > 0) ((bx + 1 - px) / dx).toFloat()
            else if (dx < 0) ((bx - px) / dx).toFloat() else Float.MAX_VALUE
        var tMaxY =
            if (dy > 0) ((by + 1 - py) / dy).toFloat()
            else if (dy < 0) ((by - py) / dy).toFloat() else Float.MAX_VALUE
        var tMaxZ =
            if (dz > 0) ((bz + 1 - pz) / dz).toFloat()
            else if (dz < 0) ((bz - pz) / dz).toFloat() else Float.MAX_VALUE
        while (true) {
            val enterT = minOf(tMaxX, tMaxY, tMaxZ)
            if (enterT >= maxDist) return maxDist
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
            if (chunkManager.getBlockAtWorld(bx, by, bz).isSolid) {
                return (enterT - CAMERA_COLLISION_MARGIN).coerceIn(0f, maxDist)
            }
        }
    }

    private fun raycastBlock(maxDist: Float = 5f): RaycastResult? {
        // THIRD_PERSON_ORBIT_CURSOR aims from the camera along the ray through the mouse cursor;
        // every other mode aims from the player's eye along the camera's forward direction.
        val ox: Double
        val oy: Double
        val oz: Double
        val dx: Float
        val dy: Float
        val dz: Float
        if (isOrbitCursor) {
            ox = jsGetCameraPositionX(camera)
            oy = jsGetCameraPositionY(camera)
            oz = jsGetCameraPositionZ(camera)
            dx = jsGetCursorRayX(camera).toFloat()
            dy = jsGetCursorRayY(camera).toFloat()
            dz = jsGetCursorRayZ(camera).toFloat()
        } else {
            ox = predX
            oy = predY + cameraEyeOffset()
            oz = predZ
            dx = jsGetCameraDir3DX(camera).toFloat()
            dy = jsGetCameraDir3DY(camera).toFloat()
            dz = jsGetCameraDir3DZ(camera).toFloat()
        }

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
        if (parts.size == 2 && parts[0] == "/vehicule:add") {
            val t = hoverTarget ?: return cmd
            return "/vehicule:add ${parts[1]} ${t.x} ${t.y} ${t.z}"
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
