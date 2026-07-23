package org.micoli.micraft.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.TimeSource
import org.micoli.micraft.babylon.*
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage

class NpcManager(private val scene: JsAny, private val localPlayerId: () -> String?) :
    ServerMessageHandler {
    @OptIn(ExperimentalWasmJsInterop::class) private val npcModels = mutableMapOf<String, JsAny>()
    private val npcBuffers = mutableMapOf<String, ArrayDeque<PosSnapshot>>()
    private val npcRenderedYaw = mutableMapOf<String, Float>()
    private val pendingNpcs = mutableListOf<NpcState>()
    private val npcNames = mutableMapOf<String, String>()
    private var highlightedNpcId: String? = null
    private val aggroNpcIds = mutableSetOf<String>()
    private val deadNpcIds = mutableSetOf<String>()

    var playerX = 0.0
    var playerZ = 0.0
    var playerYaw = 0.0

    private val clock = TimeSource.Monotonic
    private val startMark = clock.markNow()
    private var proximityTick = 0

    private fun nowMs(): Long = startMark.elapsedNow().inWholeMilliseconds

    private class PosSnapshot(val pos: Vec3, val vel: Vec3, val yaw: Float, val timeMs: Long)

    private data class InterpResult(val pos: Vec3, val vel: Vec3, val yaw: Float)

    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.NpcSpawned -> handleSpawned(msg.npc)
            is ServerMessage.NpcUpdate -> handleUpdate(msg.npc)
            is ServerMessage.NpcDespawned -> handleDespawned(msg.id)
            is ServerMessage.NpcInteractResult -> jsOpenNpcDialog(msg.payload)
            else -> Unit
        }

    fun handleSpawned(npc: NpcState) {
        npcNames[npc.id] = npc.name
        updateAutocomplete()
        jsSetNpcOnMinimap(npc.id, npc.pos.x, npc.pos.z)
        pushSnapshot(npc)
        if (!jsIsNpcModelsReady()) {
            pendingNpcs.add(npc)
            return
        }
        ensureMesh(npc)
        updateAggroHighlight(npc, localPlayerId())
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun handleUpdate(npc: NpcState) {
        jsSetNpcOnMinimap(npc.id, npc.pos.x, npc.pos.z)
        pushSnapshot(npc)
        if (npc.isDead) {
            if (npc.id !in deadNpcIds) {
                deadNpcIds.add(npc.id)
                aggroNpcIds.remove(npc.id)
                val model = npcModels[npc.id]
                if (model != null) {
                    jsAggroHighlightNpcModel(scene, model, false)
                    jsHighlightNpcModel(scene, model, false)
                    jsSetNpcDead(scene, model)
                }
            }
            return
        }
        updateAggroHighlight(npc, localPlayerId())
        if (!jsIsNpcModelsReady()) {
            pendingNpcs.removeAll { it.id == npc.id }
            pendingNpcs.add(npc)
            return
        }
        ensureMesh(npc)
    }

    fun handleDespawned(id: String) {
        npcNames.remove(id)
        updateAutocomplete()
        jsRemoveNpcFromMinimap(id)
        pendingNpcs.removeAll { it.id == id }
        aggroNpcIds.remove(id)
        deadNpcIds.remove(id)
        npcModels.remove(id)?.let(::jsDisposeNpcModel)
        npcBuffers.remove(id)
        npcRenderedYaw.remove(id)
    }

    fun tick() {
        if (jsIsNpcModelsReady() && pendingNpcs.isNotEmpty()) {
            val batch = pendingNpcs.toList()
            pendingNpcs.clear()
            batch.forEach { ensureMesh(it) }
        }
        if (!jsIsNpcModelsReady()) return
        val renderTime = nowMs() - INTERP_DELAY_MS
        for ((id, buf) in npcBuffers) {
            if (id in deadNpcIds) continue
            val model = npcModels[id] ?: continue
            val (pos, vel, yaw) = interpolate(buf, renderTime)
            val prevYaw = npcRenderedYaw[id] ?: yaw
            val renderedYaw = lerpAngle(prevYaw, yaw, 0.12f)
            npcRenderedYaw[id] = renderedYaw
            val isWalking = abs(vel.x) > 0.01f || abs(vel.z) > 0.01f
            jsSetNpcTransform(
                model,
                pos.x.toDouble(),
                pos.y.toDouble(),
                pos.z.toDouble(),
                renderedYaw,
                isWalking,
            )
        }
        if (++proximityTick >= PROXIMITY_THROTTLE_TICKS) {
            proximityTick = 0
            tickProximity(renderTime)
        }
    }

    private fun tickProximity(renderTime: Long) {
        val entries =
            npcBuffers.entries
                .map { (id, buf) ->
                    val (pos, _, _) = interpolate(buf, renderTime)
                    val dx = pos.x.toDouble() - playerX
                    val dz = pos.z.toDouble() - playerZ
                    val dist = sqrt(dx * dx + dz * dz)
                    val absAngle = atan2(dx, dz)
                    var relAngle = absAngle - playerYaw
                    val twoPi = 2.0 * PI
                    while (relAngle > PI) relAngle -= twoPi
                    while (relAngle < -PI) relAngle += twoPi
                    val aggro = id in aggroNpcIds
                    val name = npcNames[id] ?: ""
                    Triple(
                        dist,
                        id,
                        "{\"id\":\"$id\",\"name\":\"$name\",\"relAngle\":${relAngle.toFloat()},\"dist\":${dist.toFloat()},\"aggro\":$aggro}")
                }
                .sortedBy { it.first }
                .take(3)
                .map { it.third }
        jsUpdateNpcProximity("[" + entries.joinToString(",") + "]")
    }

    fun clear() {
        npcModels.values.forEach(::jsDisposeNpcModel)
        npcModels.clear()
        npcBuffers.clear()
        npcRenderedYaw.clear()
        pendingNpcs.clear()
        npcNames.clear()
        aggroNpcIds.clear()
        deadNpcIds.clear()
        jsSetNpcNames("[]")
        jsUpdateNpcProximity("[]")
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun ensureMesh(npc: NpcState) {
        npcModels.getOrPut(npc.id) { jsCreateNpcModel(scene, npc.type) ?: return }
    }

    private fun pushSnapshot(npc: NpcState) {
        val buf = npcBuffers.getOrPut(npc.id) { ArrayDeque() }
        buf.addLast(PosSnapshot(npc.pos, npc.vel, npc.yaw, nowMs()))
        while (buf.size > MAX_BUFFER) buf.removeFirst()
    }

    private fun interpolate(buf: ArrayDeque<PosSnapshot>, renderTime: Long): InterpResult {
        if (buf.isEmpty()) return InterpResult(Vec3(0f, 0f, 0f), Vec3(0f, 0f, 0f), 0f)
        if (buf.size == 1 || renderTime <= buf.first().timeMs) {
            val s = buf.first()
            return InterpResult(s.pos, s.vel, s.yaw)
        }
        if (renderTime >= buf.last().timeMs) {
            val s = buf.last()
            return InterpResult(s.pos, s.vel, s.yaw)
        }
        val nextIdx = buf.indexOfFirst { it.timeMs >= renderTime }
        val prev = buf[nextIdx - 1]
        val next = buf[nextIdx]
        val t =
            ((renderTime - prev.timeMs).toFloat() / (next.timeMs - prev.timeMs)).coerceIn(0f, 1f)
        val dx = next.pos.x - prev.pos.x
        val dz = next.pos.z - prev.pos.z
        val yaw =
            if (dx * dx + dz * dz > 1e-6f) atan2(dx.toDouble(), dz.toDouble()).toFloat()
            else next.yaw
        return InterpResult(
            pos =
                Vec3(
                    lerp(prev.pos.x, next.pos.x, t),
                    lerp(prev.pos.y, next.pos.y, t),
                    lerp(prev.pos.z, next.pos.z, t),
                ),
            vel = next.vel,
            yaw = yaw,
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lerpAngle(from: Float, to: Float, t: Float): Float {
        var diff = to - from
        val twoPi = (2.0 * PI).toFloat()
        while (diff > PI.toFloat()) diff -= twoPi
        while (diff < -PI.toFloat()) diff += twoPi
        return from + diff * t
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun updateAggroHighlight(npc: NpcState, localPlayerId: String?) {
        val wasAggro = npc.id in aggroNpcIds
        val isAggro = localPlayerId != null && npc.aggroTargetId == localPlayerId
        if (wasAggro == isAggro) return
        val model = npcModels[npc.id]
        if (model != null) jsAggroHighlightNpcModel(scene, model, isAggro)
        if (isAggro) aggroNpcIds.add(npc.id) else aggroNpcIds.remove(npc.id)
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun setHighlightTarget(id: String?) {
        val prevModel = highlightedNpcId?.let { npcModels[it] }
        if (prevModel != null) jsHighlightNpcModel(scene, prevModel, false)
        highlightedNpcId = id
        val nextModel = id?.let { npcModels[it] }
        if (nextModel != null) jsHighlightNpcModel(scene, nextModel, true)
    }

    fun cycleNearestNpc(
        playerX: Double,
        playerY: Double,
        playerZ: Double,
        playerYaw: Double,
        currentId: String?,
    ): String? {
        val maxDist2 = 20.0 * 20.0
        val halfConeCos = cos(PI / 6.0)
        val fwdX = sin(playerYaw)
        val fwdZ = cos(playerYaw)
        val renderTime = nowMs() - INTERP_DELAY_MS
        val sorted =
            npcBuffers.entries
                .filter { (id, _) -> id !in deadNpcIds }
                .filter { (_, buf) ->
                    val (pos, _, _) = interpolate(buf, renderTime)
                    val dx = pos.x.toDouble() - playerX
                    val dy = pos.y.toDouble() - playerY
                    val dz = pos.z.toDouble() - playerZ
                    val dist2 = dx * dx + dy * dy + dz * dz
                    if (dist2 > maxDist2) return@filter false
                    val dist2D = sqrt(dx * dx + dz * dz)
                    if (dist2D < 0.001) return@filter true
                    (dx * fwdX + dz * fwdZ) / dist2D >= halfConeCos
                }
                .sortedBy { (_, buf) ->
                    val (pos, _, _) = interpolate(buf, renderTime)
                    val dx = pos.x.toDouble() - playerX
                    val dy = pos.y.toDouble() - playerY
                    val dz = pos.z.toDouble() - playerZ
                    dx * dx + dy * dy + dz * dz
                }
                .map { it.key }
        if (sorted.isEmpty()) return null
        if (currentId == null) return sorted.first()
        val idx = sorted.indexOf(currentId)
        return if (idx < 0 || idx == sorted.lastIndex) null else sorted[idx + 1]
    }

    private fun updateAutocomplete() {
        val json = "[" + npcNames.values.joinToString(",") { "\"$it\"" } + "]"
        jsSetNpcNames(json)
    }

    fun nearestAggroNpc(playerX: Double, playerY: Double, playerZ: Double): String? {
        val renderTime = nowMs() - INTERP_DELAY_MS
        return npcBuffers.entries
            .filter { (id, _) -> id in aggroNpcIds && id !in deadNpcIds }
            .minByOrNull { (_, buf) ->
                val (pos, _, _) = interpolate(buf, renderTime)
                val dx = pos.x.toDouble() - playerX
                val dy = pos.y.toDouble() - playerY
                val dz = pos.z.toDouble() - playerZ
                dx * dx + dy * dy + dz * dz
            }
            ?.key
    }

    fun npcDistanceSquared(
        npcId: String,
        playerX: Double,
        playerY: Double,
        playerZ: Double
    ): Double? {
        val buf = npcBuffers[npcId] ?: return null
        val renderTime = nowMs() - INTERP_DELAY_MS
        val (pos, _, _) = interpolate(buf, renderTime)
        val dx = pos.x.toDouble() - playerX
        val dy = pos.y.toDouble() - playerY
        val dz = pos.z.toDouble() - playerZ
        return dx * dx + dy * dy + dz * dz
    }

    fun isAggroOnPlayer(npcId: String): Boolean = npcId in aggroNpcIds

    companion object {
        private const val INTERP_DELAY_MS = 100L
        private const val MAX_BUFFER = 8
        private const val PROXIMITY_THROTTLE_TICKS = 12
    }
}
