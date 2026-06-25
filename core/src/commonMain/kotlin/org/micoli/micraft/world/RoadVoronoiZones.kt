package org.micoli.micraft.world

import kotlin.math.floor
import kotlin.math.sqrt

class RoadVoronoiZones(
    private val seed: Long,
    private val config: RoadConfig,
    private val biomeAt: (wx: Int, wz: Int) -> String,
) {
    private val displacementNoise = PerlinNoise(seed + 997L)
    private val cellSize = config.voronoiCellSize

    private data class EdgeInfo(
        val dist: Double,
        val s1x: Int,
        val s1z: Int,
        val s2x: Int,
        val s2z: Int,
    )

    private fun seedPoint(cellX: Int, cellZ: Int): Pair<Int, Int> {
        var h =
            (seed + 13L) xor
                (cellX.toLong() * 7046029254386353131L) xor
                (cellZ.toLong() * 0x6C62272E07BB0142L)
        h = h xor (h ushr 30)
        h *= -4658895341019938895L
        h = h xor (h ushr 27)
        h *= -7723592293110705685L
        h = h xor (h ushr 31)
        val offX = ((h and 0xFFFFL) * cellSize.toLong() / 65536L).toInt()
        val offZ = (((h ushr 16) and 0xFFFFL) * cellSize.toLong() / 65536L).toInt()
        return Pair(cellX * cellSize + offX, cellZ * cellSize + offZ)
    }

    private fun displaced(wx: Int, wz: Int): Pair<Double, Double> {
        val freq = config.displacementFrequency
        val scale = config.displacementScale
        val dx = displacementNoise.octaveNoise(wx * freq, wz * freq, 3, 0.5) * scale
        val dz = displacementNoise.octaveNoise(wx * freq + 31.41, wz * freq + 27.18, 3, 0.5) * scale
        return Pair(wx + dx, wz + dz)
    }

    private fun edgeInfo(wx: Int, wz: Int): EdgeInfo {
        val (qx, qz) = displaced(wx, wz)
        val cx = floor(qx / cellSize).toInt()
        val cz = floor(qz / cellSize).toInt()
        var d1 = Double.MAX_VALUE
        var d2 = Double.MAX_VALUE
        var s1x = 0
        var s1z = 0
        var s2x = 0
        var s2z = 0
        for (dcx in -1..1) for (dcz in -1..1) {
            val (sx, sz) = seedPoint(cx + dcx, cz + dcz)
            val dx = qx - sx
            val dz = qz - sz
            val dist = dx * dx + dz * dz
            if (dist < d1) {
                d2 = d1
                s2x = s1x
                s2z = s1z
                d1 = dist
                s1x = sx
                s1z = sz
            } else if (dist < d2) {
                d2 = dist
                s2x = sx
                s2z = sz
            }
        }
        return EdgeInfo(sqrt(d2) - sqrt(d1), s1x, s1z, s2x, s2z)
    }

    // Deterministic [0,1] value unique to each edge (order-independent).
    private fun edgeHash(s1x: Int, s1z: Int, s2x: Int, s2z: Int): Double {
        val ax: Int
        val az: Int
        val bx: Int
        val bz: Int
        if (s1x < s2x || (s1x == s2x && s1z < s2z)) {
            ax = s1x
            az = s1z
            bx = s2x
            bz = s2z
        } else {
            ax = s2x
            az = s2z
            bx = s1x
            bz = s1z
        }
        var h =
            seed xor
                (ax.toLong() * 2654435761L) xor
                (az.toLong() * 2246822519L) xor
                (bx.toLong() * 374761393L) xor
                (bz.toLong() * 1234567891L)
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return (h and 0x7FFFFFFFFFFFFFFFL).toDouble() / Long.MAX_VALUE.toDouble()
    }

    private fun edgeExists(info: EdgeInfo): Boolean {
        val centerX = (info.s1x + info.s2x) / 2
        val centerZ = (info.s1z + info.s2z) / 2
        val centerBiome = biomeAt(centerX, centerZ)
        val prob = config.configFor(centerBiome).roadProbability
        return edgeHash(info.s1x, info.s1z, info.s2x, info.s2z) < prob
    }

    fun isOnRoad(columnBiomeId: String, wx: Int, wz: Int): Boolean {
        if (!config.enabled) return false
        val info = edgeInfo(wx, wz)
        if (!edgeExists(info)) return false
        return info.dist < config.configFor(columnBiomeId).width / 2.0
    }

    fun shouldBlockVegetation(columnBiomeId: String, wx: Int, wz: Int): Boolean {
        if (!config.enabled) return false
        if (config.vegetationAllowedOnRoad && config.minVegetationDistanceFromRoad == 0)
            return false
        val info = edgeInfo(wx, wz)
        if (!edgeExists(info)) return false
        val threshold =
            config.configFor(columnBiomeId).width / 2.0 + config.minVegetationDistanceFromRoad
        return info.dist < threshold
    }
}
