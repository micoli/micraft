package org.micoli.micraft.plugins.npc

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.InteractionableNpcBehavior
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

private fun testDefs(): Map<String, NpcDefinition> =
    mapOf(
        "SELLER" to
            NpcDefinition(
                type = "SELLER",
                behavior = InteractionableNpcBehavior(),
                bbmodelFile = "npc",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 0f,
                wanderRadius = 0f,
            ),
        "GOAT" to
            NpcDefinition(
                type = "GOAT",
                behavior = RandomMovableNpcBehavior(),
                bbmodelFile = "npc",
                width = 0.5f,
                height = 0.9f,
                wanderSpeed = 2f,
                wanderRadius = 8f,
            ),
    )

private fun testNpcManager(): Pair<NpcManager, MutableList<ServerMessage>> {
    val broadcasts = mutableListOf<ServerMessage>()
    val m = NpcManager(broadcast = { broadcasts.add(it) })
    m.loadDefinitions(testDefs())
    return m to broadcasts
}

class NpcCommandTest {
    private val cmd = NpcCommand()

    @Test
    fun spawn_validType_spawnsAndNotifies() = runBlocking {
        val (m, broadcasts) = testNpcManager()
        val session = testSession()
        cmd.execute(session, "spawn SELLER Bob the Seller", testContext(npcManager = m))
        assertTrue(broadcasts.any { it is ServerMessage.NpcSpawned })
        assertTrue(session.sent.any { it is ServerMessage.Notification })
    }

    @Test
    fun spawn_unknownType_sendsError() = runBlocking {
        val (m, _) = testNpcManager()
        val session = testSession()
        cmd.execute(session, "spawn UNICORN Sparky", testContext(npcManager = m))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notif.any {
                it.message.contains("UNICORN") ||
                    it.message.contains("Unknown") ||
                    it.message.contains("unknown")
            })
    }

    @Test
    fun spawn_noArgs_sendsUsage() = runBlocking {
        val (m, _) = testNpcManager()
        val session = testSession()
        cmd.execute(session, "spawn", testContext(npcManager = m))
        assertTrue(session.sent.any { it is ServerMessage.Notification })
    }

    @Test
    fun list_noNpcs_sendsEmpty() = runBlocking {
        val (m, _) = testNpcManager()
        val session = testSession()
        cmd.execute(session, "list", testContext(npcManager = m))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("0") })
    }

    @Test
    fun remove_validId_despawns() = runBlocking {
        val (m, broadcasts) = testNpcManager()
        val instance = m.spawnNpc("Bob", "SELLER", Vec3(0f, 0f, 0f))
        broadcasts.clear()
        val session = testSession()
        cmd.execute(session, "remove ${instance.state.id}", testContext(npcManager = m))
        assertTrue(broadcasts.any { it is ServerMessage.NpcDespawned })
        assertTrue(session.sent.any { it is ServerMessage.Notification })
    }

    @Test
    fun remove_unknownId_sendsNotFound() = runBlocking {
        val (m, _) = testNpcManager()
        val session = testSession()
        cmd.execute(session, "remove nonexistent-id-xyz", testContext(npcManager = m))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notif.any {
                it.message.contains("not found") ||
                    it.message.contains("Not found") ||
                    it.message.contains("introuvable")
            })
    }

    @Test
    fun unknownSubcommand_sendsUsage() = runBlocking {
        val (m, _) = testNpcManager()
        val session = testSession()
        cmd.execute(session, "foobar", testContext(npcManager = m))
        assertTrue(session.sent.any { it is ServerMessage.Notification })
    }
}
