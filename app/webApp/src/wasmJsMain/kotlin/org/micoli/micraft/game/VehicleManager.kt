package org.micoli.micraft.game

import kotlin.math.atan2
import kotlin.time.TimeSource
import org.micoli.micraft.babylon.*
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.vehicle.VehicleState

// Trimmed fork of NpcManager (see NpcManager.kt) for rail vehicles: same spawn/update/despawn
// lifecycle and position interpolation, minus everything that doesn't apply to a rail-bound,
// non-combat entity (aggro highlighting, dead state, minimap pins, proximity list, autocomplete).
class VehicleManager(private val scene: JsAny) : ServerMessageHandler {
    @OptIn(ExperimentalWasmJsInterop::class)
    private val vehicleModels = mutableMapOf<String, JsAny>()
    private val vehicleTypes = mutableMapOf<String, String>()
    private val vehicleBuffers = mutableMapOf<String, ArrayDeque<PosSnapshot>>()
    private val vehicleRenderedYaw = mutableMapOf<String, Float>()
    private val vehicleRenderedPitch = mutableMapOf<String, Float>()
    private val pendingVehicles = mutableListOf<VehicleState>()

    private val clock = TimeSource.Monotonic
    private val startMark = clock.markNow()

    private fun nowMs(): Long = startMark.elapsedNow().inWholeMilliseconds

    private class PosSnapshot(val pos: Vec3, val yaw: Float, val pitch: Float, val timeMs: Long)

    private data class InterpResult(val pos: Vec3, val yaw: Float, val pitch: Float)

    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.VehicleSpawned -> handleSpawned(msg.vehicle)
            is ServerMessage.VehicleUpdate -> handleUpdate(msg.vehicle)
            is ServerMessage.VehicleDespawned -> handleDespawned(msg.id)
            else -> Unit
        }

    fun handleSpawned(vehicle: VehicleState) {
        vehicleTypes[vehicle.id] = vehicle.vehicleType.id
        pushSnapshot(vehicle)
        if (!jsIsVehicleModelsReady()) {
            pendingVehicles.add(vehicle)
            return
        }
        ensureMesh(vehicle)
    }

    fun handleUpdate(vehicle: VehicleState) {
        pushSnapshot(vehicle)
        if (!jsIsVehicleModelsReady()) {
            pendingVehicles.removeAll { it.id == vehicle.id }
            pendingVehicles.add(vehicle)
            return
        }
        ensureMesh(vehicle)
    }

    fun handleDespawned(id: String) {
        vehicleTypes.remove(id)
        pendingVehicles.removeAll { it.id == id }
        vehicleModels.remove(id)?.let(::jsDisposeVehicleModel)
        vehicleBuffers.remove(id)
        vehicleRenderedYaw.remove(id)
        vehicleRenderedPitch.remove(id)
    }

    fun tick() {
        if (jsIsVehicleModelsReady() && pendingVehicles.isNotEmpty()) {
            val batch = pendingVehicles.toList()
            pendingVehicles.clear()
            batch.forEach { ensureMesh(it) }
        }
        if (!jsIsVehicleModelsReady()) return
        val renderTime = nowMs() - INTERP_DELAY_MS
        for ((id, buf) in vehicleBuffers) {
            val model = vehicleModels[id] ?: continue
            val (pos, yaw, pitch) = interpolate(buf, renderTime)
            val prevYaw = vehicleRenderedYaw[id] ?: yaw
            val renderedYaw = lerpAngle(prevYaw, yaw, 0.2f)
            vehicleRenderedYaw[id] = renderedYaw
            val prevPitch = vehicleRenderedPitch[id] ?: pitch
            val renderedPitch = lerp(prevPitch, pitch, 0.2f)
            vehicleRenderedPitch[id] = renderedPitch
            jsSetVehicleTransform(
                model,
                pos.x.toDouble(),
                pos.y.toDouble(),
                pos.z.toDouble(),
                renderedYaw,
                renderedPitch)
        }
    }

    @OptIn(ExperimentalWasmJsInterop::class) fun modelsMap(): Map<String, JsAny> = vehicleModels

    fun positionsMap(): Map<String, Vec3> {
        val renderTime = nowMs() - INTERP_DELAY_MS
        return vehicleBuffers.mapValues { (_, buf) -> interpolate(buf, renderTime).pos }
    }

    fun clear() {
        vehicleModels.values.forEach(::jsDisposeVehicleModel)
        vehicleModels.clear()
        vehicleTypes.clear()
        vehicleBuffers.clear()
        vehicleRenderedYaw.clear()
        vehicleRenderedPitch.clear()
        pendingVehicles.clear()
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun ensureMesh(vehicle: VehicleState) {
        if (vehicle.id in vehicleModels) return
        val model = jsCreateVehicleModel(scene, vehicle.vehicleType.id) ?: return
        vehicleModels[vehicle.id] = model
    }

    private fun pushSnapshot(vehicle: VehicleState) {
        val buf = vehicleBuffers.getOrPut(vehicle.id) { ArrayDeque() }
        buf.addLast(PosSnapshot(vehicle.pos, vehicle.yaw, vehicle.pitch, nowMs()))
        while (buf.size > MAX_BUFFER) buf.removeFirst()
    }

    private fun interpolate(buf: ArrayDeque<PosSnapshot>, renderTime: Long): InterpResult {
        if (buf.isEmpty()) return InterpResult(Vec3(0f, 0f, 0f), 0f, 0f)
        if (buf.size == 1 || renderTime <= buf.first().timeMs) {
            val s = buf.first()
            return InterpResult(s.pos, s.yaw, s.pitch)
        }
        if (renderTime >= buf.last().timeMs) {
            val s = buf.last()
            return InterpResult(s.pos, s.yaw, s.pitch)
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
            yaw = yaw,
            pitch = lerp(prev.pitch, next.pitch, t),
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lerpAngle(from: Float, to: Float, t: Float): Float {
        var diff = to - from
        val twoPi = (2.0 * kotlin.math.PI).toFloat()
        while (diff > kotlin.math.PI.toFloat()) diff -= twoPi
        while (diff < -kotlin.math.PI.toFloat()) diff += twoPi
        return from + diff * t
    }

    companion object {
        private const val INTERP_DELAY_MS = 100L
        private const val MAX_BUFFER = 8
    }
}
