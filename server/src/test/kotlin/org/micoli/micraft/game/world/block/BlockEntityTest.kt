package org.micoli.micraft.game.world.block

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class BlockEntityTest {

    private fun noopWim(): WorldItemManager {
        val resourcesDir = createTempDirectory("resources_blocks")
        val blockDir = resourcesDir.resolve("LEGO_BRICK_2X1")
        blockDir.toFile().mkdirs()
        blockDir
            .resolve("LEGO_BRICK_2X1.yaml")
            .writeText("hardness: 1\nsolid: true\nminimapColor: [0, 0, 0]\n")
        return WorldItemManager(
            DropConfig(BlockRegistryLoader(resourcesDir, createTempDirectory("data_blocks"))), {})
    }

    private fun placer(
        broadcasts: MutableList<ServerMessage> = mutableListOf(),
        world: WorldState = testWorld(),
    ) = BlockPlacer(world, { broadcasts.add(it) }, {})

    private fun breaker(
        broadcasts: MutableList<ServerMessage> = mutableListOf(),
        world: WorldState = testWorld(),
    ) = BlockBreaker(world, { broadcasts.add(it) }, noopWim())

    private fun registerMultiBlock() {
        BlockRegistry.load(
            mapOf(
                BlockType("LEGO_BRICK_2X1") to
                    BlockDefinition(
                        hardness = 1f,
                        solid = true,
                        replaceable = false,
                        brickSize = listOf(2, 1, 1),
                    ),
                BlockType("LEGO_BRICK") to BlockDefinition(hardness = 1f, solid = true),
            ))
    }

    @Test
    fun place_multiCell_createsEntityInWorld() = runBlocking {
        registerMultiBlock()
        val world = testWorld()
        val broadcasts = mutableListOf<ServerMessage>()
        val placer = placer(broadcasts, world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_BRICK_2X1")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("LEGO_BRICK_2X1")))
        assertTrue(world.hasEntityAt(8, 7, 8), "Master cell should have entity")
        assertTrue(world.hasEntityAt(9, 7, 8), "Satellite cell (x+1) should have entity")
    }

    @Test
    fun place_multiCell_broadcastsEntityAdd() = runBlocking {
        registerMultiBlock()
        val world = testWorld()
        val broadcasts = mutableListOf<ServerMessage>()
        val placer = placer(broadcasts, world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_BRICK_2X1")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("LEGO_BRICK_2X1")))
        val update = broadcasts.filterIsInstance<ServerMessage.WorldUpdate>().firstOrNull()
        assertFalse(update?.entityAdds.isNullOrEmpty(), "WorldUpdate should contain entityAdds")
        val proto = update!!.entityAdds.first()
        assertEquals(2, proto.sizeX)
        assertEquals(8, proto.worldX)
        assertEquals(7, proto.worldY)
    }

    @Test
    fun place_multiCell_satelliteOccupied_rejected() = runBlocking {
        registerMultiBlock()
        val world = testWorld(Triple(9, 7, 8)) // satellite cell blocked by STONE
        val broadcasts = mutableListOf<ServerMessage>()
        val placer = placer(broadcasts, world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_BRICK_2X1")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("LEGO_BRICK_2X1")))
        assertFalse(
            world.hasEntityAt(8, 7, 8), "Entity should not be created when satellite occupied")
        assertTrue(broadcasts.filterIsInstance<ServerMessage.WorldUpdate>().isEmpty())
    }

    @Test
    fun place_entityAtPos_rejected() = runBlocking {
        registerMultiBlock()
        val world = testWorld()
        val broadcasts = mutableListOf<ServerMessage>()
        val placer = placer(broadcasts, world)
        // Place first entity
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_BRICK_2X1")] = 2
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("LEGO_BRICK_2X1")))
        val countAfterFirst = broadcasts.size
        // Try to place another at same position
        session.inventory[ItemType("LEGO_BRICK_2X1")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("LEGO_BRICK_2X1")))
        assertEquals(countAfterFirst, broadcasts.size, "Second placement should be rejected")
    }

    @Test
    fun break_entitySatelliteCell_removesWholeEntity() = runBlocking {
        registerMultiBlock()
        val world = testWorld()
        val placeBroadcasts = mutableListOf<ServerMessage>()
        val placer = placer(placeBroadcasts, world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_BRICK_2X1")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("LEGO_BRICK_2X1")))
        assertTrue(world.hasEntityAt(9, 7, 8), "Satellite should exist before break")

        val breakBroadcasts = mutableListOf<ServerMessage>()
        val breaker = breaker(breakBroadcasts, world)
        // Break satellite cell
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(9, 7, 8)))
        breaker.tick(session)
        assertFalse(
            world.hasEntityAt(8, 7, 8), "Master entity should be gone after breaking satellite")
        assertFalse(world.hasEntityAt(9, 7, 8), "Satellite entity should be gone after breaking")
        val update = breakBroadcasts.filterIsInstance<ServerMessage.WorldUpdate>().firstOrNull()
        assertFalse(
            update?.entityRemoves.isNullOrEmpty(), "WorldUpdate should contain entityRemoves")
    }

    @Test
    fun break_entityMasterCell_removesWholeEntity() = runBlocking {
        registerMultiBlock()
        val world = testWorld()
        val placer = placer(world = world)
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_BRICK_2X1")] = 1
        placer.handlePlace(
            session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("LEGO_BRICK_2X1")))

        val breakBroadcasts = mutableListOf<ServerMessage>()
        val breaker = breaker(breakBroadcasts, world)
        breaker.handleStart(session, ClientMessage.BlockBreakStart(BlockPos(8, 7, 8)))
        breaker.tick(session)
        assertFalse(world.hasEntityAt(8, 7, 8), "Master entity should be gone")
        assertFalse(world.hasEntityAt(9, 7, 8), "Satellite entity should be gone")
    }
}
