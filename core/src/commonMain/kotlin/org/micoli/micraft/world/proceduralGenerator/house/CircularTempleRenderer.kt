package org.micoli.micraft.world.proceduralGenerator.house

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.micoli.micraft.world.BlockType

internal fun PlacedHouse.renderCircularTemple(blocks: ByteArray, ox: Int, oz: Int) {
    val floorH = 4
    val wallTop = anchorY + houseFloorBaseOffset(floors, floorH) + floors
    val cx = anchorX + width / 2
    val cz = anchorZ + depth / 2
    val R = minOf(width, depth) / 2
    val overhang = roofType == "cone_overhang"
    val startR = if (overhang) R + 1 else R

    // clear bounding volume (circle + cone height)
    for (y in anchorY + 1 until wallTop + startR + 2) {
        for (dx in -startR - 1..startR + 1) {
            for (dz in -startR - 1..startR + 1) {
                setHouseBlock(blocks, ox, oz, cx + dx, y, cz + dz, BlockType.AIR)
            }
        }
    }

    // circular floor
    for (dx in -R..R) {
        for (dz in -R..R) {
            if (dx * dx + dz * dz <= R * R) {
                setHouseBlock(blocks, ox, oz, cx + dx, anchorY, cz + dz, materials.floorBlock)
            }
        }
    }

    // perimeter: full walls or evenly-spaced columns (chosen by seed)
    val useColumns = houseHash(houseSeed, 42, 17, 0) > 0.5
    val numCols = (2.0 * PI * R / 2.5).toInt().coerceIn(6, 16)

    for (y in anchorY + 1 until wallTop) {
        if (useColumns) {
            for (i in 0 until numCols) {
                val angle = 2.0 * PI * i / numCols
                val colX = cx + (R * cos(angle)).roundToInt()
                val colZ = cz + (R * sin(angle)).roundToInt()
                setHouseBlock(blocks, ox, oz, colX, y, colZ, materials.wallBlock)
            }
        } else {
            for (dx in -R..R) {
                for (dz in -R..R) {
                    val d2 = dx * dx + dz * dz
                    if (d2 <= R * R && d2 > (R - 1) * (R - 1)) {
                        setHouseBlock(blocks, ox, oz, cx + dx, y, cz + dz, materials.wallBlock)
                    }
                }
            }
        }
    }

    // top ring always full wall just under roof (linteau)
    for (dx in -R..R) {
        for (dz in -R..R) {
            val d2 = dx * dx + dz * dz
            if (d2 <= R * R && d2 > (R - 1) * (R - 1)) {
                setHouseBlock(blocks, ox, oz, cx + dx, wallTop - 1, cz + dz, materials.wallBlock)
            }
        }
    }

    // cone roof: ring at each level, shrinking from startR to apex
    for (rise in 0..startR) {
        val r = startR - rise
        val y = wallTop + rise
        for (dx in -startR..startR) {
            for (dz in -startR..startR) {
                val d2 = dx * dx + dz * dz
                if (d2 <= r * r && (r == 0 || d2 > (r - 1) * (r - 1))) {
                    setHouseBlock(blocks, ox, oz, cx + dx, y, cz + dz, materials.roofBlock)
                }
            }
        }
    }
}
