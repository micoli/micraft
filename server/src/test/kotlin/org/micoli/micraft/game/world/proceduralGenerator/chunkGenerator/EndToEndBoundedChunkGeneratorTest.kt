package org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState

private const val HALF = 4
private const val GROUND = 64

private fun boundedWorld() =
    WorldState(
        EndToEndBoundedChunkGenerator(halfChunksX = HALF, halfChunksZ = HALF, groundY = GROUND),
        persistence = null,
    )

private fun WorldState.pregenerate() {
    for (cx in -HALF - 1..HALF) for (cz in -HALF - 1..HALF) getOrGenerate(ChunkPos(cx, cz))
}

class EndToEndBoundedChunkGeneratorTest {

    @Test
    fun surface_isFlatGrassInsideBounds() {
        val world = boundedWorld().apply { pregenerate() }
        for (x in listOf(-63, -16, 0, 8, 63)) for (z in listOf(-63, -16, 0, 8, 63)) {
            assertEquals(BlockType.GRASS, world.getBlockIfLoaded(x, GROUND, z), "grass at $x,$z")
            assertEquals(BlockType.AIR, world.getBlockIfLoaded(x, GROUND + 1, z), "air above $x,$z")
            assertEquals(BlockType.BEDROCK, world.getBlockIfLoaded(x, 0, z), "bedrock at $x,$z")
            assertEquals(BlockType.DIRT, world.getBlockIfLoaded(x, GROUND - 1, z))
            assertEquals(BlockType.STONE, world.getBlockIfLoaded(x, 1, z))
        }
    }

    @Test
    fun outsideBounds_isVoid() {
        val world = boundedWorld().apply { pregenerate() }
        for (y in 0..GROUND + 2) {
            assertEquals(BlockType.AIR, world.getBlockIfLoaded(64, y, 0), "east of bounds y=$y")
            assertEquals(BlockType.AIR, world.getBlockIfLoaded(-65, y, 0), "west of bounds y=$y")
            assertEquals(BlockType.AIR, world.getBlockIfLoaded(0, y, 80), "south of bounds y=$y")
        }
    }

    @Test
    fun generate_isDeterministic() {
        val gen = EndToEndBoundedChunkGenerator(halfChunksX = HALF, halfChunksZ = HALF)
        val a = gen.generate(ChunkPos(0, 0))
        val b = gen.generate(ChunkPos(0, 0))
        for (lx in 0 until WorldConstants.CHUNK_SIZE) for (y in 0..GROUND + 1) for (lz in
            0 until WorldConstants.CHUNK_SIZE) {
            assertEquals(a.getBlock(lx, y, lz), b.getBlock(lx, y, lz), "block $lx,$y,$lz")
        }
    }

    @Test
    fun biomeAndZone_areBoundedAndNpcFree() {
        val gen =
            EndToEndBoundedChunkGenerator(
                halfChunksX = HALF, halfChunksZ = HALF, biomeId = "plains", zoneLevel = 3)
        assertEquals("plains", gen.biomeAt(8, 8))
        assertEquals(3, gen.zoneLevelAt(8, 8))
        assertEquals(0, gen.biomeDefinitionAt(8, 8)!!.maxNpcs)
        assertEquals("", gen.biomeAt(999, 0))
        assertEquals(0, gen.zoneLevelAt(999, 0))
        assertNull(gen.biomeDefinitionAt(999, 0))
    }
}
