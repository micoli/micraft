package org.micoli.micraft.game.npc.behaviors

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcPhysics
import org.micoli.micraft.game.npc.WanderPhase
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.Vec3

class RandomMovableNpcBehavior : NpcBehavior {

    override fun tick(instance: NpcInstance, world: WorldState): Boolean {
        var changed = NpcPhysics.applyGravity(instance, world)
        val now = System.currentTimeMillis()
        val isFrozen =
            instance.activeEffects.any {
                (it.effect is StatusEffect.Frozen || it.effect is StatusEffect.FrozenInTime) &&
                    it.expiresAtMs > now
            }
        if (isFrozen) return changed
        val chaseTarget = instance.chaseTargetPos
        changed =
            if (chaseTarget != null) tickChase(instance, world, chaseTarget) || changed
            else tickWander(instance, world) || changed
        return changed
    }

    private fun tickChase(instance: NpcInstance, world: WorldState, targetPos: Vec3): Boolean {
        val def = instance.definition
        val pos = instance.state.pos
        val sp = instance.spawnPos

        val dtx = targetPos.x - sp.x
        val dtz = targetPos.z - sp.z
        val distFromSpawnSq = dtx * dtx + dtz * dtz
        val effectiveTarget =
            if (distFromSpawnSq > def.aggroRange * def.aggroRange) {
                val d = sqrt(distFromSpawnSq.toDouble()).toFloat()
                Vec3(sp.x + dtx / d * def.aggroRange, targetPos.y, sp.z + dtz / d * def.aggroRange)
            } else targetPos

        val dx = effectiveTarget.x - pos.x
        val dz = effectiveTarget.z - pos.z
        val dist = sqrt((dx * dx + dz * dz).toDouble()).toFloat()

        if (dist < 0.5f) {
            if (instance.state.vel.x != 0f || instance.state.vel.z != 0f) {
                instance.velocity = Vec3(0f, instance.velocity.y, 0f)
                instance.state = instance.state.copy(vel = instance.velocity)
                return true
            }
            return false
        }

        return applyMovement(
            instance, world, effectiveTarget.x, effectiveTarget.z, def.wanderSpeed * TICK_SECONDS)
    }

    private fun tickWander(instance: NpcInstance, world: WorldState): Boolean =
        when (val phase = instance.wanderPhase) {
            is WanderPhase.Pausing -> tickPausing(instance, phase)
            is WanderPhase.Moving -> tickMoving(instance, world, phase)
            is WanderPhase.Decel -> tickDecel(instance, world, phase)
        }

    private fun tickPausing(instance: NpcInstance, phase: WanderPhase.Pausing): Boolean {
        var changed = false
        if (instance.state.vel.x != 0f || instance.state.vel.z != 0f) {
            instance.velocity = Vec3(0f, instance.velocity.y, 0f)
            instance.state = instance.state.copy(vel = instance.velocity)
            changed = true
        }

        val newLookChangeTicks = phase.lookChangeTicks - 1
        val newLookYaw: Float
        val newChangeTicks: Int
        if (newLookChangeTicks <= 0) {
            newLookYaw = Random.nextFloat() * 2f * PI.toFloat()
            newChangeTicks = NpcConstants.LOOK_AROUND_CHANGE_TICKS
        } else {
            newLookYaw = phase.lookYaw
            newChangeTicks = newLookChangeTicks
        }

        val newYaw = lerpYaw(instance.state.yaw, newLookYaw, NpcConstants.LOOK_AROUND_SPEED)
        if (newYaw != instance.state.yaw) {
            instance.state = instance.state.copy(yaw = newYaw)
            changed = true
        }

        val newRemainingTicks = phase.remainingTicks - 1
        if (newRemainingTicks <= 0) {
            enqueueWaypointChain(instance)
        } else {
            instance.wanderPhase =
                phase.copy(
                    remainingTicks = newRemainingTicks,
                    lookYaw = newLookYaw,
                    lookChangeTicks = newChangeTicks,
                )
        }

        return changed
    }

    private fun tickMoving(
        instance: NpcInstance,
        world: WorldState,
        phase: WanderPhase.Moving
    ): Boolean {
        val pos = instance.state.pos
        val dx = phase.targetX - pos.x
        val dz = phase.targetZ - pos.z
        val dist = sqrt((dx * dx + dz * dz).toDouble()).toFloat()

        if (phase.remainingTicks <= 0 && instance.vy == 0f) {
            pickFreshWaypointChain(instance)
            return false
        }

        if (dist < 0.8f) {
            instance.wanderPhase =
                WanderPhase.Decel(
                    phase.targetX, phase.targetZ, NpcConstants.WANDER_DECEL_TICKS, phase.speedMult)
            return false
        }

        instance.wanderPhase = phase.copy(remainingTicks = phase.remainingTicks - 1)
        val speed = instance.definition.wanderSpeed * phase.speedMult * TICK_SECONDS
        return applyMovement(
            instance,
            world,
            phase.targetX,
            phase.targetZ,
            speed,
            onBlocked = { pickFreshWaypointChain(instance) })
    }

    private fun tickDecel(
        instance: NpcInstance,
        world: WorldState,
        phase: WanderPhase.Decel
    ): Boolean {
        val newRemainingTicks = phase.remainingTicks - 1
        if (newRemainingTicks <= 0) {
            if (instance.wanderWaypoints.isNotEmpty()) {
                instance.wanderPhase = popNextMoving(instance)
            } else {
                val pauseTicks =
                    Random.nextInt(
                        NpcConstants.WANDER_PAUSE_TICKS_MIN,
                        NpcConstants.WANDER_PAUSE_TICKS_MAX + 1)
                instance.wanderPhase =
                    WanderPhase.Pausing(
                        pauseTicks, instance.state.yaw, NpcConstants.LOOK_AROUND_CHANGE_TICKS)
            }
            if (instance.state.vel.x != 0f || instance.state.vel.z != 0f) {
                instance.velocity = Vec3(0f, instance.velocity.y, 0f)
                instance.state = instance.state.copy(vel = instance.velocity)
                return true
            }
            return false
        }

        instance.wanderPhase = phase.copy(remainingTicks = newRemainingTicks)
        val fraction = newRemainingTicks.toFloat() / NpcConstants.WANDER_DECEL_TICKS.toFloat()
        val speed = instance.definition.wanderSpeed * phase.speedMult * fraction * TICK_SECONDS
        return applyMovement(instance, world, phase.targetX, phase.targetZ, speed)
    }

    private fun applyMovement(
        instance: NpcInstance,
        world: WorldState,
        targetX: Float,
        targetZ: Float,
        speed: Float,
        onBlocked: (() -> Unit)? = null,
    ): Boolean {
        val def = instance.definition
        val pos = instance.state.pos
        val dx = targetX - pos.x
        val dz = targetZ - pos.z
        val dist = sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (dist < 0.01f) return false

        val nx = dx / dist
        val nz = dz / dist
        val solid = { bx: Int, by: Int, bz: Int -> world.getBlockIfLoaded(bx, by, bz).isSolid }

        val resolvedDx =
            AabbCollider.resolveX(solid, pos.x, pos.y, pos.z, def.width, def.height, nx * speed)
        val midX = pos.x + resolvedDx
        val resolvedDz =
            AabbCollider.resolveZ(solid, midX, pos.y, pos.z, def.width, def.height, nz * speed)
        val newZ = pos.z + resolvedDz
        val newX =
            pos.x +
                AabbCollider.resolveX(solid, pos.x, pos.y, newZ, def.width, def.height, nx * speed)

        val blockedX = nx != 0f && (newX - pos.x) == 0f
        val blockedZ = nz != 0f && resolvedDz == 0f

        // Jump trigger: per-axis block catches diagonal wall approaches
        if ((blockedX || blockedZ) &&
            instance.vy == 0f &&
            AabbCollider.isGrounded(solid, pos.x, pos.y, pos.z, def.width)) {
            val jumpCheckSpeed = def.wanderSpeed * TICK_SECONDS
            val rdxAbove =
                AabbCollider.resolveX(
                    solid, pos.x, pos.y + 1f, pos.z, def.width, def.height, nx * jumpCheckSpeed)
            val rdzAbove =
                AabbCollider.resolveZ(
                    solid,
                    pos.x + rdxAbove,
                    pos.y + 1f,
                    pos.z,
                    def.width,
                    def.height,
                    nz * jumpCheckSpeed)
            if (rdxAbove != 0f || rdzAbove != 0f) {
                instance.vy = NpcConstants.JUMP_VELOCITY
                return true
            }
        }

        // Truly stuck: both axes produced zero displacement
        if ((newX - pos.x) == 0f && resolvedDz == 0f) {
            if (instance.vy != 0f) return false
            onBlocked?.invoke()
            if (instance.state.vel.x != 0f || instance.state.vel.z != 0f) {
                instance.velocity = Vec3(0f, instance.velocity.y, 0f)
                instance.state = instance.state.copy(vel = instance.velocity)
                return true
            }
            return false
        }

        val targetYaw = atan2(nx.toDouble(), nz.toDouble()).toFloat()
        val newYaw = lerpYaw(instance.state.yaw, targetYaw, NpcConstants.YAW_TURN_SPEED)
        instance.velocity =
            Vec3((newX - pos.x) / TICK_SECONDS, instance.velocity.y, resolvedDz / TICK_SECONDS)
        instance.state =
            instance.state.copy(
                pos = Vec3(newX, pos.y, newZ),
                yaw = newYaw,
                vel = instance.velocity,
            )
        return true
    }

    private fun lerpYaw(current: Float, target: Float, speed: Float): Float {
        val twoPi = (2.0 * PI).toFloat()
        var delta = target - current
        while (delta > PI.toFloat()) delta -= twoPi
        while (delta < -PI.toFloat()) delta += twoPi
        return current + delta.coerceIn(-speed, speed)
    }

    private fun enqueueWaypointChain(instance: NpcInstance) {
        val n =
            Random.nextInt(
                NpcConstants.WANDER_WAYPOINT_COUNT_MIN, NpcConstants.WANDER_WAYPOINT_COUNT_MAX + 1)
        var angle = instance.state.yaw
        val radius = instance.definition.wanderRadius
        val spread = PI.toFloat() * 1.5f
        repeat(n) {
            angle += Random.nextFloat() * spread - spread / 2f
            val r = Random.nextFloat() * radius
            instance.wanderWaypoints.addLast(
                Pair(
                    instance.spawnPos.x + r * sin(angle),
                    instance.spawnPos.z + r * cos(angle),
                ))
        }
        instance.wanderPhase = popNextMoving(instance)
    }

    private fun pickFreshWaypointChain(instance: NpcInstance) {
        val n =
            Random.nextInt(
                NpcConstants.WANDER_WAYPOINT_COUNT_MIN, NpcConstants.WANDER_WAYPOINT_COUNT_MAX + 1)
        val radius = instance.definition.wanderRadius
        repeat(n) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val r = Random.nextFloat() * radius
            instance.wanderWaypoints.addLast(
                Pair(
                    instance.spawnPos.x + r * sin(angle),
                    instance.spawnPos.z + r * cos(angle),
                ))
        }
        instance.wanderPhase = popNextMoving(instance)
    }

    private fun popNextMoving(instance: NpcInstance): WanderPhase.Moving {
        val (tx, tz) = instance.wanderWaypoints.removeFirst()
        val speedMult =
            Random.nextFloat() *
                (NpcConstants.WANDER_SPEED_MULT_MAX - NpcConstants.WANDER_SPEED_MULT_MIN) +
                NpcConstants.WANDER_SPEED_MULT_MIN
        return WanderPhase.Moving(tx, tz, speedMult, NpcConstants.WANDER_STEP_TICKS_MAX)
    }
}
