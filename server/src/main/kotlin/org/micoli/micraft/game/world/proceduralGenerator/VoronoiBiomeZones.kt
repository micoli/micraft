package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.math.floor
import kotlin.math.sqrt
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.biome.BiomeDefinition
import org.micoli.micraft.game.world.biome.BiomeRegistry

class VoronoiBiomeZones(
    private val seed: Long,
    private val registry: BiomeRegistry,
    private val moistureNoise: PerlinNoise,
) {
    private val cellSize = registry.voronoiCellSize
    private val blendRadius = registry.voronoiBlendRadius

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

    data class VoronoiCell(val seedX: Int, val seedZ: Int, val biome: BiomeDefinition)

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
                    result.add(VoronoiCell(sx, sz, seedBiome(sx, sz)))
                }
            }
        }
        return result
    }

    private fun moistureAt(wx: Int, wz: Int): Double =
        ((moistureNoise.octaveNoise(wx / 200.0, wz / 200.0, octaves = 3) + 1.0) / 2.0).coerceIn(
            0.0, 0.9999)

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
            ((moistureNoise.octaveNoise(wx / 200.0, wz / 200.0, octaves = 3) + 1.0) / 2.0).coerceIn(
                0.0, 0.9999)
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
