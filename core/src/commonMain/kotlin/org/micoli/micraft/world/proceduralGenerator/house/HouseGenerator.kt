package org.micoli.micraft.world.proceduralGenerator.house

import kotlin.math.ceil
import kotlin.math.sqrt
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
    val floorH = 4
    val wallTop = anchorY + houseFloorBaseOffset(floors, floorH)

    // vider tout le volume de la maison (intérieur + murs + toit) avant tout placement
    clearVolume(blocks, ox, oz, wallTop + width / 2 + 2)

    // solid floor at ground level + each floor plate
    for (floor in 0 until floors) {
        val floorY = anchorY + houseFloorBaseOffset(floor, floorH)
        for (dx in 0 until width) {
            for (dz in 0 until depth) {
                setHouseBlock(
                    blocks, ox, oz, anchorX + dx, floorY, anchorZ + dz, materials.floorBlock)
            }
        }
    }

    // outer walls
    for (y in anchorY + 1 until wallTop) {
        for (dx in 0 until width) {
            setHouseBlock(blocks, ox, oz, anchorX + dx, y, anchorZ, materials.wallBlock)
            setHouseBlock(blocks, ox, oz, anchorX + dx, y, anchorZ + depth - 1, materials.wallBlock)
        }
        for (dz in 1 until depth - 1) {
            setHouseBlock(blocks, ox, oz, anchorX, y, anchorZ + dz, materials.wallBlock)
            setHouseBlock(blocks, ox, oz, anchorX + width - 1, y, anchorZ + dz, materials.wallBlock)
        }
    }

    // interior room walls + openings (per floor)
    for (floor in 0 until floors) {
        val baseY = anchorY + houseFloorBaseOffset(floor, floorH) + 1
        placeRoomWalls(blocks, ox, oz, baseY, houseFloorHeight(floor, floorH) - 1)
    }

    // exterior door openings in front wall (face +Z = anchorZ + depth - 1)
    placeExteriorDoors(blocks, ox, oz)

    // window openings on all exterior walls
    placeWindows(blocks, ox, oz)

    // roof
    when (roofType) {
        "flat" -> placeFlatRoof(blocks, ox, oz, wallTop)
        "gabled" -> placeGabledRoof(blocks, ox, oz, wallTop, overhang = false)
        "extended_gabled" -> placeGabledRoof(blocks, ox, oz, wallTop, overhang = true)
        else -> placeFlatRoof(blocks, ox, oz, wallTop)
    }
}

private fun PlacedHouse.clearVolume(blocks: ByteArray, ox: Int, oz: Int, topY: Int) {
    for (y in anchorY + 1 until topY) {
        for (dx in 0 until width) {
            for (dz in 0 until depth) {
                setHouseBlock(blocks, ox, oz, anchorX + dx, y, anchorZ + dz, BlockType.AIR)
            }
        }
    }
}

private fun PlacedHouse.placeRoomWalls(
    blocks: ByteArray,
    ox: Int,
    oz: Int,
    baseY: Int,
    wallHeight: Int,
) {
    val innerW = width - 2
    val innerD = depth - 2
    val rooms = typeCfg.roomsMin.coerceAtLeast(1)
    val cols = ceil(sqrt(rooms.toDouble())).toInt().coerceAtLeast(1)
    val rows = ceil(rooms.toDouble() / cols).toInt().coerceAtLeast(1)

    // vertical dividers (along Z axis, at fixed X positions inside)
    for (c in 1 until cols) {
        val lx = 1 + c * innerW / cols
        val doorZ = 1 + houseHash(houseSeed, lx, baseY, 0) * (innerD - 2).coerceAtLeast(1)
        for (dz in 1 until depth - 1) {
            val worldX = anchorX + lx
            val worldZ = anchorZ + dz
            val isDoor = dz == doorZ.toInt() || dz == doorZ.toInt() + 1
            if (!isDoor) {
                for (dy in 0 until wallHeight) {
                    setHouseBlock(blocks, ox, oz, worldX, baseY + dy, worldZ, materials.wallBlock)
                }
            }
        }
    }

    // horizontal dividers (along X axis, at fixed Z positions inside)
    for (r in 1 until rows) {
        val lz = 1 + r * innerD / rows
        val doorX = 1 + houseHash(houseSeed, lz, baseY, 1) * (innerW - 2).coerceAtLeast(1)
        for (dx in 1 until width - 1) {
            val worldX = anchorX + dx
            val worldZ = anchorZ + lz
            val isDoor = dx == doorX.toInt() || dx == doorX.toInt() + 1
            if (!isDoor) {
                for (dy in 0 until wallHeight) {
                    setHouseBlock(blocks, ox, oz, worldX, baseY + dy, worldZ, materials.wallBlock)
                }
            }
        }
    }
}

private fun PlacedHouse.placeExteriorDoors(blocks: ByteArray, ox: Int, oz: Int) {
    val numDoors =
        typeCfg.doorsMin +
            (houseHash(houseSeed, 0, 0, 99) * (typeCfg.doorsMax - typeCfg.doorsMin + 1))
                .toInt()
                .coerceIn(typeCfg.doorsMin, typeCfg.doorsMax)
    val frontZ = anchorZ + depth - 1
    val spacing = (width - 2) / (numDoors + 1)
    for (i in 1..numDoors) {
        val doorX = anchorX + (spacing * i).coerceIn(1, width - 3)
        // porte 2 blocs de large, 3 blocs de haut
        for (dy in 1..3) {
            setHouseBlock(blocks, ox, oz, doorX, anchorY + dy, frontZ, BlockType.AIR)
            setHouseBlock(blocks, ox, oz, doorX + 1, anchorY + dy, frontZ, BlockType.AIR)
        }
    }
}

private fun PlacedHouse.placeWindows(blocks: ByteArray, ox: Int, oz: Int) {
    val floorH = 4
    val frontZ = anchorZ + depth - 1
    val backZ = anchorZ

    for (floor in 0 until floors) {
        val windowY = anchorY + houseFloorBaseOffset(floor, floorH) + 2

        // faces avant et arrière (Z) : 1-3 fenêtres réparties
        val numZWin =
            1 + (houseHash(houseSeed, floor, 20, 0) * 3).toInt().coerceIn(0, (width - 3) / 2)
        val zSpacing = (width - 2) / (numZWin + 1)
        for (i in 1..numZWin) {
            val wx = anchorX + (zSpacing * i).coerceIn(1, width - 2)
            setHouseBlock(blocks, ox, oz, wx, windowY, frontZ, BlockType.AIR)
            setHouseBlock(blocks, ox, oz, wx, windowY, backZ, BlockType.AIR)
        }

        // faces latérales (X) : fenêtre centrale
        val numXWin =
            1 + (houseHash(houseSeed, floor, 21, 0) * 2).toInt().coerceIn(0, (depth - 3) / 2)
        val xSpacing = (depth - 2) / (numXWin + 1)
        for (i in 1..numXWin) {
            val wz = anchorZ + (xSpacing * i).coerceIn(1, depth - 2)
            setHouseBlock(blocks, ox, oz, anchorX, windowY, wz, BlockType.AIR)
            setHouseBlock(blocks, ox, oz, anchorX + width - 1, windowY, wz, BlockType.AIR)
        }
    }
}

private fun PlacedHouse.placeFlatRoof(blocks: ByteArray, ox: Int, oz: Int, topY: Int) {
    for (dx in 0 until width) {
        for (dz in 0 until depth) {
            setHouseBlock(blocks, ox, oz, anchorX + dx, topY, anchorZ + dz, materials.roofBlock)
        }
    }
    // parapet
    for (dx in 0 until width) {
        setHouseBlock(blocks, ox, oz, anchorX + dx, topY + 1, anchorZ, materials.roofBlock)
        setHouseBlock(
            blocks, ox, oz, anchorX + dx, topY + 1, anchorZ + depth - 1, materials.roofBlock)
    }
    for (dz in 1 until depth - 1) {
        setHouseBlock(blocks, ox, oz, anchorX, topY + 1, anchorZ + dz, materials.roofBlock)
        setHouseBlock(
            blocks, ox, oz, anchorX + width - 1, topY + 1, anchorZ + dz, materials.roofBlock)
    }
}

private fun PlacedHouse.placeGabledRoof(
    blocks: ByteArray,
    ox: Int,
    oz: Int,
    baseY: Int,
    overhang: Boolean
) {
    val hasOverhang = overhang
    val dzMin = if (hasOverhang) -1 else 0
    val dzMax = if (hasOverhang) depth else depth - 1

    val halfW = width / 2
    for (rise in 0..halfW) {
        val y = baseY + rise
        val xStart = anchorX + rise
        val xEnd = anchorX + width - 1 - rise
        if (xStart > xEnd) break
        for (dz in dzMin..dzMax) {
            setHouseBlock(blocks, ox, oz, xStart, y, anchorZ + dz, materials.roofBlock)
            if (xStart != xEnd) {
                setHouseBlock(blocks, ox, oz, xEnd, y, anchorZ + dz, materials.roofBlock)
            }
        }
        // pignon : remplir le triangle des murs aux faces avant/arrière
        if (rise > 0) {
            for (fillY in baseY until y) {
                setHouseBlock(blocks, ox, oz, xStart, fillY, anchorZ, materials.wallBlock)
                setHouseBlock(
                    blocks, ox, oz, xStart, fillY, anchorZ + depth - 1, materials.wallBlock)
                if (xStart != xEnd) {
                    setHouseBlock(blocks, ox, oz, xEnd, fillY, anchorZ, materials.wallBlock)
                    setHouseBlock(
                        blocks, ox, oz, xEnd, fillY, anchorZ + depth - 1, materials.wallBlock)
                }
            }
        }
    }

    // débord de rive (avancée latérale) : rangée basse dépasse d'1 bloc côté X
    if (hasOverhang) {
        for (dz in dzMin..dzMax) {
            setHouseBlock(blocks, ox, oz, anchorX - 1, baseY, anchorZ + dz, materials.roofBlock)
            setHouseBlock(blocks, ox, oz, anchorX + width, baseY, anchorZ + dz, materials.roofBlock)
        }
    }
}

private fun setHouseBlock(
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
    blocks[Chunk.Companion.index(lx, wy, lz)] = type.ordinal.toByte()
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
