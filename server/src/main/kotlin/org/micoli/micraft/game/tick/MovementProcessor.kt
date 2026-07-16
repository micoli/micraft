package org.micoli.micraft.game.tick

import kotlin.math.sqrt
import org.micoli.micraft.game.FLY_VERTICAL_SPEED
import org.micoli.micraft.game.GRAVITY
import org.micoli.micraft.game.JUMP_SPEED
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.PlayerConstants
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.height
import org.micoli.micraft.player.speed
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(MovementProcessor::class.java)

class MovementProcessor(private val world: WorldState) {

    fun process(session: PlayerSession, input: TickInput): PlayerState {
        val old = session.state
        val w = PlayerConstants.WIDTH
        val solid = { bx: Int, by: Int, bz: Int -> world.getBlock(bx, by, bz).isSolid }
        val rawPos = old.pos
        val recoveredY =
            if (AabbCollider.isOverlapping(
                solid, rawPos.x, rawPos.y, rawPos.z, w, old.stance.height)) {
                val ejected =
                    AabbCollider.ejectUp(solid, rawPos.x, rawPos.y, rawPos.z, w, old.stance.height)
                log.warn(
                    "player {} stuck inside block at y={}, ejected to y={}",
                    session.id.take(8),
                    rawPos.y,
                    ejected)
                ejected
            } else rawPos.y
        val pos = rawPos.copy(y = recoveredY)

        val newSpeedMult =
            when {
                input.speedUpRequested -> (old.speedMultiplier + 0.5f).coerceAtMost(5.0f)
                input.speedDownRequested -> (old.speedMultiplier - 0.5f).coerceAtLeast(0.5f)
                else -> old.speedMultiplier
            }

        val newFlying = if (input.flyToggleRequested) !old.flying else old.flying
        if (input.flyToggleRequested && newFlying) session.vy = 0f

        var newStance =
            if (!newFlying &&
                AabbCollider.canAdoptStance(
                    solid, pos.x, pos.y, pos.z, w, input.stance.height, old.stance.height))
                input.stance
            else old.stance

        val h = newStance.height
        val feetBlock =
            world.getBlock(
                Math.floor(pos.x.toDouble()).toInt(),
                Math.floor(pos.y.toDouble()).toInt(),
                Math.floor(pos.z.toDouble()).toInt(),
            )
        val liquidSlowdown = if (feetBlock.isLiquid) 1f / (1f + feetBlock.viscosity * 0.15f) else 1f
        val speed = newStance.speed * newSpeedMult * TICK_SECONDS * liquidSlowdown

        val len = sqrt((input.dx * input.dx + input.dz * input.dz).toDouble()).toFloat()
        val nx = if (len > 0f) input.dx / len else 0f
        val nz = if (len > 0f) input.dz / len else 0f

        if (!newFlying &&
            input.jumpRequested &&
            session.vy == 0f &&
            AabbCollider.isGrounded(solid, pos.x, pos.y, pos.z, w)) {
            session.vy = JUMP_SPEED
            if (newStance != PlayerStance.STANDING) newStance = PlayerStance.STANDING
        }

        val resolvedDx = AabbCollider.resolveX(solid, pos.x, pos.y, pos.z, w, h, nx * speed)
        val midX = pos.x + resolvedDx
        val resolvedDz = AabbCollider.resolveZ(solid, midX, pos.y, pos.z, w, h, nz * speed)
        val newZ = pos.z + resolvedDz
        val newX = pos.x + AabbCollider.resolveX(solid, pos.x, pos.y, newZ, w, h, nx * speed)

        val newY =
            if (newFlying) {
                val flyDy = input.dy * FLY_VERTICAL_SPEED * newSpeedMult * TICK_SECONDS
                val resolvedDy = AabbCollider.resolveY(solid, newX, pos.y, newZ, w, h, flyDy)
                (pos.y + resolvedDy).coerceIn(0f, WorldConstants.WORLD_MAX_Y.toFloat())
            } else {
                applyGravity(session, newX, pos.y, newZ, h, feetBlock.isLiquid)
            }

        return old.copy(
            pos = Vec3(newX, newY, newZ),
            orientation = Orientation(input.yaw, input.pitch),
            stance = newStance,
            flying = newFlying,
            speedMultiplier = newSpeedMult,
            biome = world.biomeAt(newX.toInt(), newZ.toInt()),
            zoneLevel = world.zoneLevelAt(newX.toInt(), newZ.toInt()),
        )
    }

    private fun applyGravity(
        session: PlayerSession,
        cx: Float,
        cy: Float,
        cz: Float,
        h: Float,
        inLiquid: Boolean = false,
    ): Float {
        val w = PlayerConstants.WIDTH
        val solid = { bx: Int, by: Int, bz: Int -> world.getBlock(bx, by, bz).isSolid }
        if (session.vy <= 0f && AabbCollider.isGrounded(solid, cx, cy, cz, w)) {
            session.vy = 0f
            // Snap to ground surface in case the player partially sank into a block
            val snapDy = AabbCollider.resolveY(solid, cx, cy, cz, w, h, -1f)
            return (cy + snapDy).coerceAtLeast(0f)
        }
        val effectiveGravity = if (inLiquid) GRAVITY * 0.2f else GRAVITY
        session.vy += effectiveGravity * TICK_SECONDS
        if (inLiquid) session.vy = session.vy.coerceAtLeast(-2f)
        val dy = session.vy * TICK_SECONDS
        val resolvedDy = AabbCollider.resolveY(solid, cx, cy, cz, w, h, dy)
        if (resolvedDy != dy) {
            if (session.vy < 0f)
                log.debug("player {} landed at y={}", session.id.take(8), cy + resolvedDy)
            session.vy = 0f
        }
        return (cy + resolvedDy).coerceAtLeast(0f)
    }
}
