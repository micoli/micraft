package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.tick.LiquidManager
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldState

class PumpCommandTest {
    private val cmd = PumpCommand()

    private fun world(vararg blocks: Pair<Triple<Int, Int, Int>, BlockType>): WorldState {
        val w = WorldState(MapChunkGenerator(blocks.toMap()))
        w.getOrGenerate(ChunkPos(0, 0))
        return w
    }

    @Test
    fun noArgs_sendsNoTargetNotification() = runBlocking {
        val session = testSession()
        val w = world()
        cmd.execute(session, "", testContext(world = w))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("liquid", ignoreCase = true) })
    }

    @Test
    fun solidTarget_sendsNoTargetNotification() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 5, 5) to BlockType.STONE)
        cmd.execute(session, "5 5 5", testContext(world = w))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("liquid", ignoreCase = true) })
        assertEquals(BlockType.STONE, w.getBlock(5, 5, 5))
    }

    @Test
    fun liquidTarget_blockRemovedAndBroadcast() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 5, 5) to BlockType.WATER)
        val broadcasts = mutableListOf<ServerMessage>()
        cmd.execute(session, "5 5 5", testContext(world = w, broadcast = { broadcasts.add(it) }))
        assertEquals(BlockType.AIR, w.getBlock(5, 5, 5))
        assertTrue(broadcasts.any { it is ServerMessage.WorldUpdate })
    }

    @Test
    fun connectedLiquids_allRemoved() = runBlocking {
        val session = testSession()
        val w =
            world(
                Triple(5, 5, 5) to BlockType.WATER,
                Triple(6, 5, 5) to BlockType.WATER,
                Triple(7, 5, 5) to BlockType.WATER,
            )
        cmd.execute(session, "5 5 5", testContext(world = w))
        assertEquals(BlockType.AIR, w.getBlock(5, 5, 5))
        assertEquals(BlockType.AIR, w.getBlock(6, 5, 5))
        assertEquals(BlockType.AIR, w.getBlock(7, 5, 5))
    }

    @Test
    fun nonLiquidAdjacent_notTouched() = runBlocking {
        val session = testSession()
        val w =
            world(
                Triple(5, 5, 5) to BlockType.WATER,
                Triple(6, 5, 5) to BlockType.STONE,
            )
        cmd.execute(session, "5 5 5", testContext(world = w))
        assertEquals(BlockType.AIR, w.getBlock(5, 5, 5))
        assertEquals(BlockType.STONE, w.getBlock(6, 5, 5))
    }

    @Test
    fun liquidTarget_deactivatesLiquidManager() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 5, 5) to BlockType.WATER)
        val lm = LiquidManager(w)
        lm.activate(BlockPos(5, 5, 5), 0)
        cmd.execute(session, "5 5 5", testContext(world = w, liquidManager = lm))
        val postPumpBroadcasts = mutableListOf<ServerMessage>()
        lm.tick { postPumpBroadcasts.add(it) }
        assertTrue(
            postPumpBroadcasts.filterIsInstance<ServerMessage.WorldUpdate>().isEmpty(),
            "LiquidManager should not re-spread removed water")
    }

    @Test
    fun liquidTarget_sendsDoneNotification() = runBlocking {
        val session = testSession()
        val w = world(Triple(5, 5, 5) to BlockType.WATER)
        cmd.execute(session, "5 5 5", testContext(world = w))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("1") && it.message.contains("block", ignoreCase = true)
            })
    }
}
