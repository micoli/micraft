package org.micoli.micraft.game.world.block

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.game.world.rail.RailConnectionPoint
import org.micoli.micraft.game.world.rail.RailNetworkRegistry
import org.micoli.micraft.game.world.rail.RailTopology
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class BlockInteractorTest {

    private val ySplit = BlockType("RAIL_Y_SPLIT_90")
    private val straight = BlockType("RAIL_STRAIGHT")

    private lateinit var savedBlocks: Map<BlockType, BlockDefinition>

    @BeforeTest
    fun setUp() {
        testWorld() // warm up TestFixtures static init before snapshotting the registry
        savedBlocks = BlockRegistry.all().associateWith { BlockRegistry.get(it) }
        BlockRegistry.load(
            savedBlocks +
                mapOf(
                    ySplit to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            connections =
                                listOf(
                                    listOf(
                                        RailConnectionPoint(Direction.SOUTH),
                                        RailConnectionPoint(Direction.NORTH)),
                                    listOf(
                                        RailConnectionPoint(Direction.SOUTH),
                                        RailConnectionPoint(Direction.EAST)))),
                    straight to
                        BlockDefinition(
                            solid = true,
                            isCubic = false,
                            rotatable = true,
                            connections =
                                listOf(
                                    listOf(
                                        RailConnectionPoint(Direction.NORTH),
                                        RailConnectionPoint(Direction.SOUTH)))),
                ))
    }

    @AfterTest
    fun tearDown() {
        BlockRegistry.load(savedBlocks)
    }

    @Test
    fun interact_onSwitch_flipsExtraStateAndBroadcasts() = runBlocking {
        val world = testWorld()
        val pos = BlockPos(8, 7, 8)
        world.applyChange(org.micoli.micraft.protocol.BlockChange(pos, ySplit))
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        val broadcasts = mutableListOf<ServerMessage>()
        val registry = RailNetworkRegistry(world)
        val interactor =
            BlockInteractor(world, { broadcasts.add(it) }, railNetworkRegistry = registry)

        interactor.handleInteract(session, ClientMessage.BlockInteract(pos))

        assertEquals(1, BlockState.extra(world.getExtraState(8, 7, 8)))
        val change =
            broadcasts.filterIsInstance<ServerMessage.WorldUpdate>().single().changes.single()
        assertEquals(1, BlockState.extra(change.extraState))
    }

    @Test
    fun interact_onSwitch_cyclesThroughAllBranches() = runBlocking {
        val world = testWorld()
        val pos = BlockPos(8, 7, 8)
        world.applyChange(org.micoli.micraft.protocol.BlockChange(pos, ySplit))
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        val interactor = BlockInteractor(world, {})

        interactor.handleInteract(session, ClientMessage.BlockInteract(pos))
        assertEquals(1, BlockState.extra(world.getExtraState(8, 7, 8)))
        interactor.handleInteract(session, ClientMessage.BlockInteract(pos))
        assertEquals(0, BlockState.extra(world.getExtraState(8, 7, 8)), "wraps back to branch 0")
    }

    @Test
    fun interact_onNonJunctionBlock_isNoOp() = runBlocking {
        val world = testWorld()
        val pos = BlockPos(8, 7, 8)
        world.applyChange(org.micoli.micraft.protocol.BlockChange(pos, straight))
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        val broadcasts = mutableListOf<ServerMessage>()
        val interactor = BlockInteractor(world, { broadcasts.add(it) })

        interactor.handleInteract(session, ClientMessage.BlockInteract(pos))

        assertEquals(0, world.getExtraState(8, 7, 8).toInt())
        assertEquals(emptyList(), broadcasts)
    }

    @Test
    fun interact_tooFar_isRejected() = runBlocking {
        val world = testWorld()
        val pos = BlockPos(8, 7, 8)
        world.applyChange(org.micoli.micraft.protocol.BlockChange(pos, ySplit))
        val session = testSession(pos = Vec3(100.5f, 6f, 100.5f))
        val broadcasts = mutableListOf<ServerMessage>()
        val interactor = BlockInteractor(world, { broadcasts.add(it) })

        interactor.handleInteract(session, ClientMessage.BlockInteract(pos))

        assertEquals(0, world.getExtraState(8, 7, 8).toInt())
        assertEquals(emptyList(), broadcasts)
    }

    @Test
    fun interact_invalidatesRailNetworkRegistry() = runBlocking {
        val world = testWorld()
        val pos = BlockPos(8, 7, 8)
        world.applyChange(org.micoli.micraft.protocol.BlockChange(pos, ySplit))
        world.applyChange(
            org.micoli.micraft.protocol.BlockChange(BlockPos(8, 7, 9), straight)) // entry leg
        world.applyChange(
            org.micoli.micraft.protocol.BlockChange(BlockPos(8, 7, 7), straight)) // branch0 leg
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        val registry = RailNetworkRegistry(world)
        val before = registry.topologyAt(pos).single()
        assertIs<RailTopology.Segment>(before)
        assertEquals(3, before.positions.size, "entry + split + active north branch")

        val interactor = BlockInteractor(world, {}, railNetworkRegistry = registry)
        interactor.handleInteract(session, ClientMessage.BlockInteract(pos))

        val after = registry.topologyAt(pos).single()
        assertIs<RailTopology.Segment>(after)
        assertEquals(
            2,
            after.positions.size,
            "branch switched away from north — split is now alone with the entry leg")
    }
}
