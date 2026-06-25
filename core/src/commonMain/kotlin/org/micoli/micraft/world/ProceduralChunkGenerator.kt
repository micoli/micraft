package org.micoli.micraft.world

import kotlin.math.abs

class ProceduralChunkGenerator(
    private val seed: Long = 42L,
    private val biomeRegistry: BiomeRegistry = BiomeRegistry.default(),
    private val roadConfig: RoadConfig? = null,
) : ChunkGenerator {
    private val elevationNoise = PerlinNoise(seed)
    private val mountainNoise = PerlinNoise(seed + 2L)
    private val moistureNoise = PerlinNoise(seed + 1L)
    private val voronoi = VoronoiBiomeZones(seed, biomeRegistry, moistureNoise)
    private val roadVoronoi =
        roadConfig?.let {
            RoadVoronoiZones(seed, it) { wx, wz -> voronoi.sample(wx, wz).primary.id }
        }

    fun surfaceHeight(wx: Int, wz: Int, sample: VoronoiBiomeZones.ColumnSample): Int {
        val n = elevationNoise.octaveNoise(wx / 64.0, wz / 64.0, octaves = 6, persistence = 0.5)
        val t = (n + 1.0) / 2.0
        val eMin =
            sample.primary.elevationMin +
                sample.blendFactor * (sample.secondary.elevationMin - sample.primary.elevationMin)
        val eMax =
            sample.primary.elevationMax +
                sample.blendFactor * (sample.secondary.elevationMax - sample.primary.elevationMax)
        val baseY = eMin + t * (eMax - eMin)

        // Independent large-scale ridge noise (~400-block ranges) that lifts terrain
        // beyond the moisture biome's elevationMax, enabling altitude-constrained biomes
        // (mountains, tundra) to trigger in any moisture zone.
        val m = mountainNoise.octaveNoise(wx / 400.0, wz / 400.0, octaves = 4, persistence = 0.5)
        val mountainBoost = maxOf(0.0, m) * 60.0

        return (baseY + mountainBoost).toInt().coerceIn(4, WorldConstants.WORLD_MAX_Y - 1)
    }

    private fun terrainBlock(y: Int, col: ColumnData): BlockType {
        val h = col.h
        val b = col.blocks
        return when {
            y == 0 -> BlockType.BEDROCK
            y == h -> col.roadSurface ?: b.surface
            y > h - b.subsurfaceDepth && y < h -> b.subsurface
            y < h -> BlockType.STONE
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
                    ColumnData(
                        h,
                        voronoi.selectColumn(wx, wz, h, sample),
                        if (onRoad) roadConfig!!.surfaceFor(sample.primary.id) else null,
                    )
                }
            }

        val blocks = ByteArray(Chunk.TOTAL)
        for (lx in 0 until s) {
            for (ly in 0 until Chunk.SIZE_Y) {
                for (lz in 0 until s) {
                    blocks[Chunk.index(lx, ly, lz)] =
                        terrainBlock(ly, cols[lx][lz]).ordinal.toByte()
                }
            }
        }

        placeVegetation(blocks, ox, oz)

        return Chunk(pos, blocks)
    }

    override fun biomeAt(wx: Int, wz: Int): String = voronoi.sample(wx, wz).primary.id

    // ── Vegetation ────────────────────────────────────────────────────────────

    private fun vegetationHash(wx: Int, wz: Int, typeIdx: Int): Double {
        var h =
            seed xor
                (wx.toLong() * 2654435761L) xor
                (wz.toLong() * 2246822519L) xor
                (typeIdx.toLong() * 1234567891L)
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        h *= -4265267296055464877L
        h = h xor (h ushr 33)
        return (h and 0x7FFFFFFFFFFFFFFFL).toDouble() / Long.MAX_VALUE.toDouble()
    }

    private fun placeVegetation(blocks: ByteArray, ox: Int, oz: Int) {
        val margin = 3
        val s = WorldConstants.CHUNK_SIZE
        for (lx in -margin until s + margin) {
            for (lz in -margin until s + margin) {
                val wx = ox + lx
                val wz = oz + lz
                val sample = voronoi.sample(wx, wz)
                val surfaceY = surfaceHeight(wx, wz, sample)
                val biome = voronoi.effectiveBiome(wx, wz, surfaceY, sample)
                val surfaceBlock = voronoi.selectColumn(wx, wz, surfaceY, sample).surface

                if (roadVoronoi?.shouldBlockVegetation(sample.primary.id, wx, wz) == true) continue

                for ((idx, entry) in biome.vegetation.withIndex()) {
                    if (vegetationHash(wx, wz, idx) < entry.density) {
                        placeStructure(blocks, ox, oz, wx, wz, surfaceY, surfaceBlock, entry.type)
                        break
                    }
                }
            }
        }
    }

    private fun placeStructure(
        blocks: ByteArray,
        ox: Int,
        oz: Int,
        wx: Int,
        wz: Int,
        surfaceY: Int,
        surfaceBlock: BlockType,
        type: String,
    ) {
        when (type) {
            "oak_tree" ->
                if (surfaceBlock != BlockType.SAND && surfaceBlock != BlockType.SANDSTONE)
                    placeOakTree(blocks, ox, oz, wx, wz, surfaceY)
            "pine_tree" ->
                if (surfaceBlock != BlockType.SAND && surfaceBlock != BlockType.SANDSTONE)
                    placePineTree(blocks, ox, oz, wx, wz, surfaceY, BlockType.PINE_LEAVES)
            "pine_tree_snow" ->
                if (surfaceBlock != BlockType.SAND && surfaceBlock != BlockType.SANDSTONE)
                    placePineTree(blocks, ox, oz, wx, wz, surfaceY, BlockType.PINE_LEAVES_SNOW)
            "flower" ->
                if (surfaceBlock == BlockType.GRASS)
                    setVeg(blocks, ox, oz, wx, surfaceY + 1, wz, BlockType.FLOWER)
            "weed" ->
                if (surfaceBlock == BlockType.GRASS)
                    setVeg(blocks, ox, oz, wx, surfaceY + 1, wz, BlockType.WEED)
        }
    }

    private fun placeOakTree(blocks: ByteArray, ox: Int, oz: Int, wx: Int, wz: Int, surfaceY: Int) {
        val trunkH = 4 + (vegetationHash(wx, wz, 99) * 2).toInt()
        val trunkBase = surfaceY + 1
        val trunkTop = trunkBase + trunkH - 1

        for (y in trunkBase..trunkTop) {
            setVeg(blocks, ox, oz, wx, y, wz, BlockType.OAK_LOG)
        }

        // 5×5 minus corners at trunkTop-1 and trunkTop
        for (dy in -1..0) {
            for (dx in -2..2) for (dz in -2..2) {
                if (dx == 0 && dz == 0) continue
                if (abs(dx) == 2 && abs(dz) == 2) continue
                setVeg(blocks, ox, oz, wx + dx, trunkTop + dy, wz + dz, BlockType.OAK_LEAVES)
            }
        }
        // 3×3 at trunkTop+1
        for (dx in -1..1) for (dz in -1..1) {
            setVeg(blocks, ox, oz, wx + dx, trunkTop + 1, wz + dz, BlockType.OAK_LEAVES)
        }
        // Cross at trunkTop+2
        setVeg(blocks, ox, oz, wx, trunkTop + 2, wz, BlockType.OAK_LEAVES)
        setVeg(blocks, ox, oz, wx + 1, trunkTop + 2, wz, BlockType.OAK_LEAVES)
        setVeg(blocks, ox, oz, wx - 1, trunkTop + 2, wz, BlockType.OAK_LEAVES)
        setVeg(blocks, ox, oz, wx, trunkTop + 2, wz + 1, BlockType.OAK_LEAVES)
        setVeg(blocks, ox, oz, wx, trunkTop + 2, wz - 1, BlockType.OAK_LEAVES)
    }

    private fun placePineTree(
        blocks: ByteArray,
        ox: Int,
        oz: Int,
        wx: Int,
        wz: Int,
        surfaceY: Int,
        leavesType: BlockType,
    ) {
        val trunkH = 7 + (vegetationHash(wx, wz, 99) * 3).toInt()
        val trunkBase = surfaceY + 1
        val trunkTop = trunkBase + trunkH - 1

        for (y in trunkBase..trunkTop) {
            setVeg(blocks, ox, oz, wx, y, wz, BlockType.PINE_LOG)
        }

        // Apex
        setVeg(blocks, ox, oz, wx, trunkTop, wz, leavesType)
        // 3×3 at trunkTop-1
        for (dx in -1..1) for (dz in -1..1) {
            setVeg(blocks, ox, oz, wx + dx, trunkTop - 1, wz + dz, leavesType)
        }
        // 5×5 minus corners at trunkTop-2 and trunkTop-3
        for (dy in -3..-2) {
            for (dx in -2..2) for (dz in -2..2) {
                if (abs(dx) == 2 && abs(dz) == 2) continue
                setVeg(blocks, ox, oz, wx + dx, trunkTop + dy, wz + dz, leavesType)
            }
        }
    }

    private fun setVeg(
        blocks: ByteArray,
        ox: Int,
        oz: Int,
        wx: Int,
        wy: Int,
        wz: Int,
        type: BlockType
    ) {
        val lx = wx - ox
        val lz = wz - oz
        if (lx !in 0 until WorldConstants.CHUNK_SIZE) return
        if (lz !in 0 until WorldConstants.CHUNK_SIZE) return
        if (wy !in 1 until Chunk.SIZE_Y) return
        val idx = Chunk.index(lx, wy, lz)
        if (blocks[idx] == BlockType.AIR.ordinal.toByte()) {
            blocks[idx] = type.ordinal.toByte()
        }
    }

    private data class ColumnData(
        val h: Int,
        val blocks: VoronoiBiomeZones.ColumnBlocks,
        val roadSurface: BlockType? = null,
    )
}
