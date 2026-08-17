package org.micoli.micraft.game.world.block

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

/** Rail blocks are non-cubic and rotatable on the standard 4x90 scheme, like LEGO_SLOPE. */
class RailBlockPlacementTest {

    private val railTypes =
        listOf(
            BlockType.RAIL_STRAIGHT,
            BlockType.RAIL_CURVE_90,
            BlockType.RAIL_CURVE_60,
            BlockType.RAIL_SLOPE_45,
            BlockType.RAIL_SLOPE_22,
            BlockType.RAIL_Y_SPLIT_90,
            BlockType.RAIL_CROSS,
        )

    private lateinit var savedBlocks: Map<BlockType, BlockDefinition>
    private lateinit var savedItems: Map<ItemType, ItemDefinition>

    @BeforeTest
    fun setUp() {
        // Touch TestFixtures once so its file-level ItemRegistry.load(...) initializer (only run
        // on first access, e.g. the first testWorld()/testSession() call anywhere in the JVM) has
        // already fired before we snapshot the registries below — otherwise a later first call
        // inside the @Test body would re-run that initializer and wipe out our overrides here.
        testWorld()
        savedBlocks = BlockRegistry.all().associateWith { BlockRegistry.get(it) }
        savedItems = ItemRegistry.keys().associateWith { ItemRegistry.get(it) }

        BlockRegistry.load(
            savedBlocks +
                railTypes.associateWith {
                    BlockDefinition(hardness = 1f, solid = true, isCubic = false, rotatable = true)
                })
        ItemRegistry.load(
            savedItems +
                railTypes.associate {
                    ItemType(it.id) to ItemDefinition(buildable = true, placesBlock = it)
                })
    }

    @AfterTest
    fun tearDown() {
        BlockRegistry.load(savedBlocks)
        ItemRegistry.load(savedItems)
    }

    private fun placer(
        broadcasts: MutableList<ServerMessage>,
        world: org.micoli.micraft.game.world.WorldState,
    ) = BlockPlacer(world, { broadcasts.add(it) }, {})

    @Test
    fun place_eachRailType_atEachRotation_roundTripsRotation() = runBlocking {
        for (railType in railTypes) {
            for (rotation in 0..3) {
                val broadcasts = mutableListOf<ServerMessage>()
                val world = testWorld()
                val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
                val item = ItemType(railType.id)
                session.inventory[item] = 1

                placer(broadcasts, world)
                    .handlePlace(
                        session,
                        ClientMessage.BlockPlace(BlockPos(8, 7, 8), item, rotation.toByte()))

                assertEquals(
                    railType, world.getBlock(8, 7, 8), "block type for $railType@$rotation")
                assertEquals(
                    rotation,
                    BlockState.rotation(world.getState(8, 7, 8)),
                    "rotation for $railType@$rotation")
            }
        }
    }
}
