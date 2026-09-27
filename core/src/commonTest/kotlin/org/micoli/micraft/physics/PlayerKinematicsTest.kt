package org.micoli.micraft.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.Vec3

private const val DT = 0.05f
private const val EPS = 1e-4f

private class FakeBlocks(
    private val solid: (Int, Int, Int) -> Boolean,
    private val liquid: (Int, Int, Int) -> Boolean = { _, _, _ -> false },
) : BlockQuery {
    override fun isSolid(x: Int, y: Int, z: Int) = solid(x, y, z)

    override fun isLiquid(x: Int, y: Int, z: Int) = liquid(x, y, z)

    override fun liquidSlowdown(x: Int, y: Int, z: Int) = if (liquid(x, y, z)) 0.5f else 1f
}

private val FLAT_GROUND = FakeBlocks({ _, y, _ -> y <= 0 })
private val OPEN_AIR = FakeBlocks({ _, _, _ -> false })

private fun state(
    x: Float = 8.5f,
    y: Float = 1f,
    vy: Float = 0f,
    stance: PlayerStance = PlayerStance.STANDING,
    flying: Boolean = false,
    speedMultiplier: Float = 1f,
) = KinematicState(Vec3(x, y, 8.5f), vy, stance, flying, speedMultiplier)

private fun step(
    state: KinematicState,
    blocks: BlockQuery = FLAT_GROUND,
    intent: MoveIntent = MoveIntent(),
) = PlayerKinematics.step(state, intent, DT, blocks, KinematicTuning())

class PlayerKinematicsTest {

    @Test
    fun `a player in the air accelerates under gravity`() {
        val result = step(state(y = 10f), OPEN_AIR)

        assertEquals(-1f, result.state.vy, EPS)
        assertEquals(9.95f, result.state.pos.y, EPS)
    }

    @Test
    fun `a grounded jump applies gravity in the same step`() {
        val result = step(state(stance = PlayerStance.SNEAKING), intent = MoveIntent(jump = true))

        assertEquals(7.5f, result.state.vy, EPS)
        assertEquals(1.375f, result.state.pos.y, EPS)
        assertEquals(PlayerStance.STANDING, result.state.stance)
    }

    @Test
    fun `walking moves at the stance speed`() {
        val result = step(state(), intent = MoveIntent(dx = 1f))

        assertEquals(8.725f, result.state.pos.x, EPS) // 8.5 + 4.5 * 0.05
        assertEquals(1f, result.state.pos.y, EPS)
    }

    @Test
    fun `walking into a wall reports the move as blocked`() {
        val wall = FakeBlocks({ x, y, _ -> y <= 0 || x == 9 })

        assertTrue(step(state(x = 8.7f), wall, MoveIntent(dx = 1f)).blockedHorizontally)
        assertFalse(step(state(), intent = MoveIntent(dx = 1f)).blockedHorizontally)
        assertFalse(step(state(), wall).blockedHorizontally)
    }

    @Test
    fun `toggling flight on cancels the fall and flies vertically`() {
        val result = step(state(y = 10f, vy = -5f), OPEN_AIR, MoveIntent(dy = 1f, flyToggle = true))

        assertTrue(result.state.flying)
        assertEquals(0f, result.state.vy, EPS)
        assertEquals(10.4f, result.state.pos.y, EPS) // 8 * 0.05
    }

    @Test
    fun `a submerged player swims crawling at its land speed slowed by the liquid`() {
        val water = FakeBlocks({ _, y, _ -> y <= 0 }, { _, y, _ -> y in 1..3 })

        val result = step(state(), water, MoveIntent(dx = 1f))

        assertTrue(result.submerged)
        assertTrue(result.headInLiquid)
        assertEquals(PlayerStance.CRAWLING, result.state.stance)
        assertEquals(8.6125f, result.state.pos.x, EPS) // 8.5 + 4.5 * 0.05 * 0.5
    }

    @Test
    fun `a player stuck inside a block is ejected upward`() {
        val result = step(state(y = 1.2f), FakeBlocks({ _, y, _ -> y <= 1 }))

        assertEquals(1.2f, result.ejectedFromY)
        assertTrue(result.state.pos.y >= 2f)
    }

    @Test
    fun `a crawler under a low ceiling cannot stand up`() {
        val lowCeiling = FakeBlocks({ _, y, _ -> y <= 0 || y == 2 })

        val result =
            step(
                state(stance = PlayerStance.CRAWLING),
                lowCeiling,
                MoveIntent(stance = PlayerStance.STANDING))

        assertEquals(PlayerStance.CRAWLING, result.state.stance)
    }

    @Test
    fun `speed multiplier steps by half and stays within bounds`() {
        assertEquals(1.5f, step(state(), intent = MoveIntent(speedUp = true)).state.speedMultiplier)
        assertEquals(
            5f,
            step(state(speedMultiplier = 5f), intent = MoveIntent(speedUp = true))
                .state
                .speedMultiplier)
        assertEquals(
            0.5f,
            step(state(speedMultiplier = 0.5f), intent = MoveIntent(speedDown = true))
                .state
                .speedMultiplier)
        assertFalse(step(state()).state.flying)
    }
}
