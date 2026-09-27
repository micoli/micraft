package org.micoli.micraft.physics

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.Vec3

private const val SERVER_DT = 0.05f
private const val CLIENT_DT = 1f / 60f
private const val RECONCILE_TOLERANCE_XZ = 0.5f
private const val RECONCILE_TOLERANCE_Y = 0.99f

private object Ground : BlockQuery {
    override fun isSolid(x: Int, y: Int, z: Int) = y <= 0

    override fun isLiquid(x: Int, y: Int, z: Int) = false

    override fun liquidSlowdown(x: Int, y: Int, z: Int) = 1f
}

/**
 * The server steps every 50 ms, the client Prediction every frame: over one second both must end
 * within the reconcile tolerances, or Prediction visibly fights the server.
 */
class PlayerKinematicsParityTest {

    private fun simulate(
        start: KinematicState,
        dt: Float,
        intentAt: (step: Int) -> MoveIntent,
    ): Vec3 {
        var state = start
        repeat((1f / dt).roundToInt()) { i ->
            state = PlayerKinematics.step(state, intentAt(i), dt, Ground, KinematicTuning()).state
        }
        return state.pos
    }

    private fun assertInParity(start: KinematicState, intentAt: (step: Int) -> MoveIntent) {
        val server = simulate(start, SERVER_DT, intentAt)
        val client = simulate(start, CLIENT_DT, intentAt)

        val xz = hypot(server.x - client.x, server.z - client.z)
        val y = abs(server.y - client.y)
        assertTrue(xz < RECONCILE_TOLERANCE_XZ, "XZ gap $xz (server $server, client $client)")
        assertTrue(y < RECONCILE_TOLERANCE_Y, "Y gap $y (server $server, client $client)")
    }

    private fun standing(y: Float) =
        KinematicState(Vec3(8.5f, y, 8.5f), 0f, PlayerStance.STANDING, false, 1f)

    @Test
    fun `walking one second stays in parity`() =
        assertInParity(standing(1f)) { MoveIntent(dx = 1f, dz = 1f) }

    @Test
    fun `falling one second stays in parity`() = assertInParity(standing(30f)) { MoveIntent() }

    @Test
    fun `a jump stays in parity`() =
        assertInParity(standing(1f)) { step -> MoveIntent(dx = 1f, jump = step == 0) }

    @Test
    fun `flying up at top speed stays in parity`() =
        assertInParity(standing(10f).copy(flying = true, speedMultiplier = 5f)) {
            MoveIntent(dx = 1f, dy = 1f)
        }
}
