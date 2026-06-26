package org.micoli.micraft.world.proceduralGenerator.house

import org.micoli.micraft.world.BlockRegistry
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.WorldConstants

data class PlacedHouse(
    val anchorX: Int,
    val anchorZ: Int,
    val anchorY: Int,
    val width: Int,
    val depth: Int,
    val floors: Int,
    val roofType: String,
    val typeCfg: HouseTypeConfig,
    val materials: HouseBiomeConfig,
    val houseSeed: Long,
)

fun PlacedHouse.renderIntoChunk(blocks: ByteArray, ox: Int, oz: Int) {
    when (typeCfg.id) {
        "circular_temple" -> renderCircularTemple(blocks, ox, oz)
        else -> renderRectangular(blocks, ox, oz)
    }
}

internal fun setHouseBlock(
    blocks: ByteArray,
    ox: Int,
    oz: Int,
    wx: Int,
    wy: Int,
    wz: Int,
    type: BlockType,
) {
    val lx = wx - ox
    val lz = wz - oz
    if (lx !in 0 until WorldConstants.CHUNK_SIZE) return
    if (lz !in 0 until WorldConstants.CHUNK_SIZE) return
    if (wy !in 0 until Chunk.Companion.SIZE_Y) return
    blocks[Chunk.Companion.index(lx, wy, lz)] = BlockRegistry.wireIndex(type).toByte()
}

// baseY offset of floor n: sum of heights of floors 0..n-1 = n*floorH + n*(n-1)/2
internal fun houseFloorBaseOffset(floor: Int, floorH: Int) =
    floor * floorH + floor * (floor - 1) / 2

// height of floor n = floorH + n (ground = floorH, first = floorH+1, …)
internal fun houseFloorHeight(floor: Int, floorH: Int) = floorH + floor

internal fun houseHash(seed: Long, a: Int, b: Int, c: Int): Double {
    var h =
        seed xor
            (a.toLong() * 2654435761L) xor
            (b.toLong() * 2246822519L) xor
            (c.toLong() * 1234567891L)
    h = h xor (h ushr 33)
    h *= -49064778989728563L
    h = h xor (h ushr 33)
    h *= -4265267296055464877L
    h = h xor (h ushr 33)
    return (h and 0x7FFFFFFFFFFFFFFFL).toDouble() / Long.MAX_VALUE.toDouble()
}
