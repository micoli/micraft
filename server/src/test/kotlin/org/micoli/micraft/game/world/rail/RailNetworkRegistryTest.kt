package org.micoli.micraft.game.world.rail

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.support.testWorld

class RailNetworkRegistryTest {

    private val straight = BlockType("RAIL_STRAIGHT")
    private val curve90 = BlockType("RAIL_CURVE_90")
    private val ySplit = BlockType("RAIL_Y_SPLIT_90")

    private lateinit var savedBlocks: Map<BlockType, BlockDefinition>

    @BeforeTest
    fun setUp() {
        // Touch TestFixtures once so its file-level ItemRegistry.load(...) initializer (only run
        // on first access) has already fired before we snapshot BlockRegistry below.
        testWorld()
        savedBlocks = BlockRegistry.all().associateWith { BlockRegistry.get(it) }
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
    }

    @AfterTest
    fun tearDown() {
        BlockRegistry.load(savedBlocks)
    }

    private fun place(
        world: WorldState,
        x: Int,
        z: Int,
        type: BlockType,
        rotation: Int,
        extra: Int = 0,
    ) {
        world.applyChange(
            BlockChange(
                BlockPos(x, 7, z), type, BlockState.pack(rotation, 0), BlockState.packExtra(extra)))
    }

    private fun rotationConnecting(base: List<String>, want: Set<Direction>): Int =
        (0..3).first { r -> base.map { Direction.valueOf(it).rotatedBy(r) }.toSet() == want }

    @Test
    fun straightRun_terminatedByNonRail_isOneSegment() {
        val world = testWorld()
        for (z in 0..4) place(world, 8, z, straight, 0)
        val registry = RailNetworkRegistry(world)

        val topology = registry.topologyAt(BlockPos(8, 7, 2))

        assertIs<RailTopology.Segment>(topology)
        assertEquals(5, topology.positions.size)
        assertEquals(
            setOf(0, 1, 2, 3, 4),
            topology.positions.map { it.z }.toSet(),
            "same segment end to end")
    }

    @Test
    fun breakingMidSegment_splitsIntoTwoSegments() {
        val world = testWorld()
        for (z in 0..4) place(world, 8, z, straight, 0)
        val registry = RailNetworkRegistry(world)
        registry.topologyAt(BlockPos(8, 7, 2)) // warm the cache before the break

        world.applyChange(BlockChange(BlockPos(8, 7, 2), BlockType.AIR))
        registry.invalidate(BlockPos(8, 7, 2))

        assertNull(registry.topologyAt(BlockPos(8, 7, 2)), "broken cell is no longer a rail")
        val left = registry.topologyAt(BlockPos(8, 7, 0))
        val right = registry.topologyAt(BlockPos(8, 7, 4))
        assertIs<RailTopology.Segment>(left)
        assertIs<RailTopology.Segment>(right)
        assertEquals(2, left.positions.size)
        assertEquals(2, right.positions.size)
    }

    // Ring D(8,8) -> A(9,8) -> B(9,9) -> C(8,9) -> D, each edge direction derived from the actual
    // grid offsets so the per-corner rotation is correct by construction rather than hand-derived.
    private val ringBase = listOf("NORTH", "EAST")

    private fun placeRing(world: WorldState, includeLast: Boolean = true) {
        place(
            world,
            8,
            8,
            curve90,
            rotationConnecting(ringBase, setOf(Direction.EAST, Direction.SOUTH)))
        place(
            world,
            9,
            8,
            curve90,
            rotationConnecting(ringBase, setOf(Direction.WEST, Direction.SOUTH)))
        place(
            world,
            9,
            9,
            curve90,
            rotationConnecting(ringBase, setOf(Direction.WEST, Direction.NORTH)))
        if (includeLast) {
            place(
                world,
                8,
                9,
                curve90,
                rotationConnecting(ringBase, setOf(Direction.EAST, Direction.NORTH)))
        }
    }

    @Test
    fun closedRingOfCurves_isALoop() {
        val world = testWorld()
        placeRing(world)
        val registry = RailNetworkRegistry(world)

        val topology = registry.topologyAt(BlockPos(8, 7, 8))

        assertIs<RailTopology.Loop>(topology)
        assertEquals(4, topology.positions.size)
    }

    @Test
    fun placingLastPieceOfARing_reclassifiesSegmentAsLoop() {
        val world = testWorld()
        placeRing(world, includeLast = false)
        val registry = RailNetworkRegistry(world)
        val before = registry.topologyAt(BlockPos(8, 7, 8))
        assertIs<RailTopology.Segment>(before, "3 curves alone form an open chain, not a loop")

        place(
            world,
            8,
            9,
            curve90,
            rotationConnecting(ringBase, setOf(Direction.EAST, Direction.NORTH)))
        registry.invalidate(BlockPos(8, 7, 9))
        registry.invalidate(BlockPos(9, 7, 9))
        registry.invalidate(BlockPos(8, 7, 8))

        val after = registry.topologyAt(BlockPos(8, 7, 8))
        assertIs<RailTopology.Loop>(after)
        assertEquals(4, after.positions.size)
    }

    @Test
    fun ySplit_followsActiveBranch_switchingPrunesTheOtherSide() {
        val world = testWorld()
        place(world, 8, 9, straight, 0) // entry leg, dead-ends south at (8,10)
        place(world, 8, 8, ySplit, 0, extra = 0) // branch0 (NORTH) active
        place(world, 8, 7, straight, 0) // north branch, dead-ends north at (8,6)
        place(world, 9, 8, straight, 1) // east branch stub, dead-ends east at (10,8) — inactive

        val registry = RailNetworkRegistry(world)

        val active = registry.topologyAt(BlockPos(8, 7, 8))
        assertIs<RailTopology.Segment>(active)
        assertEquals(3, active.positions.size, "entry + split + active north branch")

        val inactive = registry.topologyAt(BlockPos(9, 7, 8))
        assertIs<RailTopology.Segment>(inactive)
        assertEquals(1, inactive.positions.size, "east branch not part of the active path")

        // Toggle the switch — east branch becomes active, north branch is pruned instead.
        world.applyChange(
            BlockChange(BlockPos(8, 7, 8), ySplit, BlockState.pack(0, 0), BlockState.packExtra(1)))
        registry.invalidate(BlockPos(8, 7, 8))

        val afterToggle = registry.topologyAt(BlockPos(8, 7, 8))
        assertIs<RailTopology.Segment>(afterToggle)
        assertEquals(3, afterToggle.positions.size)
        assertEquals(
            setOf(BlockPos(8, 7, 9), BlockPos(8, 7, 8), BlockPos(9, 7, 8)),
            afterToggle.positions.toSet(),
            "now runs entry -> split -> east branch")

        val northBranchNowOrphan = registry.topologyAt(BlockPos(8, 7, 7))
        assertIs<RailTopology.Segment>(northBranchNowOrphan)
        assertEquals(1, northBranchNowOrphan.positions.size)
    }
}
