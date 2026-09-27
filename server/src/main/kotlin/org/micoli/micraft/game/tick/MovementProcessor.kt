package org.micoli.micraft.game.tick

import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.kinematicTuning
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BreathConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.physics.BlockQuery
import org.micoli.micraft.physics.KinematicState
import org.micoli.micraft.physics.MoveIntent
import org.micoli.micraft.physics.PlayerKinematics
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(MovementProcessor::class.java)

class MovementProcessor(private val world: WorldState) {
    private val blocks =
        object : BlockQuery {
            override fun isSolid(x: Int, y: Int, z: Int) = world.isSolidOrOccupied(x, y, z)

            override fun isLiquid(x: Int, y: Int, z: Int) = world.getBlock(x, y, z).isLiquid

            override fun liquidSlowdown(x: Int, y: Int, z: Int) =
                world.getBlock(x, y, z).liquidSlowdown
        }

    fun process(session: PlayerSession, input: TickInput): PlayerState {
        val old = session.state

        // Riding a vehicle: translation is driven by VehicleManager.tick() instead, but the
        // camera's yaw/pitch keeps flowing through every tick so look stays free while mounted.
        if (session.mountedVehicleId != null) {
            session.vy = 0f
            return old.copy(orientation = Orientation(input.yaw, input.pitch))
        }

        val result =
            PlayerKinematics.step(
                KinematicState(old.pos, session.vy, old.stance, old.flying, old.speedMultiplier),
                MoveIntent(
                    dx = input.dx,
                    dz = input.dz,
                    dy = input.dy,
                    stance = input.stance,
                    jump = input.jumpRequested,
                    flyToggle = input.flyToggleRequested,
                    speedUp = input.speedUpRequested,
                    speedDown = input.speedDownRequested),
                TICK_SECONDS,
                blocks,
                kinematicTuning())
        result.ejectedFromY?.let {
            log.warn("player {} stuck inside block at y={}, ejected upward", session.id.take(8), it)
        }
        val next = result.state
        session.vy = next.vy
        val newHeadInLiquid = result.headInLiquid

        val newBreath =
            when {
                old.godMode -> old.maxBreath
                newHeadInLiquid ->
                    (old.currentBreath - BreathConstants.DRAIN_PER_TICK).coerceAtLeast(0)
                else ->
                    (old.currentBreath + BreathConstants.REFILL_PER_TICK).coerceAtMost(
                        old.maxBreath)
            }

        return old.copy(
            pos = next.pos,
            orientation = Orientation(input.yaw, input.pitch),
            stance = next.stance,
            flying = next.flying,
            speedMultiplier = next.speedMultiplier,
            biome = world.biomeAt(next.pos.x.toInt(), next.pos.z.toInt()),
            headInLiquid = newHeadInLiquid,
            currentBreath = newBreath,
            zoneLevel = world.zoneLevelAt(next.pos.x.toInt(), next.pos.z.toInt()),
        )
    }
}
