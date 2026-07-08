package org.micoli.micraft.game.tick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class MovementProcessorTest {

    private fun noInput(
        stance: PlayerStance = PlayerStance.STANDING,
        dx: Float = 0f,
        dz: Float = 0f,
        dy: Float = 0f,
        jump: Boolean = false,
        flyToggle: Boolean = false,
        speedUp: Boolean = false,
        speedDown: Boolean = false,
        yaw: Float = 0f,
        pitch: Float = 0f,
    ) =
        TickInput(
            dx = dx,
            dz = dz,
            dy = dy,
            yaw = yaw,
            pitch = pitch,
            stance = stance,
            jumpRequested = jump,
            flyToggleRequested = flyToggle,
            speedUpRequested = speedUp,
            speedDownRequested = speedDown,
        )

    @Test
    fun noInput_playerStaysAtXZ_whenGrounded() {
        // Ground at y=4, player at y=5
        val world = testWorld(Triple(8, 4, 8))
        val processor = MovementProcessor(world)
        val session = testSession(pos = Vec3(8.5f, 5f, 8.5f))
        val result = processor.process(session, noInput())
        // X and Z should not change without movement input
        assertEquals(8.5f, result.pos.x, 0.01f)
        assertEquals(8.5f, result.pos.z, 0.01f)
    }

    @Test
    fun movingRight_updatesX() {
        val world = testWorld(Triple(8, 4, 8))
        val processor = MovementProcessor(world)
        val session = testSession(pos = Vec3(8.5f, 5f, 8.5f))
        val result = processor.process(session, noInput(dx = 1f))
        assertTrue(result.pos.x > 8.5f, "Expected x to increase, got ${result.pos.x}")
    }

    @Test
    fun blockedByWall_doesNotPassThrough() {
        // Wall at x=10, player at x=9.2 trying to move +2.0
        val world = testWorld(Triple(10, 5, 8), Triple(8, 4, 8))
        val processor = MovementProcessor(world)
        val session = testSession(pos = Vec3(9.2f, 5f, 8.5f))
        val result = processor.process(session, noInput(dx = 1f))
        assertTrue(
            result.pos.x < 10f, "Expected player to be blocked before x=10, got ${result.pos.x}")
    }

    @Test
    fun jump_whenGrounded_setsPositiveVy() {
        val world = testWorld(Triple(8, 4, 8))
        val processor = MovementProcessor(world)
        val session = testSession(pos = Vec3(8.5f, 5f, 8.5f))
        session.vy = 0f
        processor.process(session, noInput(jump = true))
        assertTrue(session.vy > 0f, "Expected vy > 0 after jump, got ${session.vy}")
    }

    @Test
    fun jump_whenAirborne_ignored() {
        val world = testWorld() // no ground
        val processor = MovementProcessor(world)
        val session = testSession(pos = Vec3(8.5f, 10f, 8.5f))
        session.vy = -1f
        processor.process(session, noInput(jump = true))
        // vy should not be set to JUMP_SPEED since not grounded (and vy != 0)
        // it may change due to gravity but should not become JUMP_SPEED
        assertFalse(session.vy > 8f, "Expected jump to be ignored when airborne")
    }

    @Test
    fun flyToggle_setsFlying() {
        val world = testWorld()
        val processor = MovementProcessor(world)
        val session = testSession()
        assertFalse(session.state.flying)
        val result = processor.process(session, noInput(flyToggle = true))
        assertTrue(result.flying)
    }

    @Test
    fun flyToggle_twice_togglesBack() {
        val world = testWorld()
        val processor = MovementProcessor(world)
        val session = testSession()
        val r1 = processor.process(session, noInput(flyToggle = true))
        session.state = r1
        val r2 = processor.process(session, noInput(flyToggle = true))
        assertFalse(r2.flying)
    }

    @Test
    fun flying_movesVertically_withDyInput() {
        val world = testWorld()
        val processor = MovementProcessor(world)
        val session = testSession(pos = Vec3(8.5f, 50f, 8.5f))
        session.state = session.state.copy(flying = true)
        val result = processor.process(session, noInput(dy = 1f))
        assertTrue(result.pos.y > 50f, "Expected y to increase, got ${result.pos.y}")
    }

    @Test
    fun gravity_pullsDown_whenAirborne() {
        val world = testWorld() // no floor, player in air
        val processor = MovementProcessor(world)
        val session = testSession(pos = Vec3(8.5f, 20f, 8.5f))
        session.vy = 0f
        val result = processor.process(session, noInput())
        assertTrue(result.pos.y < 20f, "Expected gravity to pull player down, got ${result.pos.y}")
    }

    @Test
    fun speedUp_increasesMultiplier() {
        val world = testWorld()
        val processor = MovementProcessor(world)
        val session = testSession()
        assertEquals(1f, session.state.speedMultiplier)
        val result = processor.process(session, noInput(speedUp = true))
        assertTrue(result.speedMultiplier > 1f)
    }

    @Test
    fun speedDown_decreasesMultiplier() {
        val world = testWorld()
        val processor = MovementProcessor(world)
        val session = testSession()
        val result = processor.process(session, noInput(speedDown = true))
        assertTrue(result.speedMultiplier < 1f)
    }

    @Test
    fun speedMultiplier_clampedAtMax() {
        val world = testWorld()
        val processor = MovementProcessor(world)
        val session = testSession()
        session.state = session.state.copy(speedMultiplier = 5.0f)
        val result = processor.process(session, noInput(speedUp = true))
        assertEquals(5.0f, result.speedMultiplier)
    }

    @Test
    fun speedMultiplier_clampedAtMin() {
        val world = testWorld()
        val processor = MovementProcessor(world)
        val session = testSession()
        session.state = session.state.copy(speedMultiplier = 0.5f)
        val result = processor.process(session, noInput(speedDown = true))
        assertEquals(0.5f, result.speedMultiplier)
    }
}
