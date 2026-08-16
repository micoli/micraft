package org.micoli.micraft.game.vehicle

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.vehicle.VehicleDefinition
import org.micoli.micraft.vehicle.VehicleRegistry

class VehicleBehaviorTest {

    private val straight = BlockType("RAIL_STRAIGHT")
    private val curve90 = BlockType("RAIL_CURVE_90")
    private val ySplit = BlockType("RAIL_Y_SPLIT_90")
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
                    straight to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            connections = listOf(Direction.NORTH, Direction.SOUTH)),
                    curve90 to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            connections = listOf(Direction.NORTH, Direction.EAST)),
                    ySplit to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            connections = listOf(Direction.SOUTH, Direction.NORTH, Direction.EAST)),
                ))
        // Exactly one full block per tick regardless of the live TICK_MS (other test classes
        // mutate that global and don't always reset it) — makes "N ticks to cross a block" exact.
        VehicleRegistry.load(
            savedVehicles + mapOf(cart to VehicleDefinition(speed = 1f / TICK_SECONDS)))
    }

    @AfterTest
    fun tearDown() {
        BlockRegistry.load(savedBlocks)
        VehicleRegistry.load(savedVehicles)
    }

    private fun place(
        world: WorldState,
        x: Int,
        z: Int,
        type: BlockType,
        rotation: Int,
        extra: Int = 0
    ) {
        world.applyChange(
            BlockChange(
                BlockPos(x, 7, z), type, BlockState.pack(rotation, 0), BlockState.packExtra(extra)))
    }

    private fun tick(instance: VehicleInstance, world: WorldState, times: Int = 1) {
        repeat(times) { VehicleBehavior.tick(instance, world) }
    }

    @Test
    fun onASegment_reachingTheDeadEnd_reversesAndComesBack() {
        val world = testWorld()
        for (z in 0..2) place(world, 8, z, straight, 0)
        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(8, 7, 1),
                    travelDirection = Direction.SOUTH)
                .apply { moving = true }

        // Tick 1: SOUTH onto z=2, the last block. Tick 2: the attempted move to z=3 (non-rail)
        // reverses it in place.
        tick(instance, world, 2)
        assertEquals(BlockPos(8, 7, 2), instance.railBlockPos)
        assertEquals(Direction.NORTH, instance.travelDirection, "reversed at the dead end")

        // Ticks 3-4: walks back to z=0. Tick 5: the attempted move to z=-1 reverses it again.
        tick(instance, world, 3)
        assertEquals(BlockPos(8, 7, 0), instance.railBlockPos)
        assertEquals(Direction.SOUTH, instance.travelDirection, "reversed again at the other end")
    }

    @Test
    fun onALoop_neverReverses_cyclesIndefinitely() {
        val world = testWorld()
        // 2x2 ring of curves, matching RailNetworkRegistryTest's construction.
        val ringBase = listOf("NORTH", "EAST")
        fun rotationConnecting(want: Set<Direction>): Int =
            (0..3).first { r ->
                ringBase.map { Direction.valueOf(it).rotatedBy(r) }.toSet() == want
            }
        place(world, 8, 8, curve90, rotationConnecting(setOf(Direction.EAST, Direction.SOUTH)))
        place(world, 9, 8, curve90, rotationConnecting(setOf(Direction.WEST, Direction.SOUTH)))
        place(world, 9, 9, curve90, rotationConnecting(setOf(Direction.WEST, Direction.NORTH)))
        place(world, 8, 9, curve90, rotationConnecting(setOf(Direction.EAST, Direction.NORTH)))

        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(8, 7, 8),
                    travelDirection = Direction.EAST)
                .apply { moving = true }
        val visited = mutableSetOf<BlockPos>()
        repeat(20) {
            tick(instance, world)
            visited.add(instance.railBlockPos)
        }
        assertEquals(
            setOf(BlockPos(8, 7, 8), BlockPos(9, 7, 8), BlockPos(9, 7, 9), BlockPos(8, 7, 9)),
            visited,
            "cycled through all 4 ring positions without ever reversing off the ring")
    }

    @Test
    fun atASwitch_takesWhicheverBranchIsActiveAtTheMomentItReachesTheBlockBoundary() {
        val world = testWorld()
        place(world, 8, 9, straight, 0) // entry leg
        place(world, 8, 8, ySplit, 0, extra = 0) // starts on branch0 (NORTH)
        place(world, 8, 7, straight, 0) // north branch
        place(world, 9, 8, straight, 1) // east branch (rotation 1 -> WEST/EAST)

        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(8, 7, 9),
                    travelDirection = Direction.NORTH)
                .apply { moving = true }

        // Toggle to the east branch before the vehicle ever reaches the split — the decision made
        // when it actually arrives there must reflect the switch's state at that moment, not at
        // spawn time (there is no mid-block state to observe with 1 block/tick: the decision and
        // the move into the split happen atomically in the same tick).
        world.applyChange(
            BlockChange(BlockPos(8, 7, 8), ySplit, BlockState.pack(0, 0), BlockState.packExtra(1)))

        tick(instance, world) // entry -> split, decision reads the now-toggled branch
        assertEquals(BlockPos(8, 7, 8), instance.railBlockPos)
        assertEquals(Direction.EAST, instance.travelDirection, "took the currently-active branch")

        tick(instance, world) // split -> east branch
        assertEquals(BlockPos(9, 7, 8), instance.railBlockPos)
    }
}
