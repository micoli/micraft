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

private val FOOD = setOf(BlockType.FLOWER, BlockType.WEED)

private fun arenaWorld(vegetationDensity: Double = 0.0) =
    WorldState(
        FlatArenaChunkGenerator(
            halfSize = HALF,
            groundY = GROUND,
            wallHeight = WALL,
            vegetationDensity = vegetationDensity,
        ),
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
    fun vegetation_isScatteredOnTheFloorForHerbivores() {
        val world = arenaWorld(vegetationDensity = 0.2).apply { pregenerate() }
        var food = 0
        var cells = 0
        for (x in -HALF + 1..HALF - 1) for (z in -HALF + 1..HALF - 1) {
            cells++
            if (world.getBlockIfLoaded(x, GROUND + 1, z) in FOOD) food++
        }
        assertTrue(food > 0, "herbivores need FLOWER or WEED to graze")
        val ratio = food.toDouble() / cells
        assertTrue(
            ratio > 0.1 && ratio < 0.3, "density should land near the requested 0.2, was $ratio")
    }

    @Test
    fun vegetation_isDeterministicFromTheSeed() {
        val a = arenaWorld(vegetationDensity = 0.2).apply { pregenerate() }
        val b = arenaWorld(vegetationDensity = 0.2).apply { pregenerate() }
        for (x in -20..20 step 3) for (z in -20..20 step 3) {
            assertEquals(
                a.getBlockIfLoaded(x, GROUND + 1, z),
                b.getBlockIfLoaded(x, GROUND + 1, z),
                "same arena settings must produce the same pasture at $x,$z")
        }
    }

    @Test
    fun vegetation_neverGrowsOnTheWall() {
        val world = arenaWorld(vegetationDensity = 1.0).apply { pregenerate() }
        for (z in -HALF..HALF step 5) {
            assertEquals(BlockType.BEDROCK, world.getBlockIfLoaded(HALF, GROUND + 1, z))
            assertEquals(BlockType.BEDROCK, world.getBlockIfLoaded(-HALF, GROUND + 1, z))
        }
    }

    @Test
    fun outsideThePerimeter_isVoid() {
        // A floor outside the walls made the whole generated area habitable: the auto-spawner walks
        // every discovered chunk, so most NPCs ended up living outside the arena.
        val world = arenaWorld(vegetationDensity = 0.5).apply { pregenerate() }
        for (offset in 1..8) {
            for (y in 0..GROUND + WALL + 2) {
                assertEquals(
                    BlockType.AIR,
                    world.getBlockIfLoaded(HALF + offset, y, 0),
                    "nothing may exist east of the wall (y=$y)")
                assertEquals(
                    BlockType.AIR,
                    world.getBlockIfLoaded(0, y, -HALF - offset),
                    "nothing may exist north of the wall (y=$y)")
            }
        }
    }

    @Test
    fun outsideThePerimeter_offersNoSurfaceToSpawnOn() {
        val world = arenaWorld(vegetationDensity = 0.5).apply { pregenerate() }
        for (offset in 1..8) {
            assertTrue(
                (0..GROUND + WALL + 2).none {
                    world.getBlockIfLoaded(HALF + offset, it, 5).isSolid
                },
                "no solid block outside means the spawner finds no surface")
        }
    }

    @Test
    fun isInsideArena_excludesTheWallItself() {
        val generator = FlatArenaChunkGenerator(halfSize = HALF)
        assertTrue(generator.isInsideArena(0f, 0f))
        assertTrue(generator.isInsideArena(HALF - 0.5f, 0f))
        assertTrue(!generator.isInsideArena(HALF.toFloat(), 0f), "the wall is not walkable space")
        assertTrue(!generator.isInsideArena(HALF + 3f, 0f))
        assertTrue(!generator.isInsideArena(0f, -HALF - 1f))
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
