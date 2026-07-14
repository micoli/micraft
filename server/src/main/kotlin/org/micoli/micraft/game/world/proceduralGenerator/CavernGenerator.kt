package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.biome.CavernConfig

data class CavernSeed(val wx: Int, val wy: Int, val wz: Int, val radius: Int)

class CavernGenerator(private val seed: Long, private val voronoiCellSize: Int = 256) {
    private val cavernNoise1 = PerlinNoise3D(seed + 100L)
    private val cavernNoise2 = PerlinNoise3D(seed + 200L)
    private val tunnelNoise1 = PerlinNoise3D(seed + 300L)
    private val tunnelNoise2 = PerlinNoise3D(seed + 400L)
    // Per-seed shape noise: sampled relative to each seed center to produce unique blobs
    private val patatoidNoise = PerlinNoise(seed + 500L)

    companion object {
        private const val STAIRCASE_CLEARANCE = 5
        private const val STAIRCASE_MAX_STEPS = 300
        private const val CAVERN_THRESHOLD = 0.25
        private const val TUNNEL_THRESHOLD = 0.10
        private const val CAVERN_SCALE_XZ = 55.0
        private const val CAVERN_SCALE_Y = 28.0
        private const val TUNNEL_SCALE_XZ = 20.0
        private const val TUNNEL_SCALE_Y = 10.0
        // How much the noise distorts the patatoid boundary (0=circle, higher=blobby)
        private const val PATATOID_DISTORT = 0.45
    }

    // Returns N deterministic 3D seed points for a Voronoi cell, each with an independent radius.
    internal fun cavernSeedPoints(cellX: Int, cellZ: Int, config: CavernConfig): List<CavernSeed> {
        val count = config.numberPerVoronoi.coerceAtLeast(1)
        return (0 until count).map { i ->
            var h =
                seed xor
                    (cellX.toLong() * -5045357748897851553L) xor
                    (cellZ.toLong() * 0x3C6EF372FE94F82AL) xor
                    (i.toLong() * -7046029254386353131L)
            h = h xor (h ushr 30)
            h *= -4658895341019938895L
            h = h xor (h ushr 27)
            h *= -7723592293110705685L
            h = h xor (h ushr 31)
            val wx = cellX * voronoiCellSize + ((h and 0xFFFFL) * voronoiCellSize / 65536L).toInt()
            val wz =
                cellZ * voronoiCellSize +
                    (((h ushr 16) and 0xFFFFL) * voronoiCellSize / 65536L).toInt()
            val yRange = (config.cavernMaxHeight - config.cavernMinHeight).coerceAtLeast(1)
            val wy = config.cavernMinHeight + (((h ushr 32) and 0xFFFFL) * yRange / 65536L).toInt()
            val radiusRange = (config.cavernMaxRadius - config.cavernMinRadius).coerceAtLeast(0)
            val radius =
                config.cavernMinRadius +
                    (((h ushr 48) and 0xFFFFL) * (radiusRange + 1) / 65536L).toInt()
            CavernSeed(wx, wy, wz, radius)
        }
    }

    // True if (wx, wy, wz) lies inside the patatoid of any nearby seed.
    // Each patatoid is a noise-distorted blob: boundary = radius * (1 + DISTORT * noise(local_pos))
    // The noise is sampled relative to the seed center, using the seed's world position as a phase
    // offset so every patatoid has a unique irregular shape.
    private fun isWithinPatatoid(wx: Int, wy: Int, wz: Int, config: CavernConfig): Boolean {
        val cx = floor(wx.toDouble() / voronoiCellSize).toInt()
        val cz = floor(wz.toDouble() / voronoiCellSize).toInt()
        val halfYRange = (config.cavernMaxHeight - config.cavernMinHeight) / 2.0
        for (dcx in -1..1) {
            for (dcz in -1..1) {
                for (s in cavernSeedPoints(cx + dcx, cz + dcz, config)) {
                    val dx = (wx - s.wx).toDouble()
                    val dz = (wz - s.wz).toDouble()
                    val distXZ = sqrt(dx * dx + dz * dz)
                    // Early-exit: skip noise eval if clearly out of max possible radius
                    if (distXZ > s.radius * (1.0 + PATATOID_DISTORT)) continue
                    val scale = s.radius * 0.5
                    // Phase offset makes each patatoid's blob shape unique
                    val phaseX = s.wx * 0.13
                    val phaseZ = s.wz * 0.09
                    val distort =
                        1.0 +
                            PATATOID_DISTORT *
                                patatoidNoise.noise(dx / scale + phaseX, dz / scale + phaseZ)
                    if (distXZ < s.radius * distort && abs(wy - s.wy) < halfYRange) return true
                }
            }
        }
        return false
    }

    private fun isCaved(wx: Int, wy: Int, wz: Int): Boolean {
        val x = wx.toDouble()
        val y = wy.toDouble()
        val z = wz.toDouble()
        val cn1 =
            cavernNoise1.octaveNoise(x / CAVERN_SCALE_XZ, y / CAVERN_SCALE_Y, z / CAVERN_SCALE_XZ)
        val cn2 =
            cavernNoise2.octaveNoise(x / CAVERN_SCALE_XZ, y / CAVERN_SCALE_Y, z / CAVERN_SCALE_XZ)
        if (abs(cn1) < CAVERN_THRESHOLD && abs(cn2) < CAVERN_THRESHOLD) return true
        val tn1 =
            tunnelNoise1.octaveNoise(x / TUNNEL_SCALE_XZ, y / TUNNEL_SCALE_Y, z / TUNNEL_SCALE_XZ)
        val tn2 =
            tunnelNoise2.octaveNoise(x / TUNNEL_SCALE_XZ, y / TUNNEL_SCALE_Y, z / TUNNEL_SCALE_XZ)
        return abs(tn1) < TUNNEL_THRESHOLD && abs(tn2) < TUNNEL_THRESHOLD
    }

    private fun blockHash(wx: Int, wy: Int, wz: Int): Double {
        val h =
            (wx.toLong() * 1664525L +
                wy.toLong() * 22695477L +
                wz.toLong() * 1013904223L +
                seed) and 0x7FFFFFFFL
        return h.toDouble() / 0x7FFFFFFFL.toDouble()
    }

    fun carve(
        blocks: ByteArray,
        ox: Int,
        oz: Int,
        surfaceAt: (lx: Int, lz: Int) -> Int,
        cavernsAt: (lx: Int, lz: Int) -> CavernConfig?,
    ) {
        val s = WorldConstants.CHUNK_SIZE
        val airWire = BlockRegistry.wireIndex(BlockType.AIR).toByte()
        val carved = BooleanArray(Chunk.TOTAL)
        val columnCaverns = Array(s) { lx -> Array(s) { lz -> cavernsAt(lx, lz) } }

        for (lx in 0 until s) {
            for (lz in 0 until s) {
                val config = columnCaverns[lx][lz] ?: continue
                val surfaceY = surfaceAt(lx, lz)
                val wx = ox + lx
                val wz = oz + lz
                val yMax = minOf(config.cavernMaxHeight, surfaceY - 2)
                for (y in maxOf(1, config.cavernMinHeight)..yMax) {
                    if (!isWithinPatatoid(wx, y, wz, config)) continue
                    if (isCaved(wx, y, wz)) {
                        val idx = Chunk.index(lx, y, lz)
                        blocks[idx] = airWire
                        carved[idx] = true
                    }
                }
            }
        }

        // Wall block pass
        for (lx in 0 until s) {
            for (lz in 0 until s) {
                val config = columnCaverns[lx][lz] ?: continue
                val surfaceY = surfaceAt(lx, lz)
                val wx = ox + lx
                val wz = oz + lz
                val yMax = minOf(config.cavernMaxHeight + 1, surfaceY - 1)
                for (y in maxOf(1, config.cavernMinHeight - 1)..yMax) {
                    val idx = Chunk.index(lx, y, lz)
                    if (blocks[idx] == airWire) continue
                    val adjacentToCave =
                        (lx > 0 && carved[Chunk.index(lx - 1, y, lz)]) ||
                            (lx < s - 1 && carved[Chunk.index(lx + 1, y, lz)]) ||
                            (lz > 0 && carved[Chunk.index(lx, y, lz - 1)]) ||
                            (lz < s - 1 && carved[Chunk.index(lx, y, lz + 1)]) ||
                            (y > 0 && carved[Chunk.index(lx, y - 1, lz)]) ||
                            (y < Chunk.SIZE_Y - 1 && carved[Chunk.index(lx, y + 1, lz)])
                    if (adjacentToCave) {
                        blocks[idx] = BlockRegistry.wireIndex(config.wallBlock).toByte()
                    }
                }
            }
        }

        // Ornament pass
        for (lx in 0 until s) {
            for (lz in 0 until s) {
                val config = columnCaverns[lx][lz] ?: continue
                if (!config.stalactitesPresent && !config.stalagmitesPresent) continue
                val wx = ox + lx
                val wz = oz + lz
                val wallWire = BlockRegistry.wireIndex(config.wallBlock).toByte()
                val yMax = minOf(config.cavernMaxHeight, Chunk.SIZE_Y - 2)

                if (config.stalactitesPresent) {
                    for (y in yMax downTo config.cavernMinHeight + 1) {
                        val idx = Chunk.index(lx, y, lz)
                        val idxBelow = Chunk.index(lx, y - 1, lz)
                        if (blocks[idx] != airWire && carved[idxBelow]) {
                            val length = (blockHash(wx, y * 31, wz) * 3).toInt() + 1
                            for (d in 1..length) {
                                val ty = y - d
                                if (ty < config.cavernMinHeight) break
                                val tidx = Chunk.index(lx, ty, lz)
                                if (!carved[tidx]) break
                                blocks[tidx] = wallWire
                            }
                        }
                    }
                }

                if (config.stalagmitesPresent) {
                    for (y in config.cavernMinHeight until yMax) {
                        val idx = Chunk.index(lx, y, lz)
                        val idxAbove = Chunk.index(lx, y + 1, lz)
                        if (blocks[idx] != airWire && carved[idxAbove]) {
                            val length = (blockHash(wx, y * 17 + 1, wz) * 3).toInt() + 1
                            for (d in 1..length) {
                                val ty = y + d
                                if (ty > yMax) break
                                val tidx = Chunk.index(lx, ty, lz)
                                if (!carved[tidx]) break
                                blocks[tidx] = wallWire
                            }
                        }
                    }
                }
            }
        }
    }

    // Cardinal direction (dx, dz) for the staircase from this seed, derived deterministically.
    // perpX/perpZ is the +1 offset for the second width block (perpendicular to travel direction).
    internal fun staircaseDirection(seed: CavernSeed): StaircaseDir {
        val h = seed.wx.toLong() * 1234567L xor seed.wz.toLong() * 7654321L xor this.seed
        return when (((h % 4) + 4) % 4) {
            0L -> StaircaseDir(dx = 1, dz = 0, perpX = 0, perpZ = 1)
            1L -> StaircaseDir(dx = -1, dz = 0, perpX = 0, perpZ = 1)
            2L -> StaircaseDir(dx = 0, dz = 1, perpX = 1, perpZ = 0)
            else -> StaircaseDir(dx = 0, dz = -1, perpX = 1, perpZ = 0)
        }
    }

    fun carveStaircases(
        blocks: ByteArray,
        ox: Int,
        oz: Int,
        localSurfaceAt: (lx: Int, lz: Int) -> Int,
        cellConfigAt: (cellX: Int, cellZ: Int) -> CavernConfig?,
    ) {
        val s = WorldConstants.CHUNK_SIZE
        val airWire = BlockRegistry.wireIndex(BlockType.AIR).toByte()
        val cellRadius = voronoiCellSize / s + 2

        val cxChunk = floor(ox.toDouble() / voronoiCellSize).toInt()
        val czChunk = floor(oz.toDouble() / voronoiCellSize).toInt()

        for (dcx in -cellRadius..cellRadius) {
            for (dcz in -cellRadius..cellRadius) {
                val cellX = cxChunk + dcx
                val cellZ = czChunk + dcz
                val config = cellConfigAt(cellX, cellZ) ?: continue
                if (!config.staircaseEnabled) continue
                for (seed in cavernSeedPoints(cellX, cellZ, config)) {
                    val startY = staircaseStartY(seed, config)
                    carveStaircaseSegment(blocks, ox, oz, seed, startY, localSurfaceAt, airWire, s)
                }
            }
        }
    }

    internal fun staircaseStartY(seed: CavernSeed, config: CavernConfig): Int {
        val halfRange = (config.cavernMaxHeight - config.cavernMinHeight) / 2
        return maxOf(seed.wy - halfRange, config.cavernMinHeight)
    }

    private fun carveStaircaseSegment(
        blocks: ByteArray,
        ox: Int,
        oz: Int,
        seed: CavernSeed,
        startY: Int,
        localSurfaceAt: (lx: Int, lz: Int) -> Int,
        airWire: Byte,
        chunkSize: Int,
    ) {
        val dir = staircaseDirection(seed)
        val maxSteps = min(STAIRCASE_MAX_STEPS, WorldConstants.WORLD_MAX_Y - startY)

        for (i in 0..maxSteps) {
            val wx1 = seed.wx + i * dir.dx
            val wz1 = seed.wz + i * dir.dz
            val wx2 = wx1 + dir.perpX
            val wz2 = wz1 + dir.perpZ
            val stepY = startY + i

            for (clearOffset in 1..STAIRCASE_CLEARANCE) {
                val clearY = stepY + clearOffset
                if (clearY >= Chunk.SIZE_Y) break

                val lx1 = wx1 - ox
                val lz1 = wz1 - oz
                if (lx1 in 0 until chunkSize && lz1 in 0 until chunkSize) {
                    if (clearY < localSurfaceAt(lx1, lz1)) {
                        blocks[Chunk.index(lx1, clearY, lz1)] = airWire
                    }
                }

                val lx2 = wx2 - ox
                val lz2 = wz2 - oz
                if (lx2 in 0 until chunkSize && lz2 in 0 until chunkSize) {
                    if (clearY < localSurfaceAt(lx2, lz2)) {
                        blocks[Chunk.index(lx2, clearY, lz2)] = airWire
                    }
                }
            }
        }
    }
}

data class StaircaseDir(val dx: Int, val dz: Int, val perpX: Int, val perpZ: Int)
