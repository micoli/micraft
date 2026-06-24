package org.micoli.micraft.plugins.teleport

import kotlin.math.floor
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldState

fun safeTeleportPos(world: WorldState, target: Vec3): Vec3 {
    var y = floor(target.y).toInt()
    val maxY = WorldConstants.WORLD_MAX_Y - 2
    while (y < maxY) {
        if (world.getBlock(floor(target.x).toInt(), y, floor(target.z).toInt()) == BlockType.AIR &&
            world.getBlock(floor(target.x).toInt(), y + 1, floor(target.z).toInt()) ==
                BlockType.AIR) {
            return target.copy(y = y.toFloat())
        }
        y++
    }
    return target.copy(y = maxY.toFloat())
}
