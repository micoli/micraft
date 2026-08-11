package org.micoli.micraft.game.world.block

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.WorldActionRecord
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class BlockPlacerTest {

    private fun registerLegoPiece() {
        BlockRegistry.load(
            mapOf(
                BlockType.LEGO_PIECE to
                    BlockDefinition(
                        hardness = 1f,
                        solid = true,
                        isCubic = false,
                        replaceable = false,
                        brickSize = listOf(0.25f, 1f, 0.25f),
                        heightFraction = 0.333f,
                    )))
    }

    private fun placer(
        broadcasts: MutableList<ServerMessage> = mutableListOf(),
        saved: MutableList<PlayerSession> = mutableListOf(),
        world: WorldState = testWorld(),
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
    fun place_insideProtectedZone_rejected() = runBlocking {
        val world = testWorld()
        val registry = org.micoli.micraft.game.world.instance.InstanceRegistry(null)
        registry.create(
            name = "Arena",
            yMin = 0,
            yMax = 16,
            chunks = setOf(org.micoli.micraft.game.world.ChunkPos(0, 0)),
            ownerName = "Alice",
        )
        val placer = BlockPlacer(world, {}, {}, instanceRegistry = registry)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 3
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE")))
        assertEquals(BlockType.AIR, world.getBlock(8, 7, 8))
        assertEquals(3, session.inventory[ItemType("COBBLESTONE")])
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
        val saved = mutableListOf<PlayerSession>()
        val placer = placer(saved = saved)
        val session = testSession()
        val intent =
            ClientMessage.ShortcutBarSet(
                page = 0, slot = 2, content = ShortcutSlot.Item(ItemType("COBBLESTONE")))
        placer.handleShortcutBarSet(session, intent)
        assertEquals(ShortcutSlot.Item(ItemType("COBBLESTONE")), session.shortcutBarPages[0][2])
        assertTrue(session.sent.any { it is ServerMessage.ShortcutBarUpdate })
        assertEquals(1, saved.size)
    }

    @Test
    fun shortcutBarSet_slot0_rejected() = runBlocking {
        val placer = placer()
        val session = testSession()
        placer.handleShortcutBarSet(
            session,
            ClientMessage.ShortcutBarSet(
                page = 0, slot = 0, content = ShortcutSlot.Item(ItemType("COBBLESTONE"))))
        // slot 0 = hand slot, must stay null
        assertEquals(null, session.shortcutBarPages[0][0])
        assertTrue(session.sent.isEmpty())
    }

    @Test
    fun shortcutBarSet_nonBuildableItem_rejected() = runBlocking {
        val placer = placer()
        val session = testSession()
        placer.handleShortcutBarSet(
            session,
            ClientMessage.ShortcutBarSet(
                page = 0, slot = 1, content = ShortcutSlot.Item(ItemType("SNOWBALL"))))
        assertEquals(null, session.shortcutBarPages[0][1])
        assertTrue(session.sent.isEmpty())
    }

    @Test
    fun shortcutBarSet_null_clearsSlot() = runBlocking {
        val placer = placer()
        val session = testSession()
        session.shortcutBarPages[0][3] = ShortcutSlot.Item(ItemType("DIRT"))
        placer.handleShortcutBarSet(
            session, ClientMessage.ShortcutBarSet(page = 0, slot = 3, content = null))
        assertEquals(null, session.shortcutBarPages[0][3])
        assertTrue(session.sent.any { it is ServerMessage.ShortcutBarUpdate })
    }

    @Test
    fun shortcutBarSet_attack_validAttack_updatesSlot() = runBlocking {
        val saved = mutableListOf<PlayerSession>()
        val registry = mapOf("fireball" to AttackDefinition())
        val placer = BlockPlacer(testWorld(), {}, { saved.add(it) }, attackRegistry = registry)
        val session = testSession()
        placer.handleShortcutBarSet(
            session,
            ClientMessage.ShortcutBarSet(
                page = 0, slot = 1, content = ShortcutSlot.Attack("fireball")))
        assertEquals(ShortcutSlot.Attack("fireball"), session.shortcutBarPages[0][1])
        assertTrue(session.sent.any { it is ServerMessage.ShortcutBarUpdate })
    }

    @Test
    fun shortcutBarSet_attack_unknownAttack_rejected() = runBlocking {
        val placer = placer()
        val session = testSession()
        placer.handleShortcutBarSet(
            session,
            ClientMessage.ShortcutBarSet(
                page = 0, slot = 1, content = ShortcutSlot.Attack("unknown_spell")))
        assertEquals(null, session.shortcutBarPages[0][1])
        assertTrue(session.sent.isEmpty())
    }

    @Test
    fun shortcutBarSet_macro_validName_accepted() = runBlocking {
        val placer = placer()
        val session = testSession()
        placer.handleShortcutBarSet(
            session,
            ClientMessage.ShortcutBarSet(
                page = 0, slot = 1, content = ShortcutSlot.Macro("myMacro")))
        assertEquals(ShortcutSlot.Macro("myMacro"), session.shortcutBarPages[0][1])
        assertTrue(session.sent.any { it is ServerMessage.ShortcutBarUpdate })
    }

    @Test
    fun shortcutBarSet_macro_blankName_rejected() = runBlocking {
        val placer = placer()
        val session = testSession()
        placer.handleShortcutBarSet(
            session,
            ClientMessage.ShortcutBarSet(page = 0, slot = 1, content = ShortcutSlot.Macro("   ")))
        assertEquals(null, session.shortcutBarPages[0][1])
        assertTrue(session.sent.isEmpty())
    }

    @Test
    fun place_withRotation_storesStateInWorldAndBroadcasts() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val world = testWorld()
        val placer = placer(broadcasts = broadcasts, world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("COBBLESTONE")] = 1
        placer.handlePlace(
            session,
            ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("COBBLESTONE"), state = 2))
        assertEquals(2.toByte(), world.getState(8, 7, 8))
        val update = broadcasts.filterIsInstance<ServerMessage.WorldUpdate>().first()
        assertEquals(2.toByte(), update.changes.first().state)
    }

    @Test
    fun place_xzYFractional_fourSlotsEachStackedThrice_allSucceed() = runBlocking {
        registerLegoPiece()
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_PIECE")] = 20
        for (xOff in 0..1) for (zOff in 0..1) for (y in 0..2) {
            placer.handlePlace(
                session,
                ClientMessage.BlockPlace(
                    BlockPos(8, 7, 8),
                    ItemType("LEGO_PIECE"),
                    xOffset = xOff.toByte(),
                    zOffset = zOff.toByte()))
        }
        assertEquals(BlockType.LEGO_PIECE, world.getBlock(8, 7, 8))
        assertEquals(20 - 12, session.inventory[ItemType("LEGO_PIECE")])
    }

    @Test
    fun place_xzYFractional_fourthInSameSlot_rejected() = runBlocking {
        registerLegoPiece()
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_PIECE")] = 10
        repeat(3) {
            placer.handlePlace(
                session,
                ClientMessage.BlockPlace(
                    BlockPos(8, 7, 8), ItemType("LEGO_PIECE"), xOffset = 0, zOffset = 0))
        }
        val before = session.inventory[ItemType("LEGO_PIECE")]
        placer.handlePlace(
            session,
            ClientMessage.BlockPlace(
                BlockPos(8, 7, 8), ItemType("LEGO_PIECE"), xOffset = 0, zOffset = 0))
        assertEquals(before, session.inventory[ItemType("LEGO_PIECE")])
    }

    @Test
    fun place_xzYFractional_differentSlotIndependentOfFullSlot() = runBlocking {
        registerLegoPiece()
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_PIECE")] = 10
        repeat(3) {
            placer.handlePlace(
                session,
                ClientMessage.BlockPlace(
                    BlockPos(8, 7, 8), ItemType("LEGO_PIECE"), xOffset = 0, zOffset = 0))
        }
        val before = session.inventory[ItemType("LEGO_PIECE")]!!
        placer.handlePlace(
            session,
            ClientMessage.BlockPlace(
                BlockPos(8, 7, 8), ItemType("LEGO_PIECE"), xOffset = 1, zOffset = 0))
        assertEquals(before - 1, session.inventory[ItemType("LEGO_PIECE")])
    }

    @Test
    fun place_xzYFractional_offsetOutOfRange_rejected() = runBlocking {
        registerLegoPiece()
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_PIECE")] = 5
        placer.handlePlace(
            session,
            ClientMessage.BlockPlace(
                BlockPos(8, 7, 8), ItemType("LEGO_PIECE"), xOffset = 4, zOffset = 0))
        assertEquals(5, session.inventory[ItemType("LEGO_PIECE")])
        assertEquals(BlockType.AIR, world.getBlock(8, 7, 8))
    }
}
