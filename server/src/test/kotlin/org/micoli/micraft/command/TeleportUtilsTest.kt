package org.micoli.micraft.command

import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.world.WorldConstants
import kotlin.test.Test
import kotlin.test.assertEquals

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
        // solid at y=10, air at y=11 and y=12
        val world = testWorld(Triple(8, 10, 8))
        val target = Vec3(8f, 10f, 8f)
        val result = safeTeleportPos(world, target)
        assertEquals(11f, result.y)
    }

    @Test
    fun scansUpward_multipleConsecutiveSolid() {
        // solid at y=5,6,7; air at y=8,9
        val world = testWorld(Triple(4, 5, 4), Triple(4, 6, 4), Triple(4, 7, 4))
        val target = Vec3(4f, 5f, 4f)
        val result = safeTeleportPos(world, target)
        assertEquals(8f, result.y)
    }

    @Test
    fun returnsMaxY_whenNoSafeSpotExists() {
        // Fill a column with stone — build a fully-solid generator
        val blocks = (0 until WorldConstants.WORLD_MAX_Y).associate { Triple(0, it, 0) to org.micoli.micraft.world.BlockType.STONE }
        val world = org.micoli.micraft.world.WorldState(org.micoli.micraft.support.MapChunkGenerator(blocks))
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
