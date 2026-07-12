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
            val dy = instance.vy * TICK_SECONDS
            instance.vy += GRAVITY * TICK_SECONDS
            val resolvedDy =
                AabbCollider.resolveY(solid, pos.x, pos.y, pos.z, def.width, def.height, dy)
            if (resolvedDy != dy) instance.vy = 0f
            instance.velocity = Vec3(instance.velocity.x, instance.vy, instance.velocity.z)
            val newY = (pos.y + resolvedDy).coerceAtLeast(0f)
            instance.state = instance.state.copy(pos = Vec3(pos.x, newY, pos.z))
            resolvedDy != 0f
        }
    }
}
