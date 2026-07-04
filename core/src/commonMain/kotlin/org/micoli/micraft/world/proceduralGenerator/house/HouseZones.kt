package org.micoli.micraft.world.proceduralGenerator.house

import kotlin.math.floor
import org.micoli.micraft.world.WorldConstants

class HouseZones(
    private val seed: Long,
    private val config: HouseConfig,
    private val biomeAt: (wx: Int, wz: Int) -> String,
    private val surfaceY: (wx: Int, wz: Int) -> Int,
) {
    private val cellSize = config.gridCellSize

    // Deterministic hash [0,1] for a cell
    private fun cellHash(cx: Int, cz: Int, salt: Int): Double {
        var h =
            seed xor
                (cx.toLong() * 7046029254386353131L) xor
                (cz.toLong() * 0x6C62272E07BB0142L) xor
                (salt.toLong() * 2654435761L)
        h = h xor (h ushr 30)
        h *= -4658895341019938895L
        h = h xor (h ushr 27)
        h *= -7723592293110705685L
        h = h xor (h ushr 31)
        return (h and 0x7FFFFFFFFFFFFFFFL).toDouble() / Long.MAX_VALUE.toDouble()
    }

    // Anchor point within a cell (deterministic offset)
    private fun anchorPoint(cx: Int, cz: Int): Pair<Int, Int> {
        val ox = (cellHash(cx, cz, 1) * (cellSize - 1)).toInt()
        val oz = (cellHash(cx, cz, 2) * (cellSize - 1)).toInt()
        return Pair(cx * cellSize + ox, cz * cellSize + oz)
    }

    private fun neighbourHasHouseBaseProbability(ncx: Int, ncz: Int): Boolean {
        val (ax, az) = anchorPoint(ncx, ncz)
        val biomeId = biomeAt(ax, az)
        val biomeCfg = config.configFor(biomeId)
        if (biomeCfg.allowedTypes.isEmpty()) return false
        return cellHash(ncx, ncz, 0) < biomeCfg.houseProbability
    }

    private fun clusterCount(cx: Int, cz: Int): Int {
        var count = 0
        val r = config.clusterCheckRadius
        for (dcx in -r..r) {
            for (dcz in -r..r) {
                if (dcx == 0 && dcz == 0) continue
                if (neighbourHasHouseBaseProbability(cx + dcx, cz + dcz)) count++
            }
        }
        return count
    }

    fun hasHouseAt(cx: Int, cz: Int): Boolean {
        if (!config.enabled) return false
        val (ax, az) = anchorPoint(cx, cz)
        val biomeId = biomeAt(ax, az)
        val biomeCfg = config.configFor(biomeId)
        if (biomeCfg.allowedTypes.isEmpty()) return false
        val base = biomeCfg.houseProbability
        val bonus = biomeCfg.clusterBonus * clusterCount(cx, cz)
        val prob = (base + bonus).coerceIn(0.0, 1.0)
        return cellHash(cx, cz, 0) < prob
    }

    fun housesInArea(x1: Int, z1: Int, x2: Int, z2: Int): List<PlacedHouse> {
        if (!config.enabled) return emptyList()
        val cxMin = floor(x1.toDouble() / cellSize).toInt()
        val cxMax = floor(x2.toDouble() / cellSize).toInt()
        val czMin = floor(z1.toDouble() / cellSize).toInt()
        val czMax = floor(z2.toDouble() / cellSize).toInt()
        return buildList {
            for (cx in cxMin..cxMax) for (cz in czMin..czMax) if (hasHouseAt(cx, cz))
                buildHouse(cx, cz)?.let { add(it) }
        }
    }

    fun housesNear(ox: Int, oz: Int): List<PlacedHouse> {
        if (!config.enabled) return emptyList()
        val maxSize = config.maxHouseSize
        val chunkSize = WorldConstants.CHUNK_SIZE

        val cxMin = floor((ox - maxSize).toDouble() / cellSize).toInt()
        val cxMax = floor((ox + chunkSize + maxSize).toDouble() / cellSize).toInt()
        val czMin = floor((oz - maxSize).toDouble() / cellSize).toInt()
        val czMax = floor((oz + chunkSize + maxSize).toDouble() / cellSize).toInt()

        val result = mutableListOf<PlacedHouse>()
        for (cx in cxMin..cxMax) {
            for (cz in czMin..czMax) {
                if (!hasHouseAt(cx, cz)) continue
                val house = buildHouse(cx, cz) ?: continue
                // Check bounding box overlaps chunk
                if (house.anchorX + house.width <= ox) continue
                if (house.anchorX >= ox + chunkSize) continue
                if (house.anchorZ + house.depth <= oz) continue
                if (house.anchorZ >= oz + chunkSize) continue
                result.add(house)
            }
        }
        return result
    }

    internal fun buildHouse(cx: Int, cz: Int): PlacedHouse? {
        val (ax, az) = anchorPoint(cx, cz)
        val biomeId = biomeAt(ax, az)
        val biomeCfg = config.configFor(biomeId)
        if (biomeCfg.allowedTypes.isEmpty()) return null

        val houseSeed = seed xor (cx.toLong() * 374761393L) xor (cz.toLong() * 1234567891L)
        val weightedTypes = biomeCfg.typeRates.entries.filter { it.value > 0 }
        val totalWeight = weightedTypes.sumOf { it.value }
        val typeId =
            if (totalWeight <= 0.0) {
                return null
            } else {
                var r = cellHash(cx, cz, 3) * totalWeight
                var selected = weightedTypes.last().key
                for ((id, weight) in weightedTypes) {
                    r -= weight
                    if (r <= 0) {
                        selected = id
                        break
                    }
                }
                selected
            }
        val typeCfg = config.houseTypes.find { it.id == typeId } ?: return null

        fun pick(min: Int, max: Int, salt: Int) =
            min + (cellHash(cx, cz, salt) * (max - min + 1)).toInt().coerceIn(0, max - min)

        val width = pick(typeCfg.widthMin, typeCfg.widthMax, 4)
        val depth = pick(typeCfg.depthMin, typeCfg.depthMax, 5)
        val floors = pick(typeCfg.floorsMin, typeCfg.floorsMax, 6)
        val roofIdx =
            (cellHash(cx, cz, 7) * typeCfg.roofTypes.size)
                .toInt()
                .coerceIn(0, typeCfg.roofTypes.size - 1)
        val roofType = typeCfg.roofTypes[roofIdx]
        val groundY = surfaceY(ax + width / 2, az + depth / 2)

        return PlacedHouse(
            anchorX = ax,
            anchorZ = az,
            anchorY = groundY - 1,
            width = width,
            depth = depth,
            floors = floors,
            roofType = roofType,
            typeCfg = typeCfg,
            materials = biomeCfg,
            houseSeed = houseSeed,
        )
    }
}
