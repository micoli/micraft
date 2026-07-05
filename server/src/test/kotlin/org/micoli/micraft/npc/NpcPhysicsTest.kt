package org.micoli.micraft.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testWorld

class NpcPhysicsTest {
    private fun makeNpc(pos: Vec3, vy: Float = 0f): NpcInstance {
        val def =
            NpcDefinition(
                type = "test",
                behavior = StaticNpcBehavior(),
                bbmodelFile = "test",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 0f,
                wanderRadius = 0f,
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
    fun npc_atYZero_clampedToZero() {
        val world = testWorld()
        val npc = makeNpc(Vec3(0.3f, 0f, 0.3f), vy = -10f)
        NpcPhysics.applyGravity(npc, world)
        assertTrue(npc.state.pos.y >= 0f, "NPC Y should not go below 0")
    }
}
