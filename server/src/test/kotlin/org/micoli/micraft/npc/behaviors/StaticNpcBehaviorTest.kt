package org.micoli.micraft.npc.behaviors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.npc.NpcDefinition
import org.micoli.micraft.npc.NpcInstance
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testWorld

class StaticNpcBehaviorTest {

    private fun instanceAt(pos: Vec3): NpcInstance {
        val def =
            NpcDefinition(
                type = "SELLER",
                behavior = StaticNpcBehavior(),
                bbmodelFile = "npc",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 0f,
                wanderRadius = 0f,
            )
        return NpcInstance(
            state = NpcState(id = "1", name = "Bob", type = "SELLER", pos = pos, yaw = 0f),
            definition = def,
            spawnPos = pos,
        )
    }

    @Test
    fun tick_groundedNpc_doesNotMove() {
        val floorY = 4
        val world = testWorld(Triple(8, floorY, 8))
        val instance = instanceAt(Vec3(8.5f, (floorY + 1).toFloat(), 8.5f))
        instance.vy = 0f
        val changed = StaticNpcBehavior().tick(instance, world)
        assertFalse(changed)
        assertEquals((floorY + 1).toFloat(), instance.state.pos.y)
    }

    @Test
    fun tick_npcInAir_fallsDueToGravity() {
        val world = testWorld(Triple(8, 0, 8))
        val instance = instanceAt(Vec3(8.5f, 20f, 8.5f))
        val changed = StaticNpcBehavior().tick(instance, world)
        assertTrue(changed)
        assertTrue(instance.state.pos.y < 20f)
    }
}
