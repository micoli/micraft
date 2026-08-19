package org.micoli.micraft.game.vehicle

import kotlin.math.atan2
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
import org.micoli.micraft.game.world.rail.RailConnectionPoint
import org.micoli.micraft.game.world.rail.RailDefinition
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.vehicle.VehicleDefinition
import org.micoli.micraft.vehicle.VehicleRegistry

private fun rc(direction: Direction, dy: Float = 1f) = RailConnectionPoint(direction, dy)

private fun grp(vararg points: RailConnectionPoint) = points.toList()

class VehicleBehaviorTest {

    private val straight = BlockType("RAIL_STRAIGHT")
    private val curve90 = BlockType("RAIL_CURVE_90")
    private val ySplit = BlockType("RAIL_Y_SPLIT_90")
    private val slope = BlockType("RAIL_SLOPE_45")
    private val cross = BlockType("RAIL_CROSS")
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
                            rail =
                                RailDefinition(
                                    connections =
                                        listOf(grp(rc(Direction.NORTH), rc(Direction.SOUTH))))),
                    curve90 to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            rail =
                                RailDefinition(
                                    connections =
                                        listOf(grp(rc(Direction.NORTH), rc(Direction.EAST))))),
                    ySplit to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            rail =
                                RailDefinition(
                                    connections =
                                        listOf(
                                            grp(rc(Direction.SOUTH), rc(Direction.NORTH)),
                                            grp(rc(Direction.SOUTH), rc(Direction.EAST))))),
                    slope to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            // South neighbor of a slope sits one grid level up.
                            rail =
                                RailDefinition(
                                    connections =
                                        listOf(grp(rc(Direction.NORTH), rc(Direction.SOUTH, 2f))))),
                    cross to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            rail =
                                RailDefinition(
                                    connections =
                                        listOf(
                                            grp(rc(Direction.NORTH), rc(Direction.SOUTH)),
                                            grp(rc(Direction.EAST), rc(Direction.WEST))))),
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
        extra: Int = 0,
        y: Int = 7,
    ) {
        world.applyChange(
            BlockChange(
                BlockPos(x, y, z), type, BlockState.pack(rotation, 0), BlockState.packExtra(extra)))
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

    @Test
    fun onASlope_climbsSmoothlyToTheNeighborOneLevelUp() {
        val world = testWorld()
        place(world, 8, 7, straight, 0, y = 7) // lower run
        place(world, 8, 8, slope, 0, y = 7) // bridges up to the z=9 run at y=8
        place(world, 8, 9, straight, 0, y = 8) // upper run

        // Half a block per tick, so crossing the slope cell takes 2 ticks and progress=0.5 is
        // observable mid-crossing (the default 1-block/tick speed never leaves a mid-block state).
        VehicleRegistry.load(
            savedVehicles + mapOf(cart to VehicleDefinition(speed = 0.5f / TICK_SECONDS)))

        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(8, 7, 7),
                    travelDirection = Direction.SOUTH)
                .apply { moving = true }

        val slopePitch = atan2(1.0, 1.0).toFloat() // dy rises 1 -> 2 over the block's unit run

        tick(instance, world, 2) // straight -> slope, still y=7
        assertEquals(BlockPos(8, 7, 8), instance.railBlockPos)
        assertEquals(8f, instance.pos.y, "on the slope cell, pose lands on its own floor")
        assertEquals(
            slopePitch,
            instance.pitch,
            "tilted nose-up entering the slope, matching its 1-in-1 rise")

        tick(instance, world) // mid-slope: progress interpolates toward y=8 upper run
        assertEquals(8.5f, instance.pos.y, "climbing halfway through the slope")
        assertEquals(0.5f, instance.progress)
        assertEquals(slopePitch, instance.pitch, "pitch stays constant across the slope cell")

        tick(instance, world) // finish crossing the slope -> upper run at y=8
        assertEquals(BlockPos(8, 8, 9), instance.railBlockPos, "reached the upper run one level up")
        assertEquals(9f, instance.pos.y)
        assertEquals(0f, instance.pitch, "level again on the flat upper run")
    }

    @Test
    fun onASlopeAtAnApparentDeadEnd_connectsToTheNeighborBuiltOneLevelUp_insteadOfBouncing() {
        val world = testWorld()
        // rotation 3: NORTH->EAST(dy0), SOUTH->WEST(dy1) — exiting WEST expects a neighbor one
        // level up. Nothing sits at the same-Y (20,1,12): a same-Y-only search would wrongly treat
        // this as a dead end and bounce instead of finding the block actually built at y=2.
        place(world, 21, 12, slope, 3, y = 1)
        place(world, 20, 12, straight, 1, y = 2)
        place(world, 22, 12, straight, 1, y = 1)

        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(22, 1, 12),
                    travelDirection = Direction.WEST)
                .apply { moving = true }

        tick(instance, world) // 22 -> slope@21, still y=1
        assertEquals(BlockPos(21, 1, 12), instance.railBlockPos)

        tick(instance, world) // slope@21 -> 20, stepping up to y=2 instead of bouncing
        assertEquals(
            BlockPos(20, 2, 12),
            instance.railBlockPos,
            "climbed onto the neighbor built one level up instead of bouncing")
    }

    @Test
    fun approachingASlopeFromItsFlatHighNeighbor_findsItOneLevelDown_insteadOfBouncing() {
        val world = testWorld()
        // Same layout as above, but the vehicle starts on the flat, dy=0-declared neighbor at the
        // slope's high side instead of on the slope itself — that flat piece has no idea a slope
        // sits one level below it, so finding the connection relies entirely on the slope's own
        // reverse-facing point declaring the jump (the fallback ±1 search), not on anything the
        // flat piece itself knows.
        place(world, 21, 12, slope, 3, y = 1)
        place(world, 20, 12, straight, 1, y = 2)
        place(world, 22, 12, straight, 1, y = 1)

        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(20, 2, 12),
                    travelDirection = Direction.EAST)
                .apply { moving = true }

        tick(instance, world) // 20 (flat, y=2) -> slope@21, stepping down to y=1
        assertEquals(
            BlockPos(21, 1, 12),
            instance.railBlockPos,
            "found the slope one level down instead of bouncing at an apparent dead end")
    }

    @Test
    fun onAStraightAtWorldFloor_deadEnding_theFallbackYSearchDoesNotCrossTheWorldBoundary() {
        val world = testWorld()
        // The ±1 fallback search in RailTraversal.connectingNeighbor must not try y=-1 at the
        // world floor — BlockPos throws on an out-of-range Y instead of just finding no neighbor.
        place(world, 8, 6, straight, 0, y = 0)

        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(8, 0, 6),
                    travelDirection = Direction.SOUTH)
                .apply { moving = true }

        tick(instance, world) // no neighbor in any direction — must reverse in place, not throw
        assertEquals(BlockPos(8, 0, 6), instance.railBlockPos)
        assertEquals(Direction.NORTH, instance.travelDirection)
    }

    @Test
    fun onACurveAtADeadEnd_backtracksTheWayItCameIn_insteadOfBouncingForever() {
        val world = testWorld()
        // curve90 connects NORTH/EAST (unrotated) — perpendicular, not opposite. Only the NORTH
        // side has a neighbor; EAST is a dead end.
        place(world, 8, 7, straight, 0) // north of the curve — the only way in or out
        place(world, 8, 8, curve90, 0) // connects NORTH (to the straight) and EAST (dead end)

        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(8, 7, 7),
                    travelDirection = Direction.SOUTH)
                .apply { moving = true }

        tick(instance, world) // straight -> curve; arrival sets travelDirection to EAST (the turn)
        assertEquals(BlockPos(8, 7, 8), instance.railBlockPos)
        assertEquals(Direction.EAST, instance.travelDirection)

        tick(instance, world) // EAST is a dead end — must reverse to NORTH (the way it came in),
        // not WEST (exitDir.opposite), which isn't one of the curve's own connections at all.
        assertEquals(BlockPos(8, 7, 8), instance.railBlockPos, "still on the curve, didn't move")
        assertEquals(Direction.NORTH, instance.travelDirection)

        tick(instance, world) // curve -> straight, back the way it came
        assertEquals(BlockPos(8, 7, 7), instance.railBlockPos)
    }

    @Test
    fun onACrossing_goesStraightThrough_neverTurnsOntoTheOtherPair() {
        val world = testWorld()
        place(world, 8, 7, straight, 0) // north leg
        place(world, 8, 9, straight, 0) // south leg
        place(world, 7, 8, straight, 1) // west leg
        place(world, 9, 8, straight, 1) // east leg
        place(world, 8, 8, cross, 0)

        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(8, 7, 7),
                    travelDirection = Direction.SOUTH)
                .apply { moving = true }

        tick(instance, world) // north leg -> crossing
        assertEquals(BlockPos(8, 7, 8), instance.railBlockPos)
        assertEquals(Direction.SOUTH, instance.travelDirection, "kept going straight, not turning")

        tick(instance, world) // crossing -> south leg
        assertEquals(BlockPos(8, 7, 9), instance.railBlockPos)
    }

    @Test
    fun onACrossingAtADeadEnd_reversesStraightBack_insteadOfTurningOntoTheOtherPair() {
        val world = testWorld()
        // Only the north leg exists — east/south/west are all dead ends.
        place(world, 8, 7, straight, 0)
        place(world, 8, 8, cross, 0)

        val instance =
            VehicleInstance(
                    id = "v1",
                    type = cart,
                    railBlockPos = BlockPos(8, 7, 7),
                    travelDirection = Direction.SOUTH)
                .apply { moving = true }

        tick(instance, world) // north leg -> crossing
        assertEquals(BlockPos(8, 7, 8), instance.railBlockPos)

        tick(instance, world) // south is a dead end — must reverse straight back to NORTH
        assertEquals(BlockPos(8, 7, 8), instance.railBlockPos, "still on the crossing, didn't move")
        assertEquals(Direction.NORTH, instance.travelDirection)
    }
}
