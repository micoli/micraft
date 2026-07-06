package org.micoli.micraft.npc.behaviors

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.npc.NpcConstants
import org.micoli.micraft.npc.NpcDefinition
import org.micoli.micraft.npc.NpcInstance
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession

class InteractionableNpcBehaviorTest {

    private fun instanceAt(pos: Vec3): NpcInstance {
        val def =
            NpcDefinition(
                type = "SELLER",
                behavior = InteractionableNpcBehavior(),
                bbmodelFile = "npc",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 0f,
                wanderRadius = 0f,
            )
        return NpcInstance(
            state = NpcState(id = "npc-1", name = "Bob", type = "SELLER", pos = pos, yaw = 0f),
            definition = def,
            spawnPos = pos,
        )
    }

    @Test
    fun onInteract_withinRange_sendsInteractResult() = runBlocking {
        val instance = instanceAt(Vec3(8f, 4f, 8f))
        val session = testSession(pos = Vec3(9f, 4f, 8f))
        val sent = mutableListOf<ServerMessage>()
        InteractionableNpcBehavior().onInteract(instance, session) { sent.add(it) }
        assertTrue(sent.any { it is ServerMessage.NpcInteractResult })
    }

    @Test
    fun onInteract_outOfRange_sendsNothing() = runBlocking {
        val instance = instanceAt(Vec3(8f, 4f, 8f))
        val farPos = Vec3(8f + NpcConstants.INTERACTION_RANGE + 10f, 4f, 8f)
        val session = testSession(pos = farPos)
        val sent = mutableListOf<ServerMessage>()
        InteractionableNpcBehavior().onInteract(instance, session) { sent.add(it) }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun onInteract_payloadContainsNpcTypeAndName() = runBlocking {
        val instance = instanceAt(Vec3(8f, 4f, 8f))
        val session = testSession(pos = Vec3(8f, 4f, 8f))
        val sent = mutableListOf<ServerMessage>()
        InteractionableNpcBehavior().onInteract(instance, session) { sent.add(it) }
        val result = sent.filterIsInstance<ServerMessage.NpcInteractResult>().first()
        assertTrue(result.payload.contains("SELLER"))
        assertTrue(result.payload.contains("Bob"))
    }
}
