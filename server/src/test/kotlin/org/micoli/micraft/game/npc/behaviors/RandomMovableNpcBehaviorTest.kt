package org.micoli.micraft.game.npc.behaviors

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
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
