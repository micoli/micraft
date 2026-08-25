package org.micoli.micraft.game

import kotlin.time.TimeSource
import org.micoli.micraft.babylon.*
import org.micoli.micraft.placeable.siege.SiegeProjectileState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage

// Trimmed fork of VehicleManager (see VehicleManager.kt): same spawn/update/despawn lifecycle and
// position interpolation buffer, minus yaw/pitch (a flying projectile's orientation isn't rendered
// yet) — despawn is driven by SiegeProjectileImpact instead of an explicit despawn message, since a
// projectile always ends its life by impacting something.
class SiegeProjectileManager(private val scene: JsAny) : ServerMessageHandler {
    @OptIn(ExperimentalWasmJsInterop::class)
    private val projectileModels = mutableMapOf<String, JsAny>()
    private val projectileTypes = mutableMapOf<String, String>()
    private val projectileBuffers = mutableMapOf<String, ArrayDeque<PosSnapshot>>()
    private val pendingProjectiles = mutableListOf<SiegeProjectileState>()

    private val clock = TimeSource.Monotonic
    private val startMark = clock.markNow()

    private fun nowMs(): Long = startMark.elapsedNow().inWholeMilliseconds

    private class PosSnapshot(val pos: Vec3, val timeMs: Long)

    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.SiegeProjectileSpawned -> handleSpawned(msg.projectile)
            is ServerMessage.SiegeProjectileUpdate -> handleUpdate(msg.projectile)
            is ServerMessage.SiegeProjectileImpact -> handleImpact(msg.x, msg.y, msg.z)
            else -> Unit
        }

    fun handleSpawned(projectile: SiegeProjectileState) {
        projectileTypes[projectile.id] = projectile.projectileType.id
        pushSnapshot(projectile)
        if (!jsIsSiegeProjectileModelsReady()) {
            pendingProjectiles.add(projectile)
            return
        }
        ensureMesh(projectile)
    }

    fun handleUpdate(projectile: SiegeProjectileState) {
        pushSnapshot(projectile)
        if (!jsIsSiegeProjectileModelsReady()) {
            pendingProjectiles.removeAll { it.id == projectile.id }
            pendingProjectiles.add(projectile)
            return
        }
        ensureMesh(projectile)
    }

    /**
     * A projectile has no explicit despawn message — its flight always ends in an impact, so this
     * disposes every model whose current (interpolated) position is near [x],[y],[z]. In practice
     * exactly one projectile impacts per broadcast; the proximity match is just simpler than
     * threading the projectile id through [ServerMessage.SiegeProjectileImpact] as well.
     */
    private fun handleImpact(x: Float, y: Float, z: Float) {
        val renderTime = nowMs() - INTERP_DELAY_MS
        val hitIds =
            projectileBuffers.entries
                .filter { (_, buf) ->
                    val pos = interpolate(buf, renderTime)
                    val dx = pos.x - x
                    val dy = pos.y - y
                    val dz = pos.z - z
                    dx * dx + dy * dy + dz * dz <= IMPACT_MATCH_RADIUS_SQ
                }
                .map { it.key }
        hitIds.forEach(::disposeProjectile)
    }

    private fun disposeProjectile(id: String) {
        projectileTypes.remove(id)
        pendingProjectiles.removeAll { it.id == id }
        projectileModels.remove(id)?.let(::jsDisposeSiegeProjectileModel)
        projectileBuffers.remove(id)
    }

    fun tick() {
        if (jsIsSiegeProjectileModelsReady() && pendingProjectiles.isNotEmpty()) {
            val batch = pendingProjectiles.toList()
            pendingProjectiles.clear()
            batch.forEach { ensureMesh(it) }
        }
        if (!jsIsSiegeProjectileModelsReady()) return
        val renderTime = nowMs() - INTERP_DELAY_MS
        for ((id, buf) in projectileBuffers) {
            val model = projectileModels[id] ?: continue
            val pos = interpolate(buf, renderTime)
            jsSetSiegeProjectileTransform(
                model, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        }
    }

    fun clear() {
        projectileModels.values.forEach(::jsDisposeSiegeProjectileModel)
        projectileModels.clear()
        projectileTypes.clear()
        projectileBuffers.clear()
        pendingProjectiles.clear()
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun ensureMesh(projectile: SiegeProjectileState) {
        if (projectile.id in projectileModels) return
        val model = jsCreateSiegeProjectileModel(scene, projectile.projectileType.id) ?: return
        projectileModels[projectile.id] = model
    }

    private fun pushSnapshot(projectile: SiegeProjectileState) {
        val buf = projectileBuffers.getOrPut(projectile.id) { ArrayDeque() }
        buf.addLast(PosSnapshot(projectile.pos, nowMs()))
        while (buf.size > MAX_BUFFER) buf.removeFirst()
    }

    private fun interpolate(buf: ArrayDeque<PosSnapshot>, renderTime: Long): Vec3 {
        if (buf.isEmpty()) return Vec3(0f, 0f, 0f)
        if (buf.size == 1 || renderTime <= buf.first().timeMs) return buf.first().pos
        if (renderTime >= buf.last().timeMs) return buf.last().pos
        val nextIdx = buf.indexOfFirst { it.timeMs >= renderTime }
        val prev = buf[nextIdx - 1]
        val next = buf[nextIdx]
        val t =
            ((renderTime - prev.timeMs).toFloat() / (next.timeMs - prev.timeMs)).coerceIn(0f, 1f)
        return Vec3(
            lerp(prev.pos.x, next.pos.x, t),
            lerp(prev.pos.y, next.pos.y, t),
            lerp(prev.pos.z, next.pos.z, t),
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    companion object {
        private const val INTERP_DELAY_MS = 100L
        private const val MAX_BUFFER = 8
        private const val IMPACT_MATCH_RADIUS_SQ =
            25f // 5 blocks — generous match, single-shooter case
    }
}
