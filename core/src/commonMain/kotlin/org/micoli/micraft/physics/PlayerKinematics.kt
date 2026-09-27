package org.micoli.micraft.physics

import kotlin.math.floor
import kotlin.math.sqrt
import org.micoli.micraft.game.world.PlayerConstants
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.player.height
import org.micoli.micraft.player.speed

/** The world as movement sees it; each side decides what counts as solid. */
interface BlockQuery {
    fun isSolid(x: Int, y: Int, z: Int): Boolean

    fun isLiquid(x: Int, y: Int, z: Int): Boolean

    fun liquidSlowdown(x: Int, y: Int, z: Int): Float
}

/** Server-tunable movement values; the client receives them, never hardcodes them (ADR-0002). */
data class KinematicTuning(
    val gravity: Float = -20f,
    val jumpSpeed: Float = 8.5f,
    val flyVerticalSpeed: Float = 8f,
)

data class KinematicState(
    val pos: Vec3,
    val vy: Float,
    val stance: PlayerStance,
    val flying: Boolean,
    val speedMultiplier: Float,
)

/** [dx]/[dz] is the horizontal direction (normalised here), [dy] the fly/swim vertical input. */
data class MoveIntent(
    val dx: Float = 0f,
    val dz: Float = 0f,
    val dy: Float = 0f,
    val stance: PlayerStance = PlayerStance.STANDING,
    val jump: Boolean = false,
    val flyToggle: Boolean = false,
    val speedUp: Boolean = false,
    val speedDown: Boolean = false,
)

data class KinematicResult(
    val state: KinematicState,
    val submerged: Boolean,
    val headInLiquid: Boolean,
    /** Y the player was stuck at before being ejected upward, null when it was free. */
    val ejectedFromY: Float? = null,
)

/**
 * The movement rules shared by the server simulation and the client Prediction: stance, submerge,
 * gravity, jump, flight, collision and liquid slowdown for one step of [dt] seconds.
 */
object PlayerKinematics {
    private const val SPEED_MULTIPLIER_STEP = 0.5f
    private const val MIN_SPEED_MULTIPLIER = 0.5f
    private const val MAX_SPEED_MULTIPLIER = 5f
    private const val LIQUID_GRAVITY_FACTOR = 0.2f
    private const val MAX_SINK_SPEED = 2f

    fun step(
        state: KinematicState,
        intent: MoveIntent,
        dt: Float,
        blocks: BlockQuery,
        tuning: KinematicTuning,
    ): KinematicResult {
        val w = PlayerConstants.WIDTH
        val solid = blocks::isSolid
        val raw = state.pos
        val stuck = AabbCollider.isOverlapping(solid, raw.x, raw.y, raw.z, w, state.stance.height)
        val pos =
            if (stuck)
                raw.copy(
                    y = AabbCollider.ejectUp(solid, raw.x, raw.y, raw.z, w, state.stance.height))
            else raw

        val speedMultiplier =
            when {
                intent.speedUp ->
                    (state.speedMultiplier + SPEED_MULTIPLIER_STEP).coerceAtMost(
                        MAX_SPEED_MULTIPLIER)
                intent.speedDown ->
                    (state.speedMultiplier - SPEED_MULTIPLIER_STEP).coerceAtLeast(
                        MIN_SPEED_MULTIPLIER)
                else -> state.speedMultiplier
            }
        val flying = if (intent.flyToggle) !state.flying else state.flying
        var vy = if (intent.flyToggle && flying) 0f else state.vy

        val feet = pos.blockCoords(0f)
        val eye = pos.blockCoords(state.stance.eyeOffset)
        // Submerged => swim: stance is forced to CRAWLING for hitbox/rendering, but horizontal
        // speed keeps using the stance the player requested on land.
        val submerged = !flying && (blocks.isLiquid(feet) || blocks.isLiquid(eye))

        var stance = nextStance(solid, pos, state.stance, intent.stance, submerged, flying)
        val h = stance.height
        val speedStance = if (submerged) intent.stance else stance
        val speed = speedStance.speed * speedMultiplier * dt * blocks.liquidSlowdown(feet)

        val len = sqrt(intent.dx * intent.dx + intent.dz * intent.dz)
        val nx = if (len > 0f) intent.dx / len else 0f
        val nz = if (len > 0f) intent.dz / len else 0f

        if (!flying &&
            !submerged &&
            intent.jump &&
            vy == 0f &&
            AabbCollider.isGrounded(solid, pos.x, pos.y, pos.z, w)) {
            vy = tuning.jumpSpeed
            stance = PlayerStance.STANDING
        }

        val midX = pos.x + AabbCollider.resolveX(solid, pos.x, pos.y, pos.z, w, h, nx * speed)
        val newZ = pos.z + AabbCollider.resolveZ(solid, midX, pos.y, pos.z, w, h, nz * speed)
        val newX = pos.x + AabbCollider.resolveX(solid, pos.x, pos.y, newZ, w, h, nx * speed)

        val newY: Float
        if (flying) {
            val flyDy = intent.dy * tuning.flyVerticalSpeed * speedMultiplier * dt
            val resolvedDy = AabbCollider.resolveY(solid, newX, pos.y, newZ, w, h, flyDy)
            newY = (pos.y + resolvedDy).coerceIn(0f, WorldConstants.WORLD_MAX_Y.toFloat())
        } else {
            val swimUp = submerged && (intent.jump || intent.dy > 0f)
            val swimDown = submerged && intent.dy < 0f
            val fall =
                fall(solid, newX, pos.y, newZ, h, vy, dt, tuning, submerged, swimUp, swimDown)
            vy = fall.vy
            newY = fall.y
        }

        val newPos = Vec3(newX, newY, newZ)
        return KinematicResult(
            state = KinematicState(newPos, vy, stance, flying, speedMultiplier),
            submerged = submerged,
            headInLiquid = blocks.isLiquid(newPos.blockCoords(stance.eyeOffset)),
            ejectedFromY = if (stuck) raw.y else null,
        )
    }

    private fun nextStance(
        solid: (Int, Int, Int) -> Boolean,
        pos: Vec3,
        current: PlayerStance,
        requested: PlayerStance,
        submerged: Boolean,
        flying: Boolean,
    ): PlayerStance {
        fun fits(target: PlayerStance) =
            AabbCollider.canAdoptStance(
                solid, pos.x, pos.y, pos.z, PlayerConstants.WIDTH, target.height, current.height)
        return when {
            submerged && fits(PlayerStance.CRAWLING) -> PlayerStance.CRAWLING
            !flying && fits(requested) -> requested
            // Flight is predicted STANDING; a stale CRAWLING/SNEAKING stance would fly at its
            // speed.
            flying && fits(PlayerStance.STANDING) -> PlayerStance.STANDING
            else -> current
        }
    }

    private class Fall(val y: Float, val vy: Float)

    private fun fall(
        solid: (Int, Int, Int) -> Boolean,
        cx: Float,
        cy: Float,
        cz: Float,
        h: Float,
        vy: Float,
        dt: Float,
        tuning: KinematicTuning,
        inLiquid: Boolean,
        swimUp: Boolean,
        swimDown: Boolean,
    ): Fall {
        val w = PlayerConstants.WIDTH
        if (vy <= 0f && AabbCollider.isGrounded(solid, cx, cy, cz, w)) {
            // Snap to the ground surface in case the player partially sank into a block.
            val snapDy = AabbCollider.resolveY(solid, cx, cy, cz, w, h, -1f)
            return Fall((cy + snapDy).coerceAtLeast(0f), 0f)
        }
        val newVy =
            when {
                swimUp -> PlayerConstants.SWIM_UP_SPEED
                swimDown -> -PlayerConstants.SWIM_DOWN_SPEED
                inLiquid ->
                    (vy + tuning.gravity * LIQUID_GRAVITY_FACTOR * dt).coerceIn(
                        -MAX_SINK_SPEED, PlayerConstants.SWIM_UP_SPEED)
                else -> vy + tuning.gravity * dt
            }
        val dy = newVy * dt
        val resolvedDy = AabbCollider.resolveY(solid, cx, cy, cz, w, h, dy)
        return Fall((cy + resolvedDy).coerceAtLeast(0f), if (resolvedDy != dy) 0f else newVy)
    }

    private fun Vec3.blockCoords(yOffset: Float) =
        Triple(floor(x).toInt(), floor(y + yOffset).toInt(), floor(z).toInt())

    private fun BlockQuery.isLiquid(c: Triple<Int, Int, Int>) = isLiquid(c.first, c.second, c.third)

    private fun BlockQuery.liquidSlowdown(c: Triple<Int, Int, Int>) =
        liquidSlowdown(c.first, c.second, c.third)
}
