package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class SpawnCommandTest {
    private val cmd = SpawnCommand()

    private val goatDef =
        NpcDefinition(
            type = "GOAT",
            behavior = StaticNpcBehavior(),
            bbmodelFile = "npc_goat",
            width = 0.6f,
            height = 1.8f,
            wanderSpeed = 0f,
            wanderRadius = 0f,
        )

    private fun world(vararg blocks: Pair<Triple<Int, Int, Int>, BlockType>): WorldState {
        val w = WorldState(MapChunkGenerator(blocks.toMap()))
        w.getOrGenerate(ChunkPos(0, 0))
        return w
    }

    private fun npcManager(vararg defs: NpcDefinition): NpcManager {
        val broadcasts = mutableListOf<ServerMessage>()
        val m = NpcManager(broadcast = { broadcasts.add(it) })
        m.loadDefinitions(defs.associateBy { it.type })
        return m
    }

    @Test
    fun noArgs_rejected() = runBlocking {
        val session = testSession()
        val w = world()
        val nm = npcManager(goatDef)
        cmd.execute(session, "", testContext(world = w, npcManager = nm))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("solid", ignoreCase = true) })
    }

    @Test
    fun noNpcManager_unavailable() = runBlocking {
        val session = testSession()
        val w = world()
        cmd.execute(session, "GOAT 5 4 5", testContext(world = w, npcManager = null))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("not available", ignoreCase = true) })
    }

    @Test
    fun unknownModel_rejected() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 3, 5) to BlockType.STONE)
        val nm = npcManager(goatDef)
        cmd.execute(session, "DRAGON 5 4 5", testContext(world = w, npcManager = nm))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("dragon", ignoreCase = true) })
    }

    @Test
    fun solidBelow_npcSpawned() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 3, 5) to BlockType.STONE)
        val broadcasts = mutableListOf<ServerMessage>()
        val nm = NpcManager(broadcast = { broadcasts.add(it) })
        nm.loadDefinitions(mapOf("GOAT" to goatDef))
        cmd.execute(
            session,
            "GOAT 5 4 5",
            testContext(world = w, npcManager = nm, broadcast = { broadcasts.add(it) }))
        assertTrue(broadcasts.any { it is ServerMessage.NpcSpawned })
        val spawned = nm.getAll().firstOrNull()
        assertEquals("GOAT", spawned?.state?.type)
        assertEquals(5.5f, spawned?.state?.pos?.x)
        assertEquals(4f, spawned?.state?.pos?.y)
        assertEquals(5.5f, spawned?.state?.pos?.z)
    }

    @Test
    fun noSolidBelow_rejected() = runBlocking {
        val session = testSession()
        val w = world()
        val nm = npcManager(goatDef)
        cmd.execute(session, "GOAT 5 4 5", testContext(world = w, npcManager = nm))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("solid", ignoreCase = true) })
        assertEquals(0, nm.getAll().size)
    }

    @Test
    fun targetNotAir_rejected() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 3, 5) to BlockType.STONE, Triple(5, 4, 5) to BlockType.STONE)
        val nm = npcManager(goatDef)
        cmd.execute(session, "GOAT 5 4 5", testContext(world = w, npcManager = nm))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("not air", ignoreCase = true) })
        assertEquals(0, nm.getAll().size)
    }

    @Test
    fun completeArg_returnsKnownTypes() = runBlocking {
        val session = testSession()
        val w = world()
        val nm = npcManager(goatDef)
        val completions = cmd.completeArg(0, "g", session, testContext(world = w, npcManager = nm))
        assertTrue(completions.any { it.equals("goat", ignoreCase = true) })
    }
}
