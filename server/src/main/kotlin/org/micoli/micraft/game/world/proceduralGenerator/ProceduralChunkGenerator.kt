package org.micoli.micraft.game.world.proceduralGenerator

import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.biome.BiomeRegistry
import org.micoli.micraft.game.world.biome.FillerEntry
import org.micoli.micraft.game.world.house.HouseConfig
import org.micoli.micraft.game.world.house.HouseZones
import org.micoli.micraft.game.world.house.renderIntoChunk
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.road.RoadConfig
import org.micoli.micraft.game.world.road.RoadVoronoiZones
import org.micoli.micraft.game.world.vegetation.placeVegetation
import org.micoli.micraft.player.Vec3

class ProceduralChunkGenerator(
    public val seed: Long = 42L,
    private val biomeRegistry: BiomeRegistry = BiomeRegistry.default(),
    private val roadConfig: RoadConfig? = null,
    private val houseConfig: HouseConfig? = null,
) : ChunkGenerator {
    private val elevationNoise = PerlinNoise(seed)
    private val mountainNoise = PerlinNoise(seed + 2L)
    private val moistureNoise = PerlinNoise(seed + 1L)
    private val waterNoise = PerlinNoise(seed + 3L)
    private val fillerNoise = PerlinNoise3D(seed + 5L)
    private val cavernGenerator = CavernGenerator(seed, biomeRegistry.voronoiCellSize)
    val voronoi = VoronoiBiomeZones(seed, biomeRegistry, moistureNoise)
    val roadVoronoi =
        roadConfig?.let {
            RoadVoronoiZones(seed, it) { wx, wz -> voronoi.sample(wx, wz).primary.id }
        }
    val houseZones =
        houseConfig?.let { cfg ->
            HouseZones(
                seed,
                cfg,
                biomeAt = { wx, wz -> voronoi.sample(wx, wz).primary.id },
                surfaceY = { wx, wz -> surfaceHeight(wx, wz, voronoi.sample(wx, wz)) },
            )
        }

    fun surfaceHeight(wx: Int, wz: Int, sample: VoronoiBiomeZones.ColumnSample): Int {
        val n = elevationNoise.octaveNoise(wx / 64.0, wz / 64.0, octaves = 6, persistence = 0.5)
        val t = (n + 1.0) / 2.0
        val s = sample.blendFactor.let { it * it * (3 - 2 * it) }
        val eMin =
            sample.secondary.elevationMin +
                s * (sample.primary.elevationMin - sample.secondary.elevationMin)
        val eMax =
            sample.secondary.elevationMax +
                s * (sample.primary.elevationMax - sample.secondary.elevationMax)
        val baseY = eMin + t * (eMax - eMin)

        // Independent large-scale ridge noise (~400-block ranges) that lifts terrain
        // beyond the moisture biome's elevationMax, enabling altitude-constrained biomes
        // (mountains, tundra) to trigger in any moisture zone.
        val m = mountainNoise.octaveNoise(wx / 400.0, wz / 400.0, octaves = 4, persistence = 0.5)
        val mountainBoost = maxOf(0.0, m) * 60.0

        return (baseY + mountainBoost).toInt().coerceIn(4, WorldConstants.WORLD_MAX_Y - 1)
    }

    private fun terrainBlock(wx: Int, wy: Int, wz: Int, col: ColumnData): BlockType {
        val h = col.h
        val b = col.blocks
        return when {
            wy == 0 -> BlockType.BEDROCK
            wy == h -> col.roadSurface ?: b.surface
            wy > h - b.subsurfaceDepth && wy < h -> b.subsurface
            wy < h -> b.fillers.selectFillerCompetitive(wx, wy, wz)
            wy > h && wy <= WorldConstants.WATER_LEVEL && col.isWaterColumn -> BlockType.WATER
            else -> BlockType.AIR
        }
    }

    override fun generate(pos: ChunkPos): Chunk {
        val ox = pos.cx * WorldConstants.CHUNK_SIZE
        val oz = pos.cz * WorldConstants.CHUNK_SIZE
        val s = WorldConstants.CHUNK_SIZE

        // Precompute per-column surface/biome data (16×16 = 256 calls instead of 262 400)
        val cols =
            Array(s) { x ->
                Array(s) { z ->
                    val wx = ox + x
                    val wz = oz + z
                    val sample = voronoi.sample(wx, wz)
                    val h = surfaceHeight(wx, wz, sample)
                    val onRoad = roadVoronoi?.isOnRoad(sample.primary.id, wx, wz) == true
                    val rate = sample.primary.waterSourceRate
                    val isWater =
                        rate > 0.0 &&
                            h < WorldConstants.WATER_LEVEL &&
                            waterNoise.octaveNoise(
                                wx / 32.0, wz / 32.0, octaves = 2, persistence = 0.5) >
                                (1.0 - rate * 2.0)
                    ColumnData(
                        h,
                        voronoi.selectColumn(wx, wz, h, sample),
                        if (onRoad) roadConfig!!.surfaceFor(sample.primary.id) else null,
                        isWater,
                    )
                }
            }

        val blocks = ByteArray(Chunk.TOTAL)
        for (lx in 0 until s) {
            for (ly in 0 until Chunk.SIZE_Y) {
                for (lz in 0 until s) {
                    blocks[Chunk.index(lx, ly, lz)] =
                        BlockRegistry.wireIndex(terrainBlock(ox + lx, ly, oz + lz, cols[lx][lz]))
                            .toByte()
                }
            }
        }

        cavernGenerator.carve(
            blocks,
            ox,
            oz,
            surfaceAt = { lx, lz -> cols[lx][lz].h },
            cavernsAt = { lx, lz ->
                val col = cols[lx][lz]
                voronoi.effectiveBiome(ox + lx, oz + lz, col.h).caverns
            },
        )

        cavernGenerator.carveStaircases(
            blocks,
            ox,
            oz,
            localSurfaceAt = { lx, lz -> cols[lx][lz].h },
            cellConfigAt = { cellX, cellZ ->
                val wx = cellX * biomeRegistry.voronoiCellSize + biomeRegistry.voronoiCellSize / 2
                val wz = cellZ * biomeRegistry.voronoiCellSize + biomeRegistry.voronoiCellSize / 2
                val sample = voronoi.sample(wx, wz)
                voronoi.effectiveBiome(wx, wz, surfaceHeight(wx, wz, sample)).caverns
            },
        )

        // here surfaceHeight is a ref to a fun
        placeVegetation(this, blocks, ox, oz)
        placeHouses(blocks, ox, oz)

        return Chunk(pos, blocks)
    }

    override fun biomeAt(wx: Int, wz: Int): String = voronoi.sample(wx, wz).primary.id

    fun namedCavernPoints(cellRadius: Int = 5): Map<String, Vec3> {
        val cellSize = biomeRegistry.voronoiCellSize
        val result = mutableMapOf<String, Vec3>()
        var seq = 0
        for (cx in -cellRadius..cellRadius) {
            for (cz in -cellRadius..cellRadius) {
                val wx = cx * cellSize + cellSize / 2
                val wz = cz * cellSize + cellSize / 2
                val biome = voronoi.sample(wx, wz).primary
                val config = biome.caverns ?: continue
                for (s in cavernGenerator.cavernSeedPoints(cx, cz, config)) {
                    result["cavern - ${biome.id}_$seq"] =
                        Vec3(s.wx.toFloat(), s.wy.toFloat(), s.wz.toFloat())
                    seq++
                }
            }
        }
        return result
    }

    fun namedStaircasePoints(cellRadius: Int = 5): Map<String, Vec3> {
        val cellSize = biomeRegistry.voronoiCellSize
        val result = mutableMapOf<String, Vec3>()
        var seq = 0
        for (cx in -cellRadius..cellRadius) {
            for (cz in -cellRadius..cellRadius) {
                val wx = cx * cellSize + cellSize / 2
                val wz = cz * cellSize + cellSize / 2
                val biome = voronoi.sample(wx, wz).primary
                val config = biome.caverns ?: continue
                if (!config.staircaseEnabled) continue
                for (s in cavernGenerator.cavernSeedPoints(cx, cz, config)) {
                    val dir = cavernGenerator.staircaseDirection(s)
                    val startY = cavernGenerator.staircaseStartY(s, config)
                    for (i in 0..300) {
                        val exitWx = s.wx + i * dir.dx
                        val exitWz = s.wz + i * dir.dz
                        val sample = voronoi.sample(exitWx, exitWz)
                        val surface = surfaceHeight(exitWx, exitWz, sample)
                        if (startY + i >= surface - 1) {
                            result["staircase - ${biome.id}_$seq"] =
                                Vec3(exitWx.toFloat(), surface.toFloat(), exitWz.toFloat())
                            seq++
                            break
                        }
                    }
                }
            }
        }
        return result
    }

    private fun placeHouses(blocks: ByteArray, ox: Int, oz: Int) {
        houseZones?.housesNear(ox, oz)?.forEach { house -> house.renderIntoChunk(blocks, ox, oz) }
    }

    private fun List<FillerEntry>.selectFillerCompetitive(wx: Int, wy: Int, wz: Int): BlockType {
        if (size == 1) return first().type
        val total = sumOf { it.density }
        val scale = 10.0
        var bestScore = Double.MIN_VALUE
        var bestType = last().type
        forEachIndexed { i, entry ->
            val ox = i * 73.7
            val oy = i * 211.9
            val oz = i * 157.3
            val n = fillerNoise.noise(wx / scale + ox, wy / scale + oy, wz / scale + oz)
            val score = n * (entry.density / total)
            if (score > bestScore) {
                bestScore = score
                bestType = entry.type
            }
        }
        return bestType
    }

    // ── Vegetation ────────────────────────────────────────────────────────────

    private data class ColumnData(
        val h: Int,
        val blocks: VoronoiBiomeZones.ColumnBlocks,
        val roadSurface: BlockType? = null,
        val isWaterColumn: Boolean = false,
    )
}
