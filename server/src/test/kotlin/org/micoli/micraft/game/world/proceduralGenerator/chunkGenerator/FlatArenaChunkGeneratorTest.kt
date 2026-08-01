package org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState

private const val HALF = 32
private const val GROUND = 7
private const val WALL = 4

private fun arenaWorld() =
    WorldState(
        FlatArenaChunkGenerator(halfSize = HALF, groundY = GROUND, wallHeight = WALL),
        persistence = null,
    )

/** Generate every chunk the arena spans so getBlockIfLoaded answers everywhere. */
private fun WorldState.pregenerate() {
    val radius = HALF / WorldConstants.CHUNK_SIZE + 1
    for (cx in -radius..radius) for (cz in -radius..radius) getOrGenerate(ChunkPos(cx, cz))
}

class FlatArenaChunkGeneratorTest {

    @Test
    fun ground_isFlatGrassAtGroundY() {
        val world = arenaWorld().apply { pregenerate() }
        for (x in listOf(-20, -1, 0, 5, 20)) for (z in listOf(-20, -1, 0, 5, 20)) {
            assertEquals(BlockType.GRASS, world.getBlockIfLoaded(x, GROUND, z), "grass at $x,$z")
            assertEquals(BlockType.AIR, world.getBlockIfLoaded(x, GROUND + 1, z), "air above $x,$z")
        }
        assertEquals(BlockType.BEDROCK, world.getBlockIfLoaded(0, 0, 0))
        assertEquals(BlockType.STONE, world.getBlockIfLoaded(0, 1, 0))
        assertEquals(BlockType.DIRT, world.getBlockIfLoaded(0, GROUND - 1, 0))
    }

    @Test
    fun perimeter_isWalledUpToWallHeight() {
        val world = arenaWorld().apply { pregenerate() }
        for (y in GROUND + 1..GROUND + WALL) {
            assertEquals(BlockType.BEDROCK, world.getBlockIfLoaded(HALF, y, 0), "east wall at y=$y")
            assertEquals(
                BlockType.BEDROCK, world.getBlockIfLoaded(-HALF, y, 0), "west wall at y=$y")
            assertEquals(
                BlockType.BEDROCK, world.getBlockIfLoaded(0, y, HALF), "south wall at y=$y")
            assertEquals(
                BlockType.BEDROCK, world.getBlockIfLoaded(0, y, -HALF), "north wall at y=$y")
        }
        assertEquals(
            BlockType.AIR,
            world.getBlockIfLoaded(HALF, GROUND + WALL + 1, 0),
            "nothing above the wall")
    }

    @Test
    fun wall_isSolidSoNpcsCannotWalkThrough() {
        val world = arenaWorld().apply { pregenerate() }
        assertTrue(world.getBlockIfLoaded(HALF, GROUND + 1, 0).isSolid)
        assertTrue(world.getBlockIfLoaded(HALF, GROUND + 2, 0).isSolid)
    }

    @Test
    fun insideArena_hasNoWallBlocks() {
        val world = arenaWorld().apply { pregenerate() }
        for (x in -HALF + 1..HALF - 1 step 7) for (z in -HALF + 1..HALF - 1 step 7) {
            assertEquals(
                BlockType.AIR, world.getBlockIfLoaded(x, GROUND + 2, z), "clear space at $x,$z")
        }
    }

    @Test
    fun biomeAndZone_areReportedSoSpawnerFiltersWork() {
        val generator =
            FlatArenaChunkGenerator(
                halfSize = HALF, maxNpcs = 12, zoneLevel = 9, biomeId = "plains")
        assertEquals("plains", generator.biomeAt(3, 4))
        assertEquals("plains", generator.biomeDefinitionAt(3, 4).id)
        assertEquals(12, generator.biomeDefinitionAt(3, 4).maxNpcs)
        assertEquals(9, generator.zoneLevelAt(3, 4))
    }
}
