package org.micoli.micraft.game.npc

import org.micoli.micraft.game.GRAVITY
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.Vec3

internal object NpcPhysics {
    fun applyGravity(instance: NpcInstance, world: WorldState): Boolean {
        val def = instance.definition
        val pos = instance.state.pos
        // Swimmers hold depth while submerged; a flying NPC has gravity off and its altitude
        // driven by [cruise] from the behaviour.
        if (instance.weightless ||
            instance.flying ||
            (def.canSwim &&
                world.getBlockIfLoaded(pos.x.toInt(), pos.y.toInt(), pos.z.toInt()).isLiquid)) {
            instance.vy = 0f
            instance.velocity = Vec3(instance.velocity.x, 0f, instance.velocity.z)
            return false
        }
        val solid = { bx: Int, by: Int, bz: Int -> world.getBlockIfLoaded(bx, by, bz).isSolid }

        return if (instance.vy <= 0f &&
            AabbCollider.isGrounded(solid, pos.x, pos.y, pos.z, def.width)) {
            instance.vy = 0f
            instance.velocity = Vec3(instance.velocity.x, 0f, instance.velocity.z)
            val snapDy =
                AabbCollider.resolveY(solid, pos.x, pos.y, pos.z, def.width, def.height, -1f)
            val newY = (pos.y + snapDy).coerceAtLeast(0f)
            if (newY != pos.y) {
                instance.state = instance.state.copy(pos = Vec3(pos.x, newY, pos.z))
                true
            } else false
        } else {
            instance.vy += GRAVITY * TICK_SECONDS
            val dy = instance.vy * TICK_SECONDS
            val resolvedDy =
                AabbCollider.resolveY(solid, pos.x, pos.y, pos.z, def.width, def.height, dy)
            if (resolvedDy != dy) instance.vy = 0f
            instance.velocity = Vec3(instance.velocity.x, instance.vy, instance.velocity.z)
            val newY = (pos.y + resolvedDy).coerceAtLeast(0f)
            instance.state = instance.state.copy(pos = Vec3(pos.x, newY, pos.z))
            resolvedDy != 0f
        }
    }

    /**
     * Steers a flying NPC toward [cruiseHeight] blocks above the ground beneath it, moving at most
     * [step] blocks of altitude per tick. Refuses to climb into a ceiling. Returns true if Y moved.
     */
    fun cruise(
        instance: NpcInstance,
        world: WorldState,
        cruiseHeight: Float,
        step: Float
    ): Boolean {
        val def = instance.definition
        val pos = instance.state.pos
        val solid = { bx: Int, by: Int, bz: Int -> world.getBlockIfLoaded(bx, by, bz).isSolid }
        instance.vy = 0f
        instance.velocity = Vec3(instance.velocity.x, 0f, instance.velocity.z)

        val drop = AabbCollider.resolveY(solid, pos.x, pos.y, pos.z, def.width, def.height, -64f)
        val groundY = pos.y + drop
        val targetY = maxOf(groundY + cruiseHeight, groundY + 2f)
        val delta = (targetY - pos.y).coerceIn(-step, step)
        if (delta == 0f) return false

        val newY = pos.y + delta
        if (delta > 0f &&
            AabbCollider.isOverlapping(solid, pos.x, newY, pos.z, def.width, def.height))
            return false

        instance.state = instance.state.copy(pos = Vec3(pos.x, newY, pos.z))
        return true
    }
}
