package org.micoli.micraft.game.physics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.PlayerConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.support.testWorld

private fun WorldState.solid() = { bx: Int, by: Int, bz: Int -> getBlock(bx, by, bz).isSolid }

class AabbColliderTest {
    private val w = PlayerConstants.WIDTH
    private val h = PlayerConstants.HEIGHT_STANDING

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

    // --- eye-level only block ---

    @Test
    fun resolveX_positive_stopsAtEyeLevelOnlyBlock() {
        // Block only at y=6 (eye level), none at y=5 (feet). cy=5, h=1.8 → ys=5..6 → must detect.
        val world = testWorld(Triple(10, 6, 8))
        val result = AabbCollider.resolveX(world.solid(), 9.5f, 5f, 8.5f, w, h, 1.0f)
        assertTrue(result < 1.0f, "Should stop at eye-level block: result=$result")
        assertTrue(9.5f + result + w / 2f <= 10f + 0.01f)
    }

    @Test
    fun resolveX_negative_stopsAtEyeLevelOnlyBlock() {
        val world = testWorld(Triple(7, 6, 8))
        val result = AabbCollider.resolveX(world.solid(), 8.5f, 5f, 8.5f, w, h, -1.0f)
        assertTrue(result > -1.0f, "Should stop at eye-level block: result=$result")
        assertTrue(8.5f + result - w / 2f >= 8f - 0.01f)
    }

    @Test
    fun resolveX_positive_stopsAtCameraLevelBlock() {
        // Camera at cy+1.62 is inside physics block cy+2 (due to block centering: block cy+2 spans Babylon Y cy+1.5..cy+2.5).
        // ys must cover cy+2, which requires HEIGHT_STANDING >= 2.001.
        val world = testWorld(Triple(10, 7, 8)) // block at cy+2 = 5+2 = 7
        val result = AabbCollider.resolveX(world.solid(), 9.5f, 5f, 8.5f, w, h, 1.0f)
        assertTrue(result < 1.0f, "Should stop at camera-level block (cy+2): result=$result")
        assertTrue(9.5f + result + w / 2f <= 10f + 0.01f)
    }

    @Test
    fun resolveX_negative_stopsAtEyeLevelBlock_approachingFromRight() {
        // Player approaching block 8 (eye-level only, y=6) from the right. cx-hw starts at 9.0 (outside block 8).
        // Should be stopped by block 8 before entering it.
        val world = testWorld(Triple(8, 6, 8))
        val cx = 9.0f + w / 2f  // cx-hw = 9.0, right face of block 8 column
        val result = AabbCollider.resolveX(world.solid(), cx, 5f, 8.5f, w, h, -1.0f)
        assertTrue(result > -1.0f, "Should stop at eye-level block when approaching from outside: result=$result")
        assertTrue(cx + result - w / 2f >= 9f - 0.01f)
    }

    // --- corner penetration ---

    @Test
    fun diagonalMovement_intoCorner_doesNotPenetrate() {
        // Corner block at (5,5,5), entity at (4.0,5.0,4.0) moving diagonally +X +Z by 1.0
        val world = testWorld(Triple(5, 5, 5))
        val solid = world.solid()
        val hw = w / 2f
        val speed = 1.0f

        val resolvedDx = AabbCollider.resolveX(solid, 4.0f, 5.0f, 4.0f, w, h, speed)
        val midX = 4.0f + resolvedDx
        val resolvedDz = AabbCollider.resolveZ(solid, midX, 5.0f, 4.0f, w, h, speed)
        val newZ = 4.0f + resolvedDz
        val newX = 4.0f + AabbCollider.resolveX(solid, 4.0f, 5.0f, newZ, w, h, speed)

        assertTrue(newX + hw <= 5.0f + 0.01f, "X right edge must not penetrate block at x=5: $newX")
        assertTrue(newZ + hw <= 5.0f + 0.01f, "Z right edge must not penetrate block at z=5: $newZ")
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
