package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testWorld

class NpcPhysicsTest {
    private fun makeNpc(
        pos: Vec3,
        vy: Float = 0f,
        movementMode: List<MovementMode> = listOf(MovementMode.WALKING),
    ): NpcInstance {
        val def =
            NpcDefinition(
                type = "test",
                behavior = StaticNpcBehavior(),
                bbmodelFile = "test",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 0f,
                wanderRadius = 0f,
                movementMode = movementMode,
            )
        val state = NpcState(id = "test-npc", name = "Test", type = "test", pos = pos, yaw = 0f)
        return NpcInstance(state = state, vy = vy, definition = def, spawnPos = pos)
    }

    @Test
    fun groundedNpc_onSolidFloor_snapsToGround() {
        // Stone block at y=4, NPC floating just above at y=5
        val world = testWorld(Triple(0, 4, 0), Triple(1, 4, 0), Triple(0, 4, 1), Triple(1, 4, 1))
        val npc = makeNpc(Vec3(0.5f, 5.0f, 0.5f), vy = 0f)
        NpcPhysics.applyGravity(npc, world)
        assertEquals(0f, npc.vy)
    }

    @Test
    fun fallingNpc_inAir_accumulatesVelocity() {
        val world = testWorld()
        val npc = makeNpc(Vec3(0.3f, 100f, 0.3f), vy = 0f)
        NpcPhysics.applyGravity(npc, world)
        assertTrue(npc.vy != 0f, "Gravity should change vy for airborne NPC")
    }

    @Test
    fun fallingNpc_movesDown() {
        val world = testWorld()
        val startY = 100f
        val npc = makeNpc(Vec3(0.3f, startY, 0.3f), vy = 0f)
        NpcPhysics.applyGravity(npc, world)
        assertTrue(npc.state.pos.y <= startY, "Airborne NPC should not move up")
    }

    @Test
    fun flyingNpc_holdsAltitude_doesNotFall() {
        val world = testWorld(Triple(0, 4, 0), Triple(1, 4, 0), Triple(0, 4, 1), Triple(1, 4, 1))
        val npc = makeNpc(Vec3(0.5f, 40f, 0.5f), movementMode = listOf(MovementMode.FLYING))
        // NpcInstance.init flips `flying` on for a FLYING definition.
        assertTrue(npc.flying)
        val moved = NpcPhysics.applyGravity(npc, world)
        assertEquals(0f, npc.vy)
        assertEquals(40f, npc.state.pos.y)
        assertTrue(!moved)
    }

    @Test
    fun cruise_climbsTowardCruiseHeightAboveGround() {
        val world = testWorld(Triple(0, 4, 0), Triple(1, 4, 0), Triple(0, 4, 1), Triple(1, 4, 1))
        // Ground top at y=5; cruise height 8 → target y ≈ 13.
        val npc = makeNpc(Vec3(0.5f, 5f, 0.5f), movementMode = listOf(MovementMode.FLYING))
        repeat(120) { NpcPhysics.cruise(npc, world, cruiseHeight = 8f, step = 0.4f) }
        assertEquals(13f, npc.state.pos.y, 1f)
    }

    @Test
    fun npc_atYZero_clampedToZero() {
        val world = testWorld()
        val npc = makeNpc(Vec3(0.3f, 0f, 0.3f), vy = -10f)
        NpcPhysics.applyGravity(npc, world)
        assertTrue(npc.state.pos.y >= 0f, "NPC Y should not go below 0")
    }
}
