package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.biome.BiomeDefinition
import org.micoli.micraft.game.world.biome.BiomeRegistry

class VoronoiBiomeZones(
    private val seed: Long,
    private val registry: BiomeRegistry,
    private val moistureNoise: PerlinNoise,
) {
    private val cellSize = registry.voronoiCellSize
    private val blendRadius = registry.voronoiBlendRadius
    private val LEVEL_MAX_DIST = 4096.0

    private fun seedPoint(cellX: Int, cellZ: Int): Pair<Int, Int> {
        var h =
            seed xor
                (cellX.toLong() * -7046029254386353131L) xor
                (cellZ.toLong() * 0x6C62272E07BB0142L)
        h = h xor (h ushr 30)
        h *= -4658895341019938895L
        h = h xor (h ushr 27)
        h *= -7723592293110705685L
        h = h xor (h ushr 31)
        val offX = ((h and 0xFFFFL) * cellSize / 65536L).toInt()
        val offZ = (((h ushr 16) and 0xFFFFL) * cellSize / 65536L).toInt()
        return Pair(cellX * cellSize + offX, cellZ * cellSize + offZ)
    }

    private fun seedBiome(sx: Int, sz: Int): BiomeDefinition {
        val raw = moistureNoise.octaveNoise(sx / 200.0, sz / 200.0, octaves = 3, persistence = 0.6)
        val m = ((raw + 1.0) / 2.0).coerceIn(0.0, 0.9999)
        return registry.selectByMoisture(m)
    }

    data class ColumnSample(
        val primary: BiomeDefinition,
        val secondary: BiomeDefinition,
        val blendFactor: Double,
        val primarySeedX: Int = 0,
        val primarySeedZ: Int = 0,
    )

    fun sample(wx: Int, wz: Int): ColumnSample {
        val cx = floor(wx.toDouble() / cellSize).toInt()
        val cz = floor(wz.toDouble() / cellSize).toInt()
        var d1 = Double.MAX_VALUE
        var d2 = Double.MAX_VALUE
        var b1: BiomeDefinition? = null
        var b2: BiomeDefinition? = null
        var sx1 = 0
        var sz1 = 0
        for (dcx in -1..1) for (dcz in -1..1) {
            val (sx, sz) = seedPoint(cx + dcx, cz + dcz)
            val dx = (wx - sx).toDouble()
            val dz = (wz - sz).toDouble()
            val dist = dx * dx + dz * dz
            if (dist < d1) {
                d2 = d1
                b2 = b1
                d1 = dist
                b1 = seedBiome(sx, sz)
                sx1 = sx
                sz1 = sz
            } else if (dist < d2) {
                d2 = dist
                b2 = seedBiome(sx, sz)
            }
        }
        val blend = ((sqrt(d2) - sqrt(d1)) / (2.0 * blendRadius)).coerceIn(0.0, 1.0)
        return ColumnSample(b1!!, b2 ?: b1, blend, sx1, sz1)
    }

    data class VoronoiCell(
        val seedX: Int,
        val seedZ: Int,
        val biome: BiomeDefinition,
        val name: String,
        val level: Int,
    )

    fun cellName(cellX: Int, cellZ: Int): String = FantasyNameGenerator.generate(seed, cellX, cellZ)

    private fun cellLevel(seedX: Int, seedZ: Int): Int {
        val dist = sqrt((seedX.toLong() * seedX + seedZ.toLong() * seedZ).toDouble())
        val distFraction = (dist / LEVEL_MAX_DIST).coerceIn(0.0, 1.0)
        return (distFraction * (WorldConstants.RPG_LEVEL_MAX - 1) + 1)
            .roundToInt()
            .coerceIn(1, WorldConstants.RPG_LEVEL_MAX)
    }

    fun zoneLevelAt(wx: Int, wz: Int): Int {
        val cx = floor(wx.toDouble() / cellSize).toInt()
        val cz = floor(wz.toDouble() / cellSize).toInt()
        var minDist = Double.MAX_VALUE
        var nearestSx = 0
        var nearestSz = 0
        for (dcx in -1..1) for (dcz in -1..1) {
            val (sx, sz) = seedPoint(cx + dcx, cz + dcz)
            val dx = (wx - sx).toDouble()
            val dz = (wz - sz).toDouble()
            val dist = dx * dx + dz * dz
            if (dist < minDist) {
                minDist = dist
                nearestSx = sx
                nearestSz = sz
            }
        }
        return cellLevel(nearestSx, nearestSz)
    }

    fun cells(centerX: Int, centerZ: Int, radiusBlocks: Int): List<VoronoiCell> {
        val minCX = floor((centerX - radiusBlocks).toDouble() / cellSize).toInt() - 1
        val maxCX = floor((centerX + radiusBlocks).toDouble() / cellSize).toInt() + 1
        val minCZ = floor((centerZ - radiusBlocks).toDouble() / cellSize).toInt() - 1
        val maxCZ = floor((centerZ + radiusBlocks).toDouble() / cellSize).toInt() + 1
        val r2 = radiusBlocks.toLong() * radiusBlocks
        val result = mutableListOf<VoronoiCell>()
        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                val (sx, sz) = seedPoint(cx, cz)
                val dx = (sx - centerX).toLong()
                val dz = (sz - centerZ).toLong()
                if (dx * dx + dz * dz <= r2) {
                    result.add(
                        VoronoiCell(sx, sz, seedBiome(sx, sz), cellName(cx, cz), cellLevel(sx, sz)))
                }
            }
        }
        return result
    }

    data class VoronoiEdge(val x1: Float, val z1: Float, val x2: Float, val z2: Float)

    private fun circumcenter(
        x1: Double,
        z1: Double,
        x2: Double,
        z2: Double,
        x3: Double,
        z3: Double
    ): Pair<Double, Double>? {
        val ax = x2 - x1
        val az = z2 - z1
        val bx = x3 - x1
        val bz = z3 - z1
        val D = 2.0 * (ax * bz - az * bx)
        if (abs(D) < 1e-10) return null
        val ux = (bz * (ax * ax + az * az) - az * (bx * bx + bz * bz)) / D
        val uz = (ax * (bx * bx + bz * bz) - bx * (ax * ax + az * az)) / D
        return (x1 + ux) to (z1 + uz)
    }

    fun computeBorderEdges(centerX: Int, centerZ: Int, radiusBlocks: Int): List<VoronoiEdge> {
        val margin = cellSize * 2
        val minCX = floor((centerX - radiusBlocks - margin).toDouble() / cellSize).toInt() - 2
        val maxCX = floor((centerX + radiusBlocks + margin).toDouble() / cellSize).toInt() + 2
        val minCZ = floor((centerZ - radiusBlocks - margin).toDouble() / cellSize).toInt() - 2
        val maxCZ = floor((centerZ + radiusBlocks + margin).toDouble() / cellSize).toInt() + 2

        val seedGrid = HashMap<Long, Pair<Int, Int>>()
        for (cx in minCX..maxCX) for (cz in minCZ..maxCZ) {
            seedGrid[cx.toLong() shl 32 or (cz.toLong() and 0xFFFFFFFFL)] = seedPoint(cx, cz)
        }
        fun cachedSeed(cx: Int, cz: Int) =
            seedGrid[cx.toLong() shl 32 or (cz.toLong() and 0xFFFFFFFFL)] ?: seedPoint(cx, cz)

        fun seedId(sx: Int, sz: Int) = sx.toLong() shl 32 or (sz.toLong() and 0xFFFFFFFFL)
        fun pairKey(a: Long, b: Long) = if (a <= b) a to b else b to a

        // vertex per edge pair: map pair key → up to 2 circumcenters
        val edgeVertices = HashMap<Pair<Long, Long>, MutableList<Pair<Double, Double>>>()
        val processedTriples = HashSet<Triple<Long, Long, Long>>()

        for (cx in minCX + 1 until maxCX) {
            for (cz in minCZ + 1 until maxCZ) {
                // Collect unique seeds from 3x3 neighbourhood
                val neighbors =
                    buildList {
                            for (dx in -1..1) for (dz in -1..1) add(cachedSeed(cx + dx, cz + dz))
                        }
                        .distinctBy { (sx, sz) -> seedId(sx, sz) }

                val n = neighbors.size
                for (i in 0 until n) {
                    val (sx1, sz1) = neighbors[i]
                    for (j in i + 1 until n) {
                        val (sx2, sz2) = neighbors[j]
                        for (k in j + 1 until n) {
                            val (sx3, sz3) = neighbors[k]

                            val ids =
                                listOf(seedId(sx1, sz1), seedId(sx2, sz2), seedId(sx3, sz3))
                                    .sorted()
                            val tripleKey = Triple(ids[0], ids[1], ids[2])
                            if (!processedTriples.add(tripleKey)) continue

                            val V =
                                circumcenter(
                                    sx1.toDouble(),
                                    sz1.toDouble(),
                                    sx2.toDouble(),
                                    sz2.toDouble(),
                                    sx3.toDouble(),
                                    sz3.toDouble()) ?: continue
                            val (vx, vz) = V

                            // Validate: no other seed closer than circumradius
                            val R2 = (vx - sx1) * (vx - sx1) + (vz - sz1) * (vz - sz1)
                            val gvx = floor(vx / cellSize).toInt()
                            val gvz = floor(vz / cellSize).toInt()
                            var valid = true
                            outer@ for (dvx in -1..1) for (dvz in -1..1) {
                                val (osx, osz) = cachedSeed(gvx + dvx, gvz + dvz)
                                if ((osx == sx1 && osz == sz1) ||
                                    (osx == sx2 && osz == sz2) ||
                                    (osx == sx3 && osz == sz3))
                                    continue
                                if ((vx - osx) * (vx - osx) + (vz - osz) * (vz - osz) < R2 - 1.0) {
                                    valid = false
                                    break@outer
                                }
                            }
                            if (!valid) continue

                            val id1 = seedId(sx1, sz1)
                            val id2 = seedId(sx2, sz2)
                            val id3 = seedId(sx3, sz3)
                            edgeVertices.getOrPut(pairKey(id1, id2)) { mutableListOf() }.add(V)
                            edgeVertices.getOrPut(pairKey(id1, id3)) { mutableListOf() }.add(V)
                            edgeVertices.getOrPut(pairKey(id2, id3)) { mutableListOf() }.add(V)
                        }
                    }
                }
            }
        }

        return edgeVertices.values
            .filter { it.size >= 2 }
            .map { verts ->
                VoronoiEdge(
                    verts[0].first.toFloat(),
                    verts[0].second.toFloat(),
                    verts[1].first.toFloat(),
                    verts[1].second.toFloat())
            }
    }

    private fun moistureAt(wx: Int, wz: Int): Double =
        ((moistureNoise.octaveNoise(wx / 200.0, wz / 200.0, octaves = 3, persistence = 0.6) + 1.0) /
                2.0)
            .coerceIn(0.0, 0.9999)

    private fun columnHash(wx: Int, wz: Int): Double {
        val h = (wx.toLong() * 1664525L + wz.toLong() * 1013904223L + seed) and 0x7FFFFFFFL
        return h.toDouble() / 0x7FFFFFFFL.toDouble()
    }

    data class ColumnBlocks(
        val surface: BlockType,
        val subsurface: BlockType,
        val subsurfaceDepth: Int,
        val fillers: List<org.micoli.micraft.game.world.biome.FillerEntry>,
    )

    fun effectiveBiome(
        wx: Int,
        wz: Int,
        surfaceY: Int,
        col: ColumnSample = sample(wx, wz)
    ): BiomeDefinition {
        val moisture =
            ((moistureNoise.octaveNoise(wx / 200.0, wz / 200.0, octaves = 3, persistence = 0.6) +
                    1.0) / 2.0)
                .coerceIn(0.0, 0.9999)
        return registry.altitudeOverride(surfaceY, moisture) ?: col.primary
    }

    fun selectColumn(
        wx: Int,
        wz: Int,
        surfaceY: Int,
        col: ColumnSample = sample(wx, wz)
    ): ColumnBlocks {
        val moisture = moistureAt(wx, wz)
        val override = registry.altitudeOverride(surfaceY, moisture)
        return if (override != null) {
            ColumnBlocks(
                override.surface, override.subsurface, override.subsurfaceDepth, override.fillers)
        } else {
            val surf =
                if (col.blendFactor > columnHash(wx, wz)) col.primary.surface
                else col.secondary.surface
            ColumnBlocks(
                surf, col.primary.subsurface, col.primary.subsurfaceDepth, col.primary.fillers)
        }
    }
}
