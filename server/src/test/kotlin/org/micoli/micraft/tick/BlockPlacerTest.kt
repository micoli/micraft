package org.micoli.micraft.tick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.WorldActionRecord
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ItemType

class BlockPlacerTest {

    private fun placer(
        broadcasts: MutableList<ServerMessage> = mutableListOf(),
        saved: MutableList<org.micoli.micraft.session.PlayerSession> = mutableListOf(),
        world: org.micoli.micraft.world.WorldState = testWorld(),
    ) = BlockPlacer(world, { broadcasts.add(it) }, { saved.add(it) })

    @Test
    fun place_valid_setsBlockInWorld() = runBlocking {
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 3
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE")))
        assertEquals(BlockType.STONE, world.getBlock(8, 7, 8))
    }

    @Test
    fun place_valid_decrementsInventory() = runBlocking {
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 3
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE")))
        assertEquals(2, session.inventory[ItemType("COBBLESTONE")])
    }

    @Test
    fun place_lastItem_removesFromInventory() = runBlocking {
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE")))
        assertTrue(
            session.inventory[ItemType("COBBLESTONE")] == null ||
                session.inventory[ItemType("COBBLESTONE")] == 0)
    }

    @Test
    fun place_valid_sendsInventoryUpdate() = runBlocking {
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 2
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE")))
        assertTrue(session.sent.any { it is ServerMessage.InventoryUpdate })
    }

    @Test
    fun place_valid_broadcastsWorldUpdate() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val world = testWorld()
        val placer = placer(broadcasts = broadcasts, world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE")))
        assertTrue(broadcasts.any { it is ServerMessage.WorldUpdate })
    }

    @Test
    fun place_valid_addsToActionHistory() = runBlocking {
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE")))
        assertEquals(1, session.actionHistory.size)
        val record = session.actionHistory.last()
        assertIs<WorldActionRecord.Place>(record)
        assertEquals(ItemType("COBBLESTONE"), record.itemType)
        assertEquals(BlockPos(8, 7, 8), record.pos)
    }

    @Test
    fun place_noItemInInventory_rejected() = runBlocking {
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        // no COBBLESTONE in inventory
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE")))
        assertEquals(BlockType.AIR, world.getBlock(8, 7, 8))
        assertTrue(session.actionHistory.isEmpty())
    }

    @Test
    fun place_nonBuildableItem_rejected() = runBlocking {
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("SNOWBALL")] = 5
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("SNOWBALL")))
        assertEquals(BlockType.AIR, world.getBlock(8, 7, 8))
        assertTrue(session.actionHistory.isEmpty())
    }

    @Test
    fun place_positionOccupied_rejected() = runBlocking {
        val world = testWorld(Triple(8, 7, 8))
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE")))
        // STONE already at (8,7,8) from testWorld → stays STONE (not replaced)
        assertEquals(BlockType.STONE, world.getBlock(8, 7, 8))
        assertTrue(session.actionHistory.isEmpty())
    }

    @Test
    fun place_tooFar_rejected() = runBlocking {
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 20f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 1
        // target at y=5, player at y=20 → dist ~14.5 > 6
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 5, 8), ItemType("COBBLESTONE")))
        assertEquals(BlockType.AIR, world.getBlock(8, 5, 8))
        assertTrue(session.actionHistory.isEmpty())
    }

    @Test
    fun shortcutBarSet_valid_updatesSlotAndSends() = runBlocking {
        val saved = mutableListOf<org.micoli.micraft.session.PlayerSession>()
        val placer = placer(saved = saved)
        val session = testSession()
        val intent = ClientMessage.ShortcutBarSet(2, ShortcutSlot.Item(ItemType("COBBLESTONE")))
        placer.handleShortcutBarSet(session, intent)
        assertEquals(ShortcutSlot.Item(ItemType("COBBLESTONE")), session.shortcutBar[2])
        assertTrue(session.sent.any { it is ServerMessage.ShortcutBarUpdate })
        assertEquals(1, saved.size)
    }

    @Test
    fun shortcutBarSet_slot0_rejected() = runBlocking {
        val placer = placer()
        val session = testSession()
        placer.handleShortcutBarSet(
            session, ClientMessage.ShortcutBarSet(0, ShortcutSlot.Item(ItemType("COBBLESTONE"))))
        // slot 0 = hand slot, must stay null
        assertEquals(null, session.shortcutBar[0])
        assertTrue(session.sent.isEmpty())
    }

    @Test
    fun shortcutBarSet_nonBuildableItem_rejected() = runBlocking {
        val placer = placer()
        val session = testSession()
        placer.handleShortcutBarSet(
            session, ClientMessage.ShortcutBarSet(1, ShortcutSlot.Item(ItemType("SNOWBALL"))))
        assertEquals(null, session.shortcutBar[1])
        assertTrue(session.sent.isEmpty())
    }

    @Test
    fun shortcutBarSet_null_clearsSlot() = runBlocking {
        val placer = placer()
        val session = testSession()
        session.shortcutBar[3] = ShortcutSlot.Item(ItemType("DIRT"))
        placer.handleShortcutBarSet(session, ClientMessage.ShortcutBarSet(3, null))
        assertEquals(null, session.shortcutBar[3])
        assertTrue(session.sent.any { it is ServerMessage.ShortcutBarUpdate })
    }

    @Test
    fun shortcutBarSet_attack_validAttack_updatesSlot() = runBlocking {
        val saved = mutableListOf<org.micoli.micraft.session.PlayerSession>()
        val registry = mapOf("fireball" to org.micoli.micraft.combat.AttackDefinition())
        val placer = BlockPlacer(testWorld(), {}, { saved.add(it) }, attackRegistry = registry)
        val session = testSession()
        placer.handleShortcutBarSet(
            session, ClientMessage.ShortcutBarSet(1, ShortcutSlot.Attack("fireball")))
        assertEquals(ShortcutSlot.Attack("fireball"), session.shortcutBar[1])
        assertTrue(session.sent.any { it is ServerMessage.ShortcutBarUpdate })
    }

    @Test
    fun shortcutBarSet_attack_unknownAttack_rejected() = runBlocking {
        val placer = placer()
        val session = testSession()
        placer.handleShortcutBarSet(
            session, ClientMessage.ShortcutBarSet(1, ShortcutSlot.Attack("unknown_spell")))
        assertEquals(null, session.shortcutBar[1])
        assertTrue(session.sent.isEmpty())
    }
}
