package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.support.testSession

class WorldItemManagerTest {

    private fun blockLoader(blocks: Map<String, String> = emptyMap()): BlockRegistryLoader {
        val resourcesDir = createTempDirectory("resources_blocks")
        val dataDir = createTempDirectory("data_blocks")
        blocks.forEach { (name, yaml) ->
            val blockDir = resourcesDir.resolve(name)
            blockDir.toFile().mkdirs()
            blockDir.resolve("$name.yaml").writeText(yaml)
        }
        return BlockRegistryLoader(resourcesDir, dataDir)
    }

    private fun dropConfigWith100PctStone(): DropConfig =
        DropConfig(
            blockLoader(
                mapOf(
                    "STONE" to
                        "hardness: 1\nsolid: true\nminimapColor: [0, 0, 0]\ndrops:\n- item: COBBLESTONE\n  dropRate: 100\n  minCount: 1\n  maxCount: 1\n")))

    private fun emptyDropConfig(): DropConfig = DropConfig(blockLoader())

    @Test
    fun spawnDrops_emptyDrop_noBroadcast() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val wim = WorldItemManager(emptyDropConfig(), { broadcasts.add(it) })
        val spawned = wim.spawnDrops(BlockPos(8, 5, 8), BlockType.STONE)
        assertTrue(spawned.isEmpty())
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun spawnDrops_withDrops_broadcastsItemsSpawned() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val wim = WorldItemManager(dropConfigWith100PctStone(), { broadcasts.add(it) })
        wim.spawnDrops(BlockPos(8, 5, 8), BlockType.STONE)
        assertTrue(broadcasts.any { it is ServerMessage.ItemsSpawned })
    }

    @Test
    fun spawnDrops_registersItem() = runBlocking {
        val wim = WorldItemManager(dropConfigWith100PctStone(), {})
        val spawned = wim.spawnDrops(BlockPos(8, 5, 8), BlockType.STONE)
        assertTrue(spawned.isNotEmpty())
        assertTrue(wim.hasItem(spawned[0].id))
    }

    @Test
    fun despawnItem_existing_removesAndBroadcasts() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val wim = WorldItemManager(dropConfigWith100PctStone(), { broadcasts.add(it) })
        val spawned = wim.spawnDrops(BlockPos(8, 5, 8), BlockType.STONE)
        val id = spawned[0].id
        broadcasts.clear()
        wim.despawnItem(id)
        assertFalse(wim.hasItem(id))
        assertTrue(broadcasts.any { it is ServerMessage.ItemDespawned })
    }

    @Test
    fun despawnItem_unknown_noop() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val wim = WorldItemManager(emptyDropConfig(), { broadcasts.add(it) })
        wim.despawnItem("nonexistent-id")
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun tickCollection_playerFar_doesNotCollect() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val saved = mutableListOf<PlayerSession>()
        val wim =
            WorldItemManager(dropConfigWith100PctStone(), { broadcasts.add(it) }, { saved.add(it) })
        val spawned = wim.spawnDrops(BlockPos(8, 5, 8), BlockType.STONE)
        broadcasts.clear()
        // Player far from drop (at 0,0,0)
        val session = testSession(pos = Vec3(0f, 0f, 0f))
        wim.tickCollection(listOf(session))
        assertTrue(wim.hasItem(spawned[0].id))
        assertTrue(session.inventory.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun tickCollection_playerNear_updatesInventory() = runBlocking {
        val wim = WorldItemManager(dropConfigWith100PctStone(), {})
        val spawned = wim.spawnDrops(BlockPos(8, 5, 8), BlockType.STONE)
        // Item spawned at (8.5, 5.5, 8.5) — put player right there
        val session = testSession(pos = Vec3(8.5f, 5f, 8.5f))
        wim.tickCollection(listOf(session))
        assertFalse(wim.hasItem(spawned[0].id))
        assertTrue((session.inventory[ItemType("COBBLESTONE")] ?: 0) > 0)
    }

    @Test
    fun tickCollection_playerNear_callsSavePlayer() = runBlocking {
        val saved = mutableListOf<PlayerSession>()
        val wim = WorldItemManager(dropConfigWith100PctStone(), {}, { saved.add(it) })
        wim.spawnDrops(BlockPos(8, 5, 8), BlockType.STONE)
        val session = testSession(pos = Vec3(8.5f, 5f, 8.5f))
        wim.tickCollection(listOf(session))
        assertEquals(1, saved.size)
    }

    @Test
    fun tickCollection_playerNear_sendsInventoryUpdate() = runBlocking {
        val wim = WorldItemManager(dropConfigWith100PctStone(), {})
        wim.spawnDrops(BlockPos(8, 5, 8), BlockType.STONE)
        val session = testSession(pos = Vec3(8.5f, 5f, 8.5f))
        wim.tickCollection(listOf(session))
        assertTrue(session.sent.any { it is ServerMessage.InventoryUpdate })
    }
}
