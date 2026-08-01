package org.micoli.micraft.game.world.block

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.PlainColor
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

/** Placement of plain-color variants: the color must come from the item, packed into the state. */
class PlainColorPlacementTest {

    private val brick = BlockType("LEGO_BRICK")
    private val brick2x1 = BlockType("LEGO_BRICK_2X1")
    private val plate = BlockType("LEGO_PLATE_2X2")

    private val blueItem = ItemType("LEGO_BRICK_BLUE")
    private val bigBlueItem = ItemType("LEGO_BRICK_2X1_BLUE")
    private val plateRedItem = ItemType("LEGO_PLATE_2X2_RED")

    private lateinit var savedBlocks: Map<BlockType, BlockDefinition>
    private lateinit var savedItems: Map<ItemType, ItemDefinition>

    @BeforeTest
    fun setUp() {
        savedBlocks = BlockRegistry.all().associateWith { BlockRegistry.get(it) }
        savedItems = ItemRegistry.keys().associateWith { ItemRegistry.get(it) }

        PlainColorRegistry.load(
            listOf(PlainColor("blue", 0, 85, 191), PlainColor("red", 196, 40, 27)))
        BlockRegistry.load(
            savedBlocks +
                mapOf(
                    brick to BlockDefinition(hardness = 1f, plainColorable = true),
                    brick2x1 to
                        BlockDefinition(
                            hardness = 1f, plainColorable = true, brickSize = listOf(2, 1, 1)),
                    plate to
                        BlockDefinition(
                            hardness = 1f,
                            plainColorable = true,
                            brickSize = listOf(2, 1, 2),
                            heightFraction = 0.333f),
                ))
        ItemRegistry.load(
            savedItems +
                mapOf(
                    blueItem to
                        ItemDefinition(buildable = true, placesBlock = brick, plainColor = "blue"),
                    bigBlueItem to
                        ItemDefinition(
                            buildable = true, placesBlock = brick2x1, plainColor = "blue"),
                    plateRedItem to
                        ItemDefinition(buildable = true, placesBlock = plate, plainColor = "red"),
                    ItemType("LEGO_BRICK") to ItemDefinition(buildable = true, placesBlock = brick),
                ))
    }

    @AfterTest
    fun tearDown() {
        BlockRegistry.load(savedBlocks)
        ItemRegistry.load(savedItems)
        PlainColorRegistry.load(emptyList())
    }

    private fun placer(
        broadcasts: MutableList<ServerMessage>,
        world: WorldState,
    ) = BlockPlacer(world, { broadcasts.add(it) }, {})

    private fun changesOf(broadcasts: List<ServerMessage>) =
        broadcasts.filterIsInstance<ServerMessage.WorldUpdate>().flatMap { it.changes }

    private fun entitiesOf(broadcasts: List<ServerMessage>) =
        broadcasts.filterIsInstance<ServerMessage.WorldUpdate>().flatMap { it.entityAdds }

    @Test
    fun place_colorVariant_packsColorAndRotationIntoState() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val world = testWorld()
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[blueItem] = 1

        placer(broadcasts, world)
            .handlePlace(session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), blueItem, 2))

        val state = world.getState(8, 7, 8)
        assertEquals(brick, world.getBlock(8, 7, 8))
        assertEquals(2, BlockState.rotation(state), "rotation kept from the intent")
        assertEquals(1, BlockState.colorIndex(state), "blue is palette index 1")
        assertEquals(state, changesOf(broadcasts).single().state)
    }

    @Test
    fun place_texturedItem_leavesColorIndexAtZero() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val world = testWorld()
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_BRICK")] = 1

        placer(broadcasts, world)
            .handlePlace(
                session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), ItemType("LEGO_BRICK"), 1))

        assertEquals(0, BlockState.colorIndex(world.getState(8, 7, 8)))
        assertEquals(1, BlockState.rotation(world.getState(8, 7, 8)))
    }

    @Test
    fun place_clientSentColorBits_areIgnored() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val world = testWorld()
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[ItemType("LEGO_BRICK")] = 1

        // Client claims color index 2 (red) while holding the textured item
        placer(broadcasts, world)
            .handlePlace(
                session,
                ClientMessage.BlockPlace(
                    BlockPos(8, 7, 8), ItemType("LEGO_BRICK"), BlockState.pack(1, 2)))

        assertEquals(0, BlockState.colorIndex(world.getState(8, 7, 8)), "color is server-derived")
    }

    @Test
    fun place_multiCell_appliesColorToSatellitesAndEntity() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val world = testWorld()
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[bigBlueItem] = 1

        placer(broadcasts, world)
            .handlePlace(session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), bigBlueItem, 0))

        val changes = changesOf(broadcasts)
        assertEquals(2, changes.size, "master + one satellite")
        assertTrue(
            changes.all { BlockState.colorIndex(it.state) == 1 }, "every cell carries the color")
        assertEquals(1, entitiesOf(broadcasts).single().colorIndex)
    }

    @Test
    fun place_fractionalPlate_carriesColorOnEntity() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val world = testWorld()
        val session = testSession(pos = Vec3(8.5f, 6f, 8.5f))
        session.inventory[plateRedItem] = 2

        placer(broadcasts, world)
            .handlePlace(session, ClientMessage.BlockPlace(BlockPos(8, 7, 8), plateRedItem, 0))

        val entity = entitiesOf(broadcasts).single()
        assertEquals(2, entity.colorIndex, "red is palette index 2")
        assertEquals(0, entity.yOffset)
        assertEquals(2, BlockState.colorIndex(changesOf(broadcasts).single().state))
    }
}
