package org.micoli.micraft.plugins.teleport

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldState

class TeleportUtilsTest {

    @Test
    fun returnsTargetY_whenAlreadyTwoAirAbove() {
        val world = testWorld()
        val target = Vec3(8f, 10f, 8f)
        val result = safeTeleportPos(world, target)
        assertEquals(10f, result.y)
    }

    @Test
    fun scansUpward_whenTargetBlockIsSolid() {
        val world = testWorld(Triple(8, 10, 8))
        val target = Vec3(8f, 10f, 8f)
        val result = safeTeleportPos(world, target)
        assertEquals(11f, result.y)
    }

    @Test
    fun scansUpward_multipleConsecutiveSolid() {
        val world = testWorld(Triple(4, 5, 4), Triple(4, 6, 4), Triple(4, 7, 4))
        val target = Vec3(4f, 5f, 4f)
        val result = safeTeleportPos(world, target)
        assertEquals(8f, result.y)
    }

    @Test
    fun returnsMaxY_whenNoSafeSpotExists() {
        val blocks =
            (0 until WorldConstants.WORLD_MAX_Y).associate { Triple(0, it, 0) to BlockType.STONE }
        val world = WorldState(MapChunkGenerator(blocks))
        val target = Vec3(0f, 0f, 0f)
        val result = safeTeleportPos(world, target)
        assertEquals((WorldConstants.WORLD_MAX_Y - 2).toFloat(), result.y)
    }

    @Test
    fun preservesXZ() {
        val world = testWorld()
        val target = Vec3(17f, 5f, -3f)
        val result = safeTeleportPos(world, target)
        assertEquals(17f, result.x)
        assertEquals(-3f, result.z)
    }
}
