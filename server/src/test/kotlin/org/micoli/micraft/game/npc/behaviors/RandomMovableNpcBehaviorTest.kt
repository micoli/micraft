package org.micoli.micraft.game.npc.behaviors

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.WanderPhase
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testWorld

class RandomMovableNpcBehaviorTest {

    private fun instanceAt(
        pos: Vec3,
        wanderRadius: Float = 12f,
        wanderSpeed: Float = 3f,
        aggroRange: Float = 12f,
    ): NpcInstance {
        val def =
            NpcDefinition(
                type = "GOAT",
                behavior = RandomMovableNpcBehavior(),
                bbmodelFile = "npc",
                width = 0.5f,
                height = 0.9f,
                wanderSpeed = wanderSpeed,
                wanderRadius = wanderRadius,
                aggroRange = aggroRange,
            )
        return NpcInstance(
            state = NpcState(id = "1", name = "Goat", type = "GOAT", pos = pos, yaw = 0f),
            definition = def,
            spawnPos = pos,
        )
    }

    @Test
    fun tick_neverMovesFartherThanWanderRadiusFromSpawn() {
        val floorY = 4
        val world =
            testWorld(
                *(4..17).flatMap { x -> (4..17).map { z -> Triple(x, floorY, z) } }.toTypedArray())
        val spawn = Vec3(10.5f, (floorY + 1).toFloat(), 10.5f)
        val instance = instanceAt(spawn, wanderRadius = 5f)
        instance.vy = 0f
        repeat(500) {
            RandomMovableNpcBehavior().tick(instance, world)
            val dx = instance.state.pos.x - spawn.x
            val dz = instance.state.pos.z - spawn.z
            val dist = sqrt((dx * dx + dz * dz).toDouble())
            assertTrue(dist <= 5.5, "npc wandered too far: $dist")
        }
    }

    @Test
    fun tick_withZeroWanderRadius_staysNearSpawn() {
        val floorY = 4
        val world = testWorld(Triple(8, floorY, 8))
        val spawn = Vec3(8.5f, (floorY + 1).toFloat(), 8.5f)
        val instance = instanceAt(spawn, wanderRadius = 0f)
        instance.vy = 0f
        repeat(50) { RandomMovableNpcBehavior().tick(instance, world) }
        val dx = instance.state.pos.x - spawn.x
        val dz = instance.state.pos.z - spawn.z
        assertTrue(sqrt((dx * dx + dz * dz).toDouble()) < 0.5)
    }

    @Test
    fun tick_inAir_appliesGravityAndFalls() {
        val world = testWorld(Triple(8, 0, 8))
        val instance = instanceAt(Vec3(8.5f, 30f, 8.5f))
        RandomMovableNpcBehavior().tick(instance, world)
        assertTrue(instance.state.pos.y < 30f)
    }

    @Test
    fun tick_withChaseTarget_movesNpcTowardTarget() {
        val floorY = 4
        val world =
            testWorld(
                *(0..30).flatMap { x -> (0..30).map { z -> Triple(x, floorY, z) } }.toTypedArray())
        val spawn = Vec3(5.5f, (floorY + 1).toFloat(), 5.5f)
        val target = Vec3(15.5f, (floorY + 1).toFloat(), 5.5f)
        val instance = instanceAt(spawn, wanderRadius = 5f)
        instance.vy = 0f
        instance.chaseTargetPos = target
        val before = instance.state.pos.x
        repeat(20) { RandomMovableNpcBehavior().tick(instance, world) }
        assertTrue(instance.state.pos.x > before, "npc should move toward target")
    }

    @Test
    fun tick_pausingPhase_rotatesYawTowardLookTarget() {
        val floorY = 4
        val world = testWorld(Triple(8, floorY, 8))
        val spawn = Vec3(8.5f, (floorY + 1).toFloat(), 8.5f)
        val instance = instanceAt(spawn)
        instance.vy = 0f
        instance.wanderPhase = WanderPhase.Pausing(60, 3.0f, 1)
        val initialYaw = instance.state.yaw
        repeat(20) { RandomMovableNpcBehavior().tick(instance, world) }
        assertTrue(instance.state.yaw != initialYaw, "yaw should rotate during pause look-around")
    }

    @Test
    fun tick_movingPhase_yawInterpolatesGradually() {
        val floorY = 4
        val world =
            testWorld(
                *(0..20).flatMap { x -> (0..20).map { z -> Triple(x, floorY, z) } }.toTypedArray())
        val spawn = Vec3(5f, (floorY + 1).toFloat(), 10f)
        val instance = instanceAt(spawn, wanderSpeed = 3f)
        instance.vy = 0f
        instance.wanderPhase = WanderPhase.Moving(15f, 10f, 1f, 100)
        var anyGradualStep = false
        var prevYaw = instance.state.yaw
        repeat(10) {
            RandomMovableNpcBehavior().tick(instance, world)
            val delta = abs(instance.state.yaw - prevYaw)
            if (delta > 0f && delta <= NpcConstants.live.yawTurnSpeed + 0.001f)
                anyGradualStep = true
            prevYaw = instance.state.yaw
        }
        assertTrue(anyGradualStep, "yaw should interpolate gradually, not snap")
    }

    @Test
    fun tick_decelPhase_transitionsToPausingWhenQueueEmpty() {
        val floorY = 4
        val world = testWorld(Triple(8, floorY, 8))
        val spawn = Vec3(8.5f, (floorY + 1).toFloat(), 8.5f)
        val instance = instanceAt(spawn)
        instance.vy = 0f
        instance.wanderPhase = WanderPhase.Decel(8.5f, 8.5f, NpcConstants.live.wanderDecelTicks, 1f)
        repeat(NpcConstants.live.wanderDecelTicks + 1) {
            RandomMovableNpcBehavior().tick(instance, world)
        }
        assertTrue(instance.wanderPhase is WanderPhase.Pausing, "should enter Pausing after Decel")
    }

    @Test
    fun tick_waypointQueue_consumedBeforePause() {
        val floorY = 4
        val world =
            testWorld(
                *(0..30).flatMap { x -> (0..30).map { z -> Triple(x, floorY, z) } }.toTypedArray())
        val spawn = Vec3(15f, (floorY + 1).toFloat(), 15f)
        val instance = instanceAt(spawn, wanderRadius = 10f)
        instance.vy = 0f
        instance.wanderWaypoints.addLast(Pair(17f, 15f))
        instance.wanderPhase = WanderPhase.Decel(17f, 15f, 1, 1f)
        repeat(3) { RandomMovableNpcBehavior().tick(instance, world) }
        assertTrue(
            instance.wanderPhase is WanderPhase.Moving,
            "should pop next waypoint from queue into Moving, not Pausing")
    }

    @Test
    fun tick_withChaseTargetBeyondAggroRange_stopsAtAggroBoundary() {
        val floorY = 4
        val world =
            testWorld(
                *(0..40).flatMap { x -> (0..40).map { z -> Triple(x, floorY, z) } }.toTypedArray())
        val aggroRange = 8f
        val spawn = Vec3(10.5f, (floorY + 1).toFloat(), 10.5f)
        val farTarget = Vec3(30.5f, (floorY + 1).toFloat(), 10.5f)
        val instance = instanceAt(spawn, wanderRadius = 5f, aggroRange = aggroRange)
        instance.vy = 0f
        instance.chaseTargetPos = farTarget
        repeat(300) { RandomMovableNpcBehavior().tick(instance, world) }
        val dx = instance.state.pos.x - spawn.x
        val dz = instance.state.pos.z - spawn.z
        val dist = sqrt((dx * dx + dz * dz).toDouble())
        assertTrue(dist <= aggroRange + 0.5, "npc should not exceed aggroRange from spawn: $dist")
    }
}
