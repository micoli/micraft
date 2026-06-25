package org.micoli.micraft.world

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.world.proceduralGenerator.house.HouseBiomeConfig
import org.micoli.micraft.world.proceduralGenerator.house.HouseConfig
import org.micoli.micraft.world.proceduralGenerator.house.HouseTypeConfig
import org.micoli.micraft.world.proceduralGenerator.house.HouseZones

class HouseGeneratorTest {

    private val cabinType =
        HouseTypeConfig(
            id = "cabin",
            widthMin = 7,
            widthMax = 7,
            depthMin = 7,
            depthMax = 7,
            floorsMin = 1,
            floorsMax = 1,
            roofTypes = listOf("flat"),
            roomsMin = 2,
            roomsMax = 2,
            doorsMin = 1,
            doorsMax = 1,
        )

    private val plainsBiome =
        HouseBiomeConfig(
            wallBlock = BlockType.OAK_LOG,
            roofBlock = BlockType.OAK_LOG,
            floorBlock = BlockType.DIRT,
            houseProbability = 1.0,
            clusterBonus = 0.0,
            allowedTypes = listOf("cabin"),
        )

    private val config =
        HouseConfig(
            enabled = true,
            gridCellSize = 16,
            clusterCheckRadius = 0,
            floorHeight = 4,
            maxHouseSize = 20,
            houseTypes = listOf(cabinType),
            defaultBiome = HouseBiomeConfig(),
            biomes = mapOf("plains" to plainsBiome),
        )

    private val plainsBiomeDef =
        BiomeDefinition(
            id = "plains",
            zones = listOf(BiomeZone(0.0, 1.0)),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT,
            elevationMin = 48,
            elevationMax = 78,
        )

    private val registry =
        BiomeRegistry.from(
            BiomeConfig(
                biomes = listOf(plainsBiomeDef),
                voronoiCellSize = 256,
                voronoiBlendRadius = 4,
            ))

    private fun makeGenerator() =
        ProceduralChunkGenerator(
            seed = 0L,
            biomeRegistry = registry,
            houseConfig = config,
        )

    @Test
    fun houseGenerationIsDeterministic() {
        val g1 = makeGenerator()
        val g2 = makeGenerator()
        val chunk1 = g1.generate(ChunkPos(0, 0))
        val chunk2 = g2.generate(ChunkPos(0, 0))
        assertTrue(
            chunk1.blocks.contentEquals(chunk2.blocks),
            "Same seed + config must produce identical chunks",
        )
    }

    @Test
    fun houseBlocksAppearedInChunk() {
        val gen = makeGenerator()
        val found = mutableSetOf<BlockType>()
        for (cx in -3..3) {
            for (cz in -3..3) {
                val chunk = gen.generate(ChunkPos(cx, cz))
                for (lx in 0 until WorldConstants.CHUNK_SIZE) {
                    for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                        for (y in 0 until Chunk.SIZE_Y) {
                            found.add(chunk.getBlock(lx, y, lz))
                        }
                    }
                }
            }
        }
        assertTrue(BlockType.OAK_LOG in found, "Expected OAK_LOG wall blocks in generated world")
        assertTrue(BlockType.DIRT in found, "Expected DIRT floor blocks in generated world")
    }

    @Test
    fun disabledConfig_noHouseBlocksAboveTerrain() {
        val disabledConfig = config.copy(enabled = false)
        val gen =
            ProceduralChunkGenerator(
                seed = 0L,
                biomeRegistry = registry,
                houseConfig = disabledConfig,
            )
        val chunk = gen.generate(ChunkPos(0, 0))
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                val topY =
                    (WorldConstants.WORLD_MAX_Y downTo 1).first {
                        chunk.getBlock(lx, it, lz) != BlockType.AIR
                    }
                val top = chunk.getBlock(lx, topY, lz)
                assertFalse(
                    top == BlockType.OAK_LOG,
                    "No OAK_LOG should appear when houses disabled at ($lx,$topY,$lz)",
                )
            }
        }
    }

    @Test
    fun flatRoofHouse_roofBlocksPresent() {
        val gen = makeGenerator()
        var foundRoof = false
        outer@ for (cx in -3..3) {
            for (cz in -3..3) {
                val chunk = gen.generate(ChunkPos(cx, cz))
                for (lx in 0 until WorldConstants.CHUNK_SIZE) {
                    for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                        for (y in (WorldConstants.WORLD_MAX_Y / 2) downTo 1) {
                            val b = chunk.getBlock(lx, y, lz)
                            if (b == BlockType.OAK_LOG) {
                                foundRoof = true
                                break@outer
                            }
                        }
                    }
                }
            }
        }
        assertTrue(foundRoof, "Expected roof blocks (OAK_LOG) to be placed")
    }

    @Test
    fun houseZones_hasHouseAtProbability1() {
        val zones =
            HouseZones(
                seed = 0L,
                config = config,
                biomeAt = { _, _ -> "plains" },
                surfaceY = { _, _ -> 60 },
            )
        var found = false
        for (cx in -5..5) {
            for (cz in -5..5) {
                if (zones.hasHouseAt(cx, cz)) {
                    found = true
                    break
                }
            }
        }
        assertTrue(found, "At probability=1.0, at least one house should be placed")
    }

    @Test
    fun houseZones_noHouseAtProbability0() {
        val zeroConfig =
            config.copy(biomes = mapOf("plains" to plainsBiome.copy(houseProbability = 0.0)))
        val zones =
            HouseZones(
                seed = 0L,
                config = zeroConfig,
                biomeAt = { _, _ -> "plains" },
                surfaceY = { _, _ -> 60 },
            )
        for (cx in -5..5) {
            for (cz in -5..5) {
                assertFalse(zones.hasHouseAt(cx, cz), "No house at probability=0 for ($cx,$cz)")
            }
        }
    }
}
