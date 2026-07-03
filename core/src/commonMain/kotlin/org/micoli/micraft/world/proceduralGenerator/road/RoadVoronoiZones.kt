package org.micoli.micraft.world.proceduralGenerator.road

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
import org.micoli.micraft.world.proceduralGenerator.PerlinNoise

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

    data class RoadVertexSegment(val x1: Double, val z1: Double, val x2: Double, val z2: Double)

    fun roadVertexSegmentsInArea(wx1: Int, wz1: Int, wx2: Int, wz2: Int): List<RoadVertexSegment> {
        if (!config.enabled) return emptyList()
        val margin = 2
        val cxMin = floor(wx1.toDouble() / cellSize).toInt() - margin
        val cxMax = floor(wx2.toDouble() / cellSize).toInt() + margin
        val czMin = floor(wz1.toDouble() / cellSize).toInt() - margin
        val czMax = floor(wz2.toDouble() / cellSize).toInt() + margin

        // allSeeds[i] = [cellX, cellZ, seedX, seedZ]
        val allSeeds = ArrayList<IntArray>()
        val seedByCell = HashMap<Long, IntArray>()
        for (cx in cxMin..cxMax) {
            for (cz in czMin..czMax) {
                val (sx, sz) = seedPoint(cx, cz)
                val s = intArrayOf(cx, cz, sx, sz)
                allSeeds.add(s)
                seedByCell[cx.toLong() shl 32 or (cz.toLong() and 0xFFFFFFFFL)] = s
            }
        }
        val checkR2 = (cellSize * 5.0) * (cellSize * 5.0)

        val result = mutableListOf<RoadVertexSegment>()
        val seen = HashSet<String>()
        val dirs = listOf(Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1))

        for (si in allSeeds) {
            for ((dcx, dcz) in dirs) {
                val jKey = (si[0] + dcx).toLong() shl 32 or ((si[1] + dcz).toLong() and 0xFFFFFFFFL)
                val sj = seedByCell[jKey] ?: continue
                val ax = si[2]; val az = si[3]; val bx = sj[2]; val bz = sj[3]
                var cax = ax; var caz = az; var cbx = bx; var cbz = bz
                if (ax > bx || (ax == bx && az > bz)) { cax = bx; caz = bz; cbx = ax; cbz = az }
                val key = "$cax,$caz|$cbx,$cbz"
                if (!seen.add(key)) continue
                val prob = config.configFor(biomeAt((cax + cbx) / 2, (caz + cbz) / 2)).roadProbability
                if (edgeHash(cax, caz, cbx, cbz) >= prob) continue

                val mx = (cax + cbx) / 2.0; val mz = (caz + cbz) / 2.0
                val pdx = -(cbz - caz).toDouble(); val pdz = (cbx - cax).toDouble()
                val pLen = sqrt(pdx * pdx + pdz * pdz)
                var posV: Pair<Double, Double>? = null
                var negV: Pair<Double, Double>? = null
                var posVDistSq = Double.MAX_VALUE
                var negVDistSq = Double.MAX_VALUE

                for (sk in allSeeds) {
                    if (sk[0] == si[0] && sk[1] == si[1]) continue
                    if (sk[0] == sj[0] && sk[1] == sj[1]) continue
                    val dxm = sk[2] - mx; val dzm = sk[3] - mz
                    if (dxm * dxm + dzm * dzm > checkR2) continue
                    val projSeed = dxm * pdx + dzm * pdz
                    val v = voronoiCircumcenter(cax, caz, cbx, cbz, sk[2], sk[3]) ?: continue
                    val proj = (v.first - mx) * pdx + (v.second - mz) * pdz
                    // Skip obtuse triangles: seed and circumcenter must be on same side of edge
                    if (projSeed * proj <= 0.0) continue
                    val distSq = (v.first - mx) * (v.first - mx) + (v.second - mz) * (v.second - mz)
                    // Skip near-collinear triples: degenerate circumcenter shoots to infinity
                    if (distSq > checkR2) continue
                    if (proj > 0.0 && distSq < posVDistSq) { posV = v; posVDistSq = distSq }
                    else if (proj < 0.0 && distSq < negVDistSq) { negV = v; negVDistSq = distSq }
                }

                val edgeDist = sqrt((cbx - cax).toDouble().let { it * it } + (cbz - caz).toDouble().let { it * it })
                val fposV = posV ?: Pair(mx + pdx / pLen * edgeDist * 0.5, mz + pdz / pLen * edgeDist * 0.5)
                val fnegV = negV ?: Pair(mx - pdx / pLen * edgeDist * 0.5, mz - pdz / pLen * edgeDist * 0.5)
                result.add(RoadVertexSegment(fposV.first, fposV.second, fnegV.first, fnegV.second))
            }
        }
        return result
    }

    private fun voronoiCircumcenter(ax: Int, az: Int, bx: Int, bz: Int, cx: Int, cz: Int): Pair<Double, Double>? {
        val D = 2.0 * (ax * (bz - cz) + bx * (cz - az) + cx * (az - bz))
        if (abs(D) < 1e-10) return null
        val a2 = ax.toLong() * ax + az.toLong() * az
        val b2 = bx.toLong() * bx + bz.toLong() * bz
        val c2 = cx.toLong() * cx + cz.toLong() * cz
        return Pair(
            (a2 * (bz - cz) + b2 * (cz - az) + c2 * (az - bz)) / D,
            (a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax)) / D,
        )
    }

    fun isOnRoadAt(wx: Int, wz: Int): Boolean = isOnRoad(biomeAt(wx, wz), wx, wz)

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
