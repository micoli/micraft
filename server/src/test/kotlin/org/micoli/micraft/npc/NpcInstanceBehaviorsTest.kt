package org.micoli.micraft.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.npc.behaviors.InteractionableNpcBehavior
import org.micoli.micraft.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class NpcInstanceBehaviorsTest {
    private fun makeNpc(
        pos: Vec3 = Vec3(0.3f, 100f, 0.3f),
        behavior: NpcBehavior = StaticNpcBehavior(),
    ): NpcInstance {
        val def =
            NpcDefinition(
                type = "test",
                behavior = behavior,
                bbmodelFile = "test",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 2f,
                wanderRadius = 10f,
            )
        return NpcInstance(
            state = NpcState(id = "npc-1", name = "Test", type = "test", pos = pos, yaw = 0f),
            definition = def,
            spawnPos = pos,
        )
    }

    @Test
    fun npcInstance_initialVy_zero() {
        assertEquals(0f, makeNpc().vy)
    }

    @Test
    fun npcInstance_initialState_matchesSpawnPos() {
        val pos = Vec3(1f, 2f, 3f)
        val npc = makeNpc(pos)
        assertEquals(pos, npc.spawnPos)
        assertEquals(pos, npc.state.pos)
    }

    @Test
    fun staticBehavior_tick_doesNotThrow() {
        val npc = makeNpc()
        StaticNpcBehavior().tick(npc, testWorld())
    }

    @Test
    fun interactionableBehavior_tick_doesNotThrow() {
        val npc = makeNpc(behavior = InteractionableNpcBehavior())
        InteractionableNpcBehavior().tick(npc, testWorld())
    }

    @Test
    fun randomMovableBehavior_tick_doesNotThrow() {
        val npc = makeNpc(behavior = RandomMovableNpcBehavior())
        RandomMovableNpcBehavior().tick(npc, testWorld())
    }

    @Test
    fun staticBehavior_onInteract_sendsPayload() =
        runBlocking<Unit> {
            val npc = makeNpc(behavior = InteractionableNpcBehavior())
            val session = testSession(pos = Vec3(1f, 100f, 1f))
            val received = mutableListOf<ServerMessage>()
            InteractionableNpcBehavior().onInteract(npc, session) { received.add(it) }
            val result = received.filterIsInstance<ServerMessage.NpcInteractResult>()
            assertFalse(result.isEmpty(), "onInteract should send NpcInteractResult")
        }
}
