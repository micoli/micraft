package org.micoli.micraft.game.npc.behaviors

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcPhysics
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.Vec3

class RandomMovableNpcBehavior : NpcBehavior {

    override fun tick(instance: NpcInstance, world: WorldState): Boolean {
        var changed = NpcPhysics.applyGravity(instance, world)
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

        val speed = def.wanderSpeed * TICK_SECONDS
        val nx = dx / dist
        val nz = dz / dist
        val solid = { bx: Int, by: Int, bz: Int -> world.getBlockIfLoaded(bx, by, bz).isSolid }

        val resolvedDx =
            AabbCollider.resolveX(solid, pos.x, pos.y, pos.z, def.width, def.height, nx * speed)
        val midX = pos.x + resolvedDx
        val resolvedDz =
            AabbCollider.resolveZ(solid, midX, pos.y, pos.z, def.width, def.height, nz * speed)
        val newZ = pos.z + resolvedDz
        val newX = pos.x + AabbCollider.resolveX(solid, pos.x, pos.y, newZ, def.width, def.height, nx * speed)

        if ((newX - pos.x) == 0f && resolvedDz == 0f) {
            if (instance.vy == 0f &&
                AabbCollider.isGrounded(solid, pos.x, pos.y, pos.z, def.width)) {
                val rdxAbove =
                    AabbCollider.resolveX(
                        solid, pos.x, pos.y + 1f, pos.z, def.width, def.height, nx * speed)
                val rdzAbove =
                    AabbCollider.resolveZ(
                        solid,
                        pos.x + rdxAbove,
                        pos.y + 1f,
                        pos.z,
                        def.width,
                        def.height,
                        nz * speed)
                if (rdxAbove != 0f || rdzAbove != 0f) {
                    instance.vy = NpcConstants.JUMP_VELOCITY
                    return true
                }
            }
            if (instance.state.vel.x != 0f || instance.state.vel.z != 0f) {
                instance.velocity = Vec3(0f, instance.velocity.y, 0f)
                instance.state = instance.state.copy(vel = instance.velocity)
                return true
            }
            return false
        }

        val newYaw = atan2(nx.toDouble(), nz.toDouble()).toFloat()
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

    private fun tickWander(instance: NpcInstance, world: WorldState): Boolean {
        val def = instance.definition
        val pos = instance.state.pos

        if (instance.wanderPauseTicks > 0) {
            instance.wanderPauseTicks--
            if (instance.state.vel.x != 0f || instance.state.vel.z != 0f) {
                instance.velocity = Vec3(0f, instance.velocity.y, 0f)
                instance.state = instance.state.copy(vel = instance.velocity)
                return true
            }
            return false
        }

        if (instance.wanderStepTicks <= 0) {
            instance.wanderStepTicks = Random.nextInt(1, NpcConstants.WANDER_STEP_TICKS_MAX + 1)
            pickNewTarget(instance)
        }

        val dx = instance.wanderTargetX - pos.x
        val dz = instance.wanderTargetZ - pos.z
        val dist = sqrt((dx * dx + dz * dz).toDouble()).toFloat()

        if (dist < 0.2f) {
            instance.wanderStepTicks = 0
            instance.wanderPauseTicks =
                Random.nextInt(
                    NpcConstants.WANDER_PAUSE_TICKS_MIN, NpcConstants.WANDER_PAUSE_TICKS_MAX + 1)
            return false
        }

        val speed = def.wanderSpeed * TICK_SECONDS
        val nx = dx / dist
        val nz = dz / dist
        val solid = { bx: Int, by: Int, bz: Int -> world.getBlockIfLoaded(bx, by, bz).isSolid }

        val resolvedDx =
            AabbCollider.resolveX(solid, pos.x, pos.y, pos.z, def.width, def.height, nx * speed)
        val midX = pos.x + resolvedDx
        val resolvedDz =
            AabbCollider.resolveZ(solid, midX, pos.y, pos.z, def.width, def.height, nz * speed)
        val newZ = pos.z + resolvedDz
        val newX = pos.x + AabbCollider.resolveX(solid, pos.x, pos.y, newZ, def.width, def.height, nx * speed)

        if ((newX - pos.x) == 0f && resolvedDz == 0f) {
            if (instance.vy == 0f &&
                AabbCollider.isGrounded(solid, pos.x, pos.y, pos.z, def.width)) {
                val rdxAbove =
                    AabbCollider.resolveX(
                        solid, pos.x, pos.y + 1f, pos.z, def.width, def.height, nx * speed)
                val rdzAbove =
                    AabbCollider.resolveZ(
                        solid,
                        pos.x + rdxAbove,
                        pos.y + 1f,
                        pos.z,
                        def.width,
                        def.height,
                        nz * speed)
                if (rdxAbove != 0f || rdzAbove != 0f) {
                    instance.vy = NpcConstants.JUMP_VELOCITY
                    instance.wanderStepTicks--
                    return true
                }
            }
            pickNewTarget(instance)
            instance.wanderStepTicks--
            if (instance.state.vel.x != 0f || instance.state.vel.z != 0f) {
                instance.velocity = Vec3(0f, instance.velocity.y, 0f)
                instance.state = instance.state.copy(vel = instance.velocity)
                return true
            }
            return false
        }

        instance.wanderStepTicks--
        val newYaw = atan2(nx.toDouble(), nz.toDouble()).toFloat()
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

    private fun pickNewTarget(instance: NpcInstance) {
        val radius = instance.definition.wanderRadius
        val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
        val r = Random.nextFloat() * radius
        instance.wanderTargetX = instance.spawnPos.x + r * cos(angle)
        instance.wanderTargetZ = instance.spawnPos.z + r * sin(angle)
    }
}
