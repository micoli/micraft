package org.micoli.micraft.game.vehicle

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.command.commands.VehicleAddCommand
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.game.world.rail.RailConnectionPoint
import org.micoli.micraft.game.world.rail.RailDefinition
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.vehicle.VehicleDefinition
import org.micoli.micraft.vehicle.VehicleRegistry

class VehicleManagerTest {

    private val rail = BlockType("RAIL_STRAIGHT")
    private val cart = EntityType("CART")

    private lateinit var savedBlocks: Map<BlockType, BlockDefinition>
    private lateinit var savedVehicles: Map<EntityType, VehicleDefinition>

    @BeforeTest
    fun setUp() {
        testWorld() // warm up TestFixtures static init before snapshotting the registries
        savedBlocks = BlockRegistry.all().associateWith { BlockRegistry.get(it) }
        savedVehicles = VehicleRegistry.keys().associateWith { VehicleRegistry.get(it)!! }
        BlockRegistry.load(
            savedBlocks +
                mapOf(
                    rail to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            rail =
                                RailDefinition(
                                    connections =
                                        listOf(
                                            listOf(
                                                RailConnectionPoint(Direction.NORTH),
                                                RailConnectionPoint(Direction.SOUTH)))))))
        VehicleRegistry.load(savedVehicles + mapOf(cart to VehicleDefinition(speed = 2f)))
    }

    @AfterTest
    fun tearDown() {
        BlockRegistry.load(savedBlocks)
        VehicleRegistry.load(savedVehicles)
    }

    @Test
    fun spawnVehicle_onRailBlock_succeedsAndBroadcasts() = runBlocking {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), rail))
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = VehicleManager({ broadcasts.add(it) })

        val spawned = manager.spawnVehicle(cart, BlockPos(8, 7, 8), world, Direction.SOUTH)

        assertNotNull(spawned)
        assertEquals(cart, spawned.type)
        assertEquals(1, manager.getAll().size)
        val spawnedMsg = broadcasts.filterIsInstance<ServerMessage.VehicleSpawned>().single()
        assertEquals(spawned.id, spawnedMsg.vehicle.id)
        assertEquals(BlockPos(8, 7, 8), spawnedMsg.vehicle.railBlockPos)
    }

    @Test
    fun spawnVehicle_onNonRailBlock_isRejected() = runBlocking {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), BlockType.STONE))
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = VehicleManager({ broadcasts.add(it) })

        val spawned = manager.spawnVehicle(cart, BlockPos(8, 7, 8), world, Direction.SOUTH)

        assertNull(spawned)
        assertEquals(emptyList(), manager.getAll().toList())
        assertEquals(emptyList(), broadcasts)
    }

    @Test
    fun spawnVehicle_unknownType_isRejected() = runBlocking {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), rail))
        val manager = VehicleManager({})

        val spawned =
            manager.spawnVehicle(
                EntityType("NOT_A_VEHICLE"), BlockPos(8, 7, 8), world, Direction.SOUTH)

        assertNull(spawned)
    }

    @Test
    fun initialDirectionFrom_picksTheDirectionClosestToPlayerFacing() {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), rail)) // connections rotated: NORTH, SOUTH
        // Facing -Z (north, per the documented yaw=0 convention) should pick NORTH, and the
        // opposite facing should pick the opposite direction, SOUTH.
        val facingNorth = VehicleAddCommand.initialDirectionFrom(0f, BlockPos(8, 7, 8), world)
        val facingSouth = VehicleAddCommand.initialDirectionFrom(180f, BlockPos(8, 7, 8), world)
        assertEquals(Direction.NORTH, facingNorth)
        assertEquals(Direction.SOUTH, facingSouth)
        assertEquals(facingNorth.opposite, facingSouth)
    }
}
