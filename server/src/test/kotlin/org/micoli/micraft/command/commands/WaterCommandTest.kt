package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class WaterCommandTest {
    private val cmd = WaterCommand()

    private fun world(vararg blocks: Pair<Triple<Int, Int, Int>, BlockType>): WorldState {
        val w = WorldState(MapChunkGenerator(blocks.toMap()))
        w.getOrGenerate(ChunkPos(0, 0))
        return w
    }

    private fun liquidManager(w: WorldState) = LiquidManager(w)

    @Test
    fun noArgs_rejected() = runBlocking {
        // Client sends no coords → no_solid_target message
        val session = testSession()
        val w = world()
        val lm = liquidManager(w)
        cmd.execute(session, "", testContext(world = w, liquidManager = lm))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("solid", ignoreCase = true) })
    }

    @Test
    fun solidBelow_waterPlaced() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 3, 5) to BlockType.STONE)
        val lm = liquidManager(w)
        val broadcasts = mutableListOf<ServerMessage>()
        cmd.execute(
            session,
            "5 4 5",
            testContext(world = w, liquidManager = lm, broadcast = { broadcasts.add(it) }))
        assertEquals(BlockType.WATER, w.getBlock(5, 4, 5))
        assertTrue(broadcasts.any { it is ServerMessage.WorldUpdate })
    }

    @Test
    fun noSolidBelow_rejected() = runBlocking {
        val session = testSession()
        val w = world()
        val lm = liquidManager(w)
        cmd.execute(session, "5 4 5", testContext(world = w, liquidManager = lm))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("solid", ignoreCase = true) })
        assertEquals(BlockType.AIR, w.getBlock(5, 4, 5))
    }

    @Test
    fun liquidBelow_rejected() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 3, 5) to BlockType.WATER)
        val lm = liquidManager(w)
        cmd.execute(session, "5 4 5", testContext(world = w, liquidManager = lm))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("solid", ignoreCase = true) })
        assertEquals(BlockType.AIR, w.getBlock(5, 4, 5))
    }

    @Test
    fun targetNotAir_rejected() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 3, 5) to BlockType.STONE, Triple(5, 4, 5) to BlockType.STONE)
        val lm = liquidManager(w)
        cmd.execute(session, "5 4 5", testContext(world = w, liquidManager = lm))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("not air", ignoreCase = true) })
    }

    @Test
    fun noLiquidManager_unavailable() = runBlocking {
        val session = testSession()
        val w = world()
        cmd.execute(session, "5 4 5", testContext(world = w, liquidManager = null))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("not available", ignoreCase = true) })
    }
}
