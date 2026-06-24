package org.micoli.micraft.physics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.world.PlayerConstants
import org.micoli.micraft.world.WorldState
import org.micoli.micraft.world.isSolid

private fun WorldState.solid() = { bx: Int, by: Int, bz: Int -> getBlock(bx, by, bz).isSolid }

class AabbColliderTest {
    private val w = PlayerConstants.WIDTH
    private val h = 1.8f

    // --- isGrounded ---

    @Test
    fun isGrounded_solidBelow_returnsTrue() {
        // Stone at y=4, player standing at y=5
        val world = testWorld(Triple(8, 4, 8))
        assertTrue(AabbCollider.isGrounded(world.solid(), 8.5f, 5f, 8.5f, w))
    }

    @Test
    fun isGrounded_airBelow_returnsFalse() {
        val world = testWorld()
        assertFalse(AabbCollider.isGrounded(world.solid(), 8.5f, 5f, 8.5f, w))
    }

    @Test
    fun isGrounded_standingExactlyOnBlock_returnsTrue() {
        val world = testWorld(Triple(8, 4, 8))
        // y=5 exactly on top of block at y=4
        assertTrue(AabbCollider.isGrounded(world.solid(), 8.5f, 5f, 8.5f, w))
    }

    // --- resolveX ---

    @Test
    fun resolveX_zero_returnsZero() {
        val world = testWorld()
        val result = AabbCollider.resolveX(world.solid(), 8.5f, 5f, 8.5f, w, h, 0f)
        assertTrue(result == 0f)
    }

    @Test
    fun resolveX_positive_noObstacle_returnsFullDx() {
        val world = testWorld()
        val dx = 0.3f
        val result = AabbCollider.resolveX(world.solid(), 8.5f, 5f, 8.5f, w, h, dx)
        assertTrue(result == dx)
    }

    @Test
    fun resolveX_positive_stopsAtWall() {
        // Wall of stone at x=10 (blocks x=10,11,...) player at x=9.5 moving +1.0
        val world = testWorld(Triple(10, 5, 8))
        val result = AabbCollider.resolveX(world.solid(), 9.5f, 5f, 8.5f, w, h, 1.0f)
        // Should stop before the wall
        assertTrue(result < 1.0f)
        // New position should not penetrate the block
        assertTrue(9.5f + result + w / 2f <= 10f + 0.01f)
    }

    @Test
    fun resolveX_negative_stopsAtWall() {
        // Wall at x=7, player at x=8.5 moving -1.0
        val world = testWorld(Triple(7, 5, 8))
        val result = AabbCollider.resolveX(world.solid(), 8.5f, 5f, 8.5f, w, h, -1.0f)
        assertTrue(result > -1.0f)
        assertTrue(8.5f + result - w / 2f >= 8f - 0.01f)
    }

    @Test
    fun resolveX_negative_noObstacle_returnsFullDx() {
        val world = testWorld()
        val dx = -0.3f
        val result = AabbCollider.resolveX(world.solid(), 8.5f, 5f, 8.5f, w, h, dx)
        assertTrue(result == dx)
    }

    // --- resolveZ ---

    @Test
    fun resolveZ_zero_returnsZero() {
        val world = testWorld()
        val result = AabbCollider.resolveZ(world.solid(), 8.5f, 5f, 8.5f, w, h, 0f)
        assertTrue(result == 0f)
    }

    @Test
    fun resolveZ_positive_noObstacle_returnsFullDz() {
        val world = testWorld()
        val dz = 0.3f
        val result = AabbCollider.resolveZ(world.solid(), 8.5f, 5f, 8.5f, w, h, dz)
        assertTrue(result == dz)
    }

    @Test
    fun resolveZ_positive_stopsAtWall() {
        val world = testWorld(Triple(8, 5, 10))
        val result = AabbCollider.resolveZ(world.solid(), 8.5f, 5f, 9.5f, w, h, 1.0f)
        assertTrue(result < 1.0f)
    }

    // --- resolveY ---

    @Test
    fun resolveY_zero_returnsZero() {
        val world = testWorld()
        val result = AabbCollider.resolveY(world.solid(), 8.5f, 5f, 8.5f, w, h, 0f)
        assertTrue(result == 0f)
    }

    @Test
    fun resolveY_falling_landsOnBlock() {
        // Stone floor at y=3, player falling from y=5
        val world = testWorld(Triple(8, 3, 8))
        val result = AabbCollider.resolveY(world.solid(), 8.5f, 5f, 8.5f, w, h, -3f)
        // Should stop at y=4 (top of block at y=3)
        val newY = 5f + result
        assertTrue(newY >= 4f - 0.01f)
        assertTrue(result > -3f)
    }

    @Test
    fun resolveY_rising_hitsHeadOnCeiling() {
        // Ceiling at y=7, player height 1.8 standing at y=5 (top of head at y=6.8)
        val world = testWorld(Triple(8, 7, 8))
        val result = AabbCollider.resolveY(world.solid(), 8.5f, 5f, 8.5f, w, h, 3f)
        // Head (y + h = 5 + 1.8 = 6.8) hits block at 7 → stops before
        assertTrue(result < 3f)
    }

    @Test
    fun resolveY_noObstacle_returnsFullDy() {
        val world = testWorld()
        val dy = -2f
        val result = AabbCollider.resolveY(world.solid(), 8.5f, 5f, 8.5f, w, h, dy)
        assertTrue(result == dy)
    }

    // --- canAdoptStance ---

    @Test
    fun canAdoptStance_shrinking_alwaysTrue() {
        val world = testWorld(Triple(8, 7, 8)) // obstacle nearby
        val result =
            AabbCollider.canAdoptStance(
                world.solid(), 8.5f, 5f, 8.5f, w, newH = 1.5f, currentH = 1.8f)
        assertTrue(result)
    }

    @Test
    fun canAdoptStance_growing_clearSpace_returnsTrue() {
        val world = testWorld()
        val result =
            AabbCollider.canAdoptStance(
                world.solid(), 8.5f, 5f, 8.5f, w, newH = 1.8f, currentH = 1.5f)
        assertTrue(result)
    }

    @Test
    fun canAdoptStance_growing_blocked_returnsFalse() {
        // Block at y=6 blocks expansion from height 1.5 (top at 6.5) to 1.8 (top at 6.8)
        val world = testWorld(Triple(8, 6, 8))
        val result =
            AabbCollider.canAdoptStance(
                world.solid(), 8.5f, 5f, 8.5f, w, newH = 1.8f, currentH = 1.5f)
        assertFalse(result)
    }
}
