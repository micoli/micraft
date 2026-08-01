package org.micoli.micraft.game.npc.behaviors

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.npc.NpcBehavior
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcPhysics
import org.micoli.micraft.game.npc.NpcTickContext
import org.micoli.micraft.game.npc.WanderPhase
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.Vec3

private const val WAYPOINT_ATTEMPTS = 8
private const val MAX_WALK_SAMPLES = 24

class RandomMovableNpcBehavior : NpcBehavior {

    override fun tick(instance: NpcInstance, world: WorldState, ctx: NpcTickContext): Boolean {
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
            if (chaseTarget != null) tickChase(instance, world, chaseTarget, ctx) || changed
            else tickWander(instance, world, ctx) || changed
        return changed
    }

    private fun tickChase(
        instance: NpcInstance,
        world: WorldState,
        targetPos: Vec3,
        ctx: NpcTickContext,
    ): Boolean {
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
            instance,
            world,
            effectiveTarget.x,
            effectiveTarget.z,
            def.wanderSpeed * TICK_SECONDS,
            ctx)
    }

    private fun tickWander(instance: NpcInstance, world: WorldState, ctx: NpcTickContext): Boolean =
        when (val phase = instance.wanderPhase) {
            is WanderPhase.Pausing -> tickPausing(instance, world, phase, ctx)
            is WanderPhase.Moving -> tickMoving(instance, world, phase, ctx)
            is WanderPhase.Decel -> tickDecel(instance, world, phase, ctx)
        }

    private fun tickPausing(
        instance: NpcInstance,
        world: WorldState,
        phase: WanderPhase.Pausing,
        ctx: NpcTickContext,
    ): Boolean {
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
            newLookYaw = ctx.random.nextFloat() * 2f * PI.toFloat()
            newChangeTicks = ctx.tuning.lookAroundChangeTicks
        } else {
            newLookYaw = phase.lookYaw
            newChangeTicks = newLookChangeTicks
        }

        val newYaw = lerpYaw(instance.state.yaw, newLookYaw, ctx.tuning.lookAroundSpeed)
        if (newYaw != instance.state.yaw) {
            instance.state = instance.state.copy(yaw = newYaw)
            changed = true
        }

        val newRemainingTicks = phase.remainingTicks - 1
        if (newRemainingTicks <= 0) {
            enqueueWaypointChain(instance, world, ctx)
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
        phase: WanderPhase.Moving,
        ctx: NpcTickContext,
    ): Boolean {
        val pos = instance.state.pos
        val dx = phase.targetX - pos.x
        val dz = phase.targetZ - pos.z
        val dist = sqrt((dx * dx + dz * dz).toDouble()).toFloat()

        if (phase.remainingTicks <= 0 && instance.vy == 0f) {
            pickFreshWaypointChain(instance, world, ctx)
            return false
        }

        if (dist < 0.8f) {
            instance.wanderPhase =
                WanderPhase.Decel(
                    phase.targetX, phase.targetZ, ctx.tuning.wanderDecelTicks, phase.speedMult)
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
            ctx,
            onBlocked = { blockedX, blockedZ ->
                pickFreshWaypointChain(instance, world, ctx, blockedX, blockedZ)
            })
    }

    private fun tickDecel(
        instance: NpcInstance,
        world: WorldState,
        phase: WanderPhase.Decel,
        ctx: NpcTickContext,
    ): Boolean {
        val newRemainingTicks = phase.remainingTicks - 1
        if (newRemainingTicks <= 0) {
            if (instance.wanderWaypoints.isNotEmpty()) {
                instance.wanderPhase = popNextMoving(instance, ctx)
            } else {
                val pauseTicks =
                    ctx.random.nextInt(
                        ctx.tuning.wanderPauseTicksMin, ctx.tuning.wanderPauseTicksMax + 1)
                instance.wanderPhase =
                    WanderPhase.Pausing(
                        pauseTicks, instance.state.yaw, ctx.tuning.lookAroundChangeTicks)
            }
            if (instance.state.vel.x != 0f || instance.state.vel.z != 0f) {
                instance.velocity = Vec3(0f, instance.velocity.y, 0f)
                instance.state = instance.state.copy(vel = instance.velocity)
                return true
            }
            return false
        }

        instance.wanderPhase = phase.copy(remainingTicks = newRemainingTicks)
        val fraction = newRemainingTicks.toFloat() / ctx.tuning.wanderDecelTicks.toFloat()
        val speed = instance.definition.wanderSpeed * phase.speedMult * fraction * TICK_SECONDS
        return applyMovement(instance, world, phase.targetX, phase.targetZ, speed, ctx)
    }

    private fun applyMovement(
        instance: NpcInstance,
        world: WorldState,
        targetX: Float,
        targetZ: Float,
        speed: Float,
        ctx: NpcTickContext,
        onBlocked: ((blockedDirX: Float, blockedDirZ: Float) -> Unit)? = null,
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
                instance.vy = ctx.tuning.jumpVelocity
                return true
            }
        }

        // Truly stuck: both axes produced zero displacement
        if ((newX - pos.x) == 0f && resolvedDz == 0f) {
            if (instance.vy != 0f) return false
            onBlocked?.invoke(nx, nz)
            if (instance.state.vel.x != 0f || instance.state.vel.z != 0f) {
                instance.velocity = Vec3(0f, instance.velocity.y, 0f)
                instance.state = instance.state.copy(vel = instance.velocity)
                return true
            }
            return false
        }

        val targetYaw = atan2(nx.toDouble(), nz.toDouble()).toFloat()
        val newYaw = lerpYaw(instance.state.yaw, targetYaw, ctx.tuning.yawTurnSpeed)
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

    /**
     * Queue a few waypoints the NPC can actually walk to.
     *
     * Waypoints are drawn in a disc around the spawn point, and that disc happily covers walls,
     * cliffs and water. Targeting an unreachable spot makes the NPC press against the obstacle and
     * re-pick another unreachable spot, which is why mobs used to hug arena walls. Candidates are
     * therefore rejected unless a straight walk to them is clear.
     *
     * [awayFromX]/[awayFromZ], when given, is the direction the NPC was just blocked in: candidates
     * are then restricted to the opposite half-plane so it turns away instead of retrying the wall.
     */
    private fun enqueueWaypointChain(
        instance: NpcInstance,
        world: WorldState,
        ctx: NpcTickContext,
        awayFromX: Float = 0f,
        awayFromZ: Float = 0f,
        fromCurrentYaw: Boolean = true,
    ) {
        val n =
            ctx.random.nextInt(
                ctx.tuning.wanderWaypointCountMin, ctx.tuning.wanderWaypointCountMax + 1)
        var angle = instance.state.yaw
        val spread = PI.toFloat() * 1.5f
        repeat(n) {
            angle =
                if (fromCurrentYaw) angle + ctx.random.nextFloat() * spread - spread / 2f
                else ctx.random.nextFloat() * 2f * PI.toFloat()
            val waypoint = pickReachable(instance, world, ctx, angle, awayFromX, awayFromZ)
            if (waypoint != null) instance.wanderWaypoints.addLast(waypoint)
        }
        if (instance.wanderWaypoints.isEmpty()) {
            // boxed in: wait where it stands rather than grind against the obstacle
            instance.wanderPhase =
                WanderPhase.Pausing(
                    ctx.tuning.wanderPauseTicksMin,
                    instance.state.yaw,
                    ctx.tuning.lookAroundChangeTicks)
            return
        }
        instance.wanderPhase = popNextMoving(instance, ctx)
    }

    private fun pickFreshWaypointChain(
        instance: NpcInstance,
        world: WorldState,
        ctx: NpcTickContext,
        awayFromX: Float = 0f,
        awayFromZ: Float = 0f,
    ) = enqueueWaypointChain(instance, world, ctx, awayFromX, awayFromZ, fromCurrentYaw = false)

    /**
     * One reachable waypoint, or null. Tries a few directions and shrinking distances: near an
     * obstacle a shorter hop usually works even when the full radius does not.
     */
    private fun pickReachable(
        instance: NpcInstance,
        world: WorldState,
        ctx: NpcTickContext,
        preferredAngle: Float,
        awayFromX: Float,
        awayFromZ: Float,
    ): Pair<Float, Float>? {
        val radius = instance.definition.wanderRadius
        if (radius <= 0f) return null
        val turnedAway = awayFromX != 0f || awayFromZ != 0f
        val pos = instance.state.pos
        repeat(WAYPOINT_ATTEMPTS) { attempt ->
            val angle =
                if (attempt == 0 && !turnedAway) preferredAngle
                else ctx.random.nextFloat() * 2f * PI.toFloat()
            val dirX = sin(angle)
            val dirZ = cos(angle)
            // turn away from what just blocked us
            if (turnedAway && dirX * awayFromX + dirZ * awayFromZ > 0f) return@repeat
            // shrink the distance as attempts fail
            val scale = 1f - attempt.toFloat() / WAYPOINT_ATTEMPTS
            val r = (ctx.random.nextFloat() * radius * scale).coerceAtLeast(1f)
            val tx = instance.spawnPos.x + r * dirX
            val tz = instance.spawnPos.z + r * dirZ
            if (hasClearWalk(instance, world, pos.x, pos.z, tx, tz)) return Pair(tx, tz)
        }
        return null
    }

    /**
     * Can the NPC walk straight from (x1, z1) to (x2, z2)? Sampled every block, allowing a
     * one-block step up — the same climb [applyMovement] can do. There is no pathfinder, so a clear
     * straight line is the honest definition of "reachable" for a wander target.
     */
    private fun hasClearWalk(
        instance: NpcInstance,
        world: WorldState,
        x1: Float,
        z1: Float,
        x2: Float,
        z2: Float,
    ): Boolean {
        val def = instance.definition
        val solid = { bx: Int, by: Int, bz: Int -> world.getBlockIfLoaded(bx, by, bz).isSolid }
        val dx = x2 - x1
        val dz = z2 - z1
        val distance = sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (distance < 0.01f) return false
        val steps = distance.toInt().coerceIn(1, MAX_WALK_SAMPLES)
        val y = instance.state.pos.y
        for (step in 1..steps) {
            val t = step.toFloat() / steps
            val sx = x1 + dx * t
            val sz = z1 + dz * t
            val free =
                !AabbCollider.isOverlapping(solid, sx, y, sz, def.width, def.height) ||
                    !AabbCollider.isOverlapping(solid, sx, y + 1f, sz, def.width, def.height)
            if (!free) return false
        }
        return true
    }

    private fun popNextMoving(instance: NpcInstance, ctx: NpcTickContext): WanderPhase.Moving {
        val (tx, tz) = instance.wanderWaypoints.removeFirst()
        val speedMult =
            ctx.random.nextFloat() *
                (ctx.tuning.wanderSpeedMultMax - ctx.tuning.wanderSpeedMultMin) +
                ctx.tuning.wanderSpeedMultMin
        return WanderPhase.Moving(tx, tz, speedMult, ctx.tuning.wanderStepTicksMax)
    }
}
