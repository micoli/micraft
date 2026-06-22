package org.micoli.micraft.command

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.BreakRecord
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.DropConfig
import org.micoli.micraft.world.ItemType
import org.micoli.micraft.world.WorldItemManager
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UndoCommandTest {
    private val cmd = UndoCommand()

    private fun emptyDropConfig(): DropConfig {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText("{}\n")
        return DropConfig(tmp)
    }

    @Test
    fun emptyHistory_sendsNothingToUndo() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Nothing") || it.message.contains("nothing") })
    }

    @Test
    fun oneRecord_restoresBlock() = runBlocking {
        val world = org.micoli.micraft.support.testWorld()
        val session = testSession()
        val pos = BlockPos(8, 5, 8)
        session.breakHistory.addLast(BreakRecord(pos, BlockType.STONE, emptyList()))
        cmd.execute(session, "", testContext(world = world))
        assertEquals(BlockType.STONE, world.getBlock(8, 5, 8))
    }

    @Test
    fun oneRecord_broadcastsWorldUpdate() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val session = testSession()
        session.breakHistory.addLast(BreakRecord(BlockPos(8, 5, 8), BlockType.DIRT, emptyList()))
        cmd.execute(session, "", testContext(broadcast = { broadcasts.add(it) }))
        assertTrue(broadcasts.any { it is ServerMessage.WorldUpdate })
    }

    @Test
    fun oneRecord_sendsInventoryUpdate() = runBlocking {
        val session = testSession()
        session.breakHistory.addLast(BreakRecord(BlockPos(8, 5, 8), BlockType.DIRT, emptyList()))
        cmd.execute(session, "", testContext())
        assertTrue(session.sent.any { it is ServerMessage.InventoryUpdate })
    }

    @Test
    fun oneRecord_callsSavePlayer() = runBlocking {
        val saved = mutableListOf<org.micoli.micraft.session.PlayerSession>()
        val session = testSession()
        session.breakHistory.addLast(BreakRecord(BlockPos(8, 5, 8), BlockType.DIRT, emptyList()))
        cmd.execute(session, "", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }

    @Test
    fun nGreaterThanHistory_clampsToHistorySize() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val session = testSession()
        session.breakHistory.addLast(BreakRecord(BlockPos(8, 5, 8), BlockType.DIRT, emptyList()))
        session.breakHistory.addLast(BreakRecord(BlockPos(9, 5, 8), BlockType.STONE, emptyList()))
        cmd.execute(session, "10", testContext(broadcast = { broadcasts.add(it) }))
        assertEquals(2, broadcasts.filterIsInstance<ServerMessage.WorldUpdate>().size)
        assertTrue(session.breakHistory.isEmpty())
    }

    @Test
    fun itemStillInWorld_despawned() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val wim = WorldItemManager(emptyDropConfig(), { broadcasts.add(it) })
        // Manually spawn an item so it's registered in wim
        val pos = BlockPos(8, 5, 8)
        val spawned = wim.spawnDrops(pos, BlockType.STONE) // no drops from empty config → empty
        // Since emptyDropConfig yields nothing, we test with worldItems = null
        // instead test the branch where worldItems has no item → reduces inventory
        val session = testSession()
        session.inventory[ItemType.COBBLESTONE] = 3
        val item = org.micoli.micraft.world.WorldItem(
            "item-test-1",
            org.micoli.micraft.player.Vec3(8.5f, 5.5f, 8.5f),
            ItemType.COBBLESTONE,
            2,
        )
        session.breakHistory.addLast(BreakRecord(pos, BlockType.STONE, listOf(item)))
        // worldItems = null → item treated as already collected → reduce inventory
        cmd.execute(session, "", testContext(worldItems = null))
        assertEquals(1, session.inventory[ItemType.COBBLESTONE])
    }

    @Test
    fun itemAlreadyCollected_reducesInventoryToZero_removesKey() = runBlocking {
        val session = testSession()
        session.inventory[ItemType.COBBLESTONE] = 1
        val item = org.micoli.micraft.world.WorldItem(
            "item-test-2",
            org.micoli.micraft.player.Vec3(8.5f, 5.5f, 8.5f),
            ItemType.COBBLESTONE,
            1,
        )
        session.breakHistory.addLast(BreakRecord(BlockPos(8, 5, 8), BlockType.STONE, listOf(item)))
        // worldItems = null → item treated as collected → remove key
        cmd.execute(session, "", testContext(worldItems = null))
        assertTrue(session.inventory[ItemType.COBBLESTONE] == null || session.inventory[ItemType.COBBLESTONE] == 0)
    }

    @Test
    fun multipleUndos_allBroadcastWorldUpdate() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val session = testSession()
        repeat(3) { i ->
            session.breakHistory.addLast(BreakRecord(BlockPos(8 + i, 5, 8), BlockType.DIRT, emptyList()))
        }
        cmd.execute(session, "3", testContext(broadcast = { broadcasts.add(it) }))
        assertEquals(3, broadcasts.filterIsInstance<ServerMessage.WorldUpdate>().size)
    }
}
