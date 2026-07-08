package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.test.Test
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.house.HouseBiomeConfig
import org.micoli.micraft.game.world.house.HouseTypeConfig
import org.micoli.micraft.game.world.house.PlacedHouse
import org.micoli.micraft.game.world.house.houseFloorBaseOffset
import org.micoli.micraft.game.world.house.houseFloorHeight
import org.micoli.micraft.game.world.house.renderIntoChunk

class RenderIntoChunkTest {

    private val rectType =
        HouseTypeConfig(
            id = "hut",
            widthMin = 5,
            widthMax = 5,
            depthMin = 5,
            depthMax = 5,
            floorsMin = 1,
            floorsMax = 1,
            roofTypes = listOf("flat"),
            roomsMin = 1,
            roomsMax = 1,
            doorsMin = 1,
            doorsMax = 1)

    private val templeType = rectType.copy(id = "circular_temple")

    private fun house(typeCfg: HouseTypeConfig) =
        PlacedHouse(
            anchorX = 2,
            anchorZ = 2,
            anchorY = 64,
            width = 5,
            depth = 5,
            floors = 1,
            roofType = "flat",
            typeCfg = typeCfg,
            materials =
                HouseBiomeConfig(
                    wallBlock = BlockType.STONE,
                    roofBlock = BlockType.STONE,
                    floorBlock = BlockType.STONE),
            houseSeed = 1L)

    private fun emptyChunkBlocks() = ByteArray(Chunk.TOTAL)

    private fun countNonAir(blocks: ByteArray): Int =
        blocks.count { it != BlockRegistry.wireIndex(BlockType.AIR).toByte() }

    @Test
    fun renderRectangular_placesWallAndFloorBlocks() {
        val blocks = emptyChunkBlocks()
        house(rectType).renderIntoChunk(blocks, 0, 0)
        assertTrue(countNonAir(blocks) > 0)
    }

    @Test
    fun renderCircularTemple_placesBlocks() {
        val blocks = emptyChunkBlocks()
        house(templeType).renderIntoChunk(blocks, 0, 0)
        assertTrue(countNonAir(blocks) > 0)
    }

    @Test
    fun render_isDeterministicForSameSeed() {
        val a = emptyChunkBlocks()
        val b = emptyChunkBlocks()
        house(rectType).renderIntoChunk(a, 0, 0)
        house(rectType).renderIntoChunk(b, 0, 0)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun houseFloorBaseOffset_groundFloorIsZero() {
        assertTrue(houseFloorBaseOffset(0, 4) == 0)
    }

    @Test
    fun houseFloorHeight_increasesPerFloor() {
        assertTrue(houseFloorHeight(1, 4) > houseFloorHeight(0, 4))
    }
}
