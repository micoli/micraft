package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class ExplodeCommandTest {
    private val cmd = ExplodeCommand()

    private fun world(vararg blocks: Pair<Triple<Int, Int, Int>, BlockType>): WorldState {
        val w = WorldState(MapChunkGenerator(blocks.toMap()))
        w.getOrGenerate(ChunkPos(0, 0))
        return w
    }

    @Test
    fun noArgs_sendsUsageNotification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("explode", ignoreCase = true) })
    }

    @Test
    fun radiusTooLarge_sendsErrorNotification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "99", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("max", ignoreCase = true) })
    }

    @Test
    fun radius1_blocksWithinSphereRemoved() = runBlocking {
        // player at (8,8,8), radius 1 → 7 blocks (center + 6 face-adjacent)
        val w =
            world(
                Triple(8, 8, 8) to BlockType.STONE,
                Triple(9, 8, 8) to BlockType.STONE,
                Triple(8, 9, 8) to BlockType.STONE,
                Triple(8, 8, 9) to BlockType.STONE,
            )
        val session = testSession(pos = Vec3(8f, 8f, 8f))
        cmd.execute(session, "1", testContext(world = w))
        assertEquals(BlockType.AIR, w.getBlock(8, 8, 8))
        assertEquals(BlockType.AIR, w.getBlock(9, 8, 8))
        assertEquals(BlockType.AIR, w.getBlock(8, 9, 8))
        assertEquals(BlockType.AIR, w.getBlock(8, 8, 9))
    }

    @Test
    fun blockOutsideSphere_notTouched() = runBlocking {
        val w = world(Triple(12, 8, 8) to BlockType.STONE) // distance=4 from (8,8,8)
        val session = testSession(pos = Vec3(8f, 8f, 8f))
        cmd.execute(session, "1", testContext(world = w))
        assertEquals(BlockType.STONE, w.getBlock(12, 8, 8))
    }

    @Test
    fun explode_broadcastsWorldUpdate() = runBlocking {
        val w = world(Triple(8, 8, 8) to BlockType.STONE)
        val broadcasts = mutableListOf<ServerMessage>()
        val session = testSession(pos = Vec3(8f, 8f, 8f))
        cmd.execute(session, "1", testContext(world = w, broadcast = { broadcasts.add(it) }))
        assertTrue(broadcasts.any { it is ServerMessage.WorldUpdate })
    }

    @Test
    fun explode_sendsDoneNotificationWithCount() = runBlocking {
        val w =
            world(
                Triple(8, 8, 8) to BlockType.STONE,
                Triple(9, 8, 8) to BlockType.STONE,
            )
        val session = testSession(pos = Vec3(8f, 8f, 8f))
        cmd.execute(session, "1", testContext(world = w))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.any { it.message.contains("2") })
    }

    @Test
    fun allAir_broadcastsEmptyUpdate() = runBlocking {
        val w = world() // no blocks placed
        val broadcasts = mutableListOf<ServerMessage>()
        val session = testSession(pos = Vec3(8f, 8f, 8f))
        cmd.execute(session, "1", testContext(world = w, broadcast = { broadcasts.add(it) }))
        val update = broadcasts.filterIsInstance<ServerMessage.WorldUpdate>().firstOrNull()
        assertTrue(update != null && update.changes.isEmpty())
    }
}
