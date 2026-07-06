package org.micoli.micraft.world.proceduralGenerator.vegetation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.world.BlockRegistry
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.proceduralGenerator.ProceduralChunkGenerator

class VegetationGeneratorTest {

    @Test
    fun oakTreeBlocks_hasTrunkAtBaseColumn() {
        val blocks = oakTreeBlocks(10, 10, surfaceY = 64)
        val trunkBlock =
            blocks.first { it.first.x == 10 && it.first.z == 10 && it.second == BlockType.OAK_LOG }
        assertEquals(65, trunkBlock.first.y)
    }

    @Test
    fun oakTreeBlocks_isDeterministic() {
        assertEquals(oakTreeBlocks(5, 5, 50), oakTreeBlocks(5, 5, 50))
    }

    @Test
    fun oakTreeBlocks_containsLeaves() {
        val blocks = oakTreeBlocks(0, 0, 50)
        assertTrue(blocks.any { it.second == BlockType.OAK_LEAVES })
    }

    @Test
    fun pineTreeBlocks_hasTrunkLongerThanOak() {
        val pine = pineTreeBlocks(0, 0, 50, BlockType.PINE_LEAVES)
        val oak = oakTreeBlocks(0, 0, 50)
        val pineTrunk = pine.count { it.second == BlockType.PINE_LOG }
        val oakTrunk = oak.count { it.second == BlockType.OAK_LOG }
        assertTrue(pineTrunk > oakTrunk)
    }

    @Test
    fun pineTreeBlocks_usesGivenLeavesType() {
        val pine = pineTreeBlocks(0, 0, 50, BlockType.PINE_LEAVES_SNOW)
        assertTrue(pine.any { it.second == BlockType.PINE_LEAVES_SNOW })
    }

    @Test
    fun placeVegetation_doesNotThrow_andStaysWithinChunkAirSlots() {
        val generator = ProceduralChunkGenerator(seed = 11L)
        val blocks = ByteArray(Chunk.Companion.TOTAL)
        placeVegetation(generator, blocks, 0, 0)
        // No assertion on exact placement (density-dependent); just verify valid wire indices.
        for (b in blocks) {
            assertTrue((b.toInt() and 0xFF) < BlockRegistry.all().size)
        }
    }

    @Test
    fun placeVegetation_isDeterministicForSameSeed() {
        val genA = ProceduralChunkGenerator(seed = 99L)
        val genB = ProceduralChunkGenerator(seed = 99L)
        val blocksA = ByteArray(Chunk.Companion.TOTAL)
        val blocksB = ByteArray(Chunk.Companion.TOTAL)
        placeVegetation(genA, blocksA, 0, 0)
        placeVegetation(genB, blocksB, 0, 0)
        assertTrue(blocksA.contentEquals(blocksB))
    }
}
