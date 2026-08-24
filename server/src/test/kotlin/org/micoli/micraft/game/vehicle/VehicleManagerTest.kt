package org.micoli.micraft.game.vehicle

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
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

    @Test
    fun mount_onUnoccupiedVehicle_succeeds() = runBlocking {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), rail))
        val manager = VehicleManager({})
        val instance = manager.spawnVehicle(cart, BlockPos(8, 7, 8), world, Direction.SOUTH)!!
        val session = testSession()

        assertTrue(manager.mount(instance.id, session))

        assertEquals(session.id, manager.get(instance.id)?.riderSessionId)
    }

    @Test
    fun mount_onOccupiedVehicle_fails() = runBlocking {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), rail))
        val manager = VehicleManager({})
        val instance = manager.spawnVehicle(cart, BlockPos(8, 7, 8), world, Direction.SOUTH)!!
        manager.mount(instance.id, testSession(id = "first"))

        assertFalse(manager.mount(instance.id, testSession(id = "second")))
        assertEquals("first", manager.get(instance.id)?.riderSessionId)
    }

    @Test
    fun dismount_clearsRider() = runBlocking {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), rail))
        val manager = VehicleManager({})
        val instance = manager.spawnVehicle(cart, BlockPos(8, 7, 8), world, Direction.SOUTH)!!
        val session = testSession()
        manager.mount(instance.id, session)

        manager.dismount(session)

        assertNull(manager.get(instance.id)?.riderSessionId)
    }

    @Test
    fun clearRider_byId_isNoOpWhenNoneMatches() = runBlocking {
        val manager = VehicleManager({})
        manager.clearRider("nobody")
    }

    @Test
    fun tick_syncsRiderPositionEveryTick_evenWhenStopped() = runBlocking {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), rail))
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = VehicleManager({ broadcasts.add(it) })
        val instance = manager.spawnVehicle(cart, BlockPos(8, 7, 8), world, Direction.SOUTH)!!
        instance.moving = false
        val session = testSession()
        session.mountedVehicleId = instance.id
        manager.mount(instance.id, session)

        manager.tick(world, listOf(session))

        val update = broadcasts.filterIsInstance<ServerMessage.PlayerUpdate>().last()
        assertEquals(instance.pos.x, update.state.pos.x)
        assertEquals(instance.pos.y, update.state.pos.y)
        assertEquals(instance.pos.z, update.state.pos.z)
    }

    @Test
    fun tick_appliesSeatOffset() = runBlocking {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), rail))
        VehicleRegistry.load(
            savedVehicles +
                mapOf(cart to VehicleDefinition(speed = 2f, seatOffset = Vec3(0f, 1f, 0f))))
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = VehicleManager({ broadcasts.add(it) })
        val instance = manager.spawnVehicle(cart, BlockPos(8, 7, 8), world, Direction.SOUTH)!!
        val session = testSession()
        session.mountedVehicleId = instance.id
        manager.mount(instance.id, session)

        manager.tick(world, listOf(session))

        val update = broadcasts.filterIsInstance<ServerMessage.PlayerUpdate>().last()
        assertEquals(instance.pos.y + 1f, update.state.pos.y)
    }

    @Test
    fun tick_selfHeals_whenRiderSessionMissing() = runBlocking {
        val world = testWorld()
        world.applyChange(BlockChange(BlockPos(8, 7, 8), rail))
        val manager = VehicleManager({})
        val instance = manager.spawnVehicle(cart, BlockPos(8, 7, 8), world, Direction.SOUTH)!!
        val session = testSession()
        manager.mount(instance.id, session)
        // Session never got its mountedVehicleId set (or disconnected without cleanup) — the
        // rider link is stale from the vehicle's point of view.

        manager.tick(world, emptyList())

        assertNull(manager.get(instance.id)?.riderSessionId)
    }
}
