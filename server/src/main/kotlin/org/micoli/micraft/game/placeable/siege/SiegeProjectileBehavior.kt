package org.micoli.micraft.game.placeable.siege

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import org.micoli.micraft.game.GRAVITY
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3

/**
 * Pure, testable per-tick physics for a flying siege projectile — a sibling to
 * [org.micoli.micraft.game.vehicle.VehicleBehavior], but free flight (gravity-integrated velocity,
 * no rail constraint) instead of rail-bound movement.
 *
 * Mutates [SiegeProjectileInstance.pos]/[SiegeProjectileInstance.velocity] in place and returns the
 * terrain impact, if any, this tick — matches [org.micoli.micraft.game.vehicle.VehicleBehavior]'s
 * mutate-and-return-a-signal convention. Entity (player/NPC) collision is intentionally NOT checked
 * here: it needs live session/NPC lists this pure function has no access to, and is handled instead
 * by [SiegeProjectileManager.tick] using the same before/after tick positions this function leaves
 * on the instance.
 */
object SiegeProjectileBehavior {
    /** Terrain sub-stepping granularity, in blocks — small enough to never skip a thin wall. */
    private const val MAX_SUBSTEP = 0.5f

    data class ImpactResult(val pos: Vec3)

    fun tick(
        instance: SiegeProjectileInstance,
        world: WorldState,
        tickSeconds: Float
    ): ImpactResult? {
        val v = instance.velocity
        val newVy = v.y + GRAVITY * tickSeconds
        val start = instance.pos
        val end =
            Vec3(
                x = start.x + v.x * tickSeconds,
                y = start.y + newVy * tickSeconds,
                z = start.z + v.z * tickSeconds,
            )
        val newVelocity = Vec3(v.x, newVy, v.z)

        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        val steps = if (dist <= MAX_SUBSTEP) 1 else ceil(dist / MAX_SUBSTEP).toInt()

        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val sx = start.x + dx * t
            val sy = start.y + dy * t
            val sz = start.z + dz * t
            if (world.isSolidOrOccupied(floor(sx).toInt(), floor(sy).toInt(), floor(sz).toInt())) {
                val hitPos = Vec3(sx, sy, sz)
                instance.pos = hitPos
                instance.velocity = newVelocity
                return ImpactResult(hitPos)
            }
        }
        instance.pos = end
        instance.velocity = newVelocity
        return null
    }
}
