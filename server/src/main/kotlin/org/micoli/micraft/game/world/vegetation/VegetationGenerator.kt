package org.micoli.micraft.game.world.vegetation

import kotlin.math.abs
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.proceduralGenerator.ProceduralChunkGenerator

private fun vegetationHash(
    generator: ProceduralChunkGenerator,
    wx: Int,
    wz: Int,
    typeIdx: Int
): Double {
    var h =
        generator.seed xor
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

fun placeVegetation(generator: ProceduralChunkGenerator, blocks: ByteArray, ox: Int, oz: Int) {
    val margin = 3
    val s = WorldConstants.CHUNK_SIZE
    for (lx in -margin until s + margin) {
        for (lz in -margin until s + margin) {
            val wx = ox + lx
            val wz = oz + lz
            val sample = generator.voronoi.sample(wx, wz)
            val surfaceY = generator.surfaceHeight(wx, wz, sample)
            val biome = generator.voronoi.effectiveBiome(wx, wz, surfaceY, sample)
            val surfaceBlock = generator.voronoi.selectColumn(wx, wz, surfaceY, sample).surface

            if (generator.roadVoronoi?.shouldBlockVegetation(sample.primary.id, wx, wz) == true)
                continue

            for ((idx, entry) in biome.vegetation.withIndex()) {
                if (vegetationHash(generator, wx, wz, idx) < entry.density) {
                    placeVegetationStructure(
                        generator, blocks, ox, oz, wx, wz, surfaceY, surfaceBlock, entry.type)
                    break
                }
            }
        }
    }
}

private fun placeVegetationStructure(
    generator: ProceduralChunkGenerator,
    blocks: ByteArray,
    ox: Int,
    oz: Int,
    wx: Int,
    wz: Int,
    surfaceY: Int,
    surfaceBlock: BlockType,
    type: VegetationType,
) {
    when (type) {
        VegetationType.OAK_TREE ->
            if (surfaceBlock.treeAllowed) placeOakTree(generator, blocks, ox, oz, wx, wz, surfaceY)
        VegetationType.PINE_TREE ->
            if (surfaceBlock.treeAllowed)
                placePineTree(generator, blocks, ox, oz, wx, wz, surfaceY, BlockType.PINE_LEAVES)
        VegetationType.PINE_TREE_SNOW ->
            if (surfaceBlock.treeAllowed)
                placePineTree(
                    generator, blocks, ox, oz, wx, wz, surfaceY, BlockType.PINE_LEAVES_SNOW)
        VegetationType.FLOWER ->
            if (surfaceBlock.isVegetationHost)
                setVeg(blocks, ox, oz, wx, surfaceY + 1, wz, BlockType.FLOWER)
        VegetationType.WEED ->
            if (surfaceBlock.isVegetationHost)
                setVeg(blocks, ox, oz, wx, surfaceY + 1, wz, BlockType.WEED)
    }
}

private fun positionSeed(wx: Int, wz: Int): Double {
    var h = (wx.toLong() * 2654435761L) xor (wz.toLong() * 2246822519L)
    h = h xor (h ushr 33)
    h *= -49064778989728563L
    h = h xor (h ushr 33)
    h *= -4265267296055464877L
    h = h xor (h ushr 33)
    return (h and 0x7FFFFFFFFFFFFFFFL).toDouble() / Long.MAX_VALUE.toDouble()
}

fun oakTreeBlocks(wx: Int, wz: Int, surfaceY: Int): List<Pair<BlockPos, BlockType>> {
    val trunkH = 4 + (positionSeed(wx, wz) * 2).toInt()
    val trunkBase = surfaceY + 1
    val trunkTop = trunkBase + trunkH - 1
    val result = mutableListOf<Pair<BlockPos, BlockType>>()

    fun add(x: Int, y: Int, z: Int, type: BlockType) {
        if (y in WorldConstants.WORLD_MIN_Y..WorldConstants.WORLD_MAX_Y) {
            result += Pair(BlockPos(x, y, z), type)
        }
    }

    for (y in trunkBase..trunkTop) add(wx, y, wz, BlockType.OAK_LOG)
    for (dy in -1..0) {
        for (dx in -2..2) for (dz in -2..2) {
            if (dx == 0 && dz == 0) continue
            if (abs(dx) == 2 && abs(dz) == 2) continue
            add(wx + dx, trunkTop + dy, wz + dz, BlockType.OAK_LEAVES)
        }
    }
    for (dx in -1..1) for (dz in -1..1) add(wx + dx, trunkTop + 1, wz + dz, BlockType.OAK_LEAVES)
    add(wx, trunkTop + 2, wz, BlockType.OAK_LEAVES)
    add(wx + 1, trunkTop + 2, wz, BlockType.OAK_LEAVES)
    add(wx - 1, trunkTop + 2, wz, BlockType.OAK_LEAVES)
    add(wx, trunkTop + 2, wz + 1, BlockType.OAK_LEAVES)
    add(wx, trunkTop + 2, wz - 1, BlockType.OAK_LEAVES)
    return result
}

fun pineTreeBlocks(
    wx: Int,
    wz: Int,
    surfaceY: Int,
    leavesType: BlockType
): List<Pair<BlockPos, BlockType>> {
    val trunkH = 7 + (positionSeed(wx, wz) * 3).toInt()
    val trunkBase = surfaceY + 1
    val trunkTop = trunkBase + trunkH - 1
    val result = mutableListOf<Pair<BlockPos, BlockType>>()

    fun add(x: Int, y: Int, z: Int, type: BlockType) {
        if (y in WorldConstants.WORLD_MIN_Y..WorldConstants.WORLD_MAX_Y) {
            result += Pair(BlockPos(x, y, z), type)
        }
    }

    for (y in trunkBase..trunkTop) add(wx, y, wz, BlockType.PINE_LOG)
    add(wx, trunkTop, wz, leavesType)
    for (dx in -1..1) for (dz in -1..1) add(wx + dx, trunkTop - 1, wz + dz, leavesType)
    for (dy in -3..-2) {
        for (dx in -2..2) for (dz in -2..2) {
            if (abs(dx) == 2 && abs(dz) == 2) continue
            add(wx + dx, trunkTop + dy, wz + dz, leavesType)
        }
    }
    return result
}

private fun placeOakTree(
    generator: ProceduralChunkGenerator,
    blocks: ByteArray,
    ox: Int,
    oz: Int,
    wx: Int,
    wz: Int,
    surfaceY: Int
) {
    val trunkH = 4 + (vegetationHash(generator, wx, wz, 99) * 2).toInt()
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
    generator: ProceduralChunkGenerator,
    blocks: ByteArray,
    ox: Int,
    oz: Int,
    wx: Int,
    wz: Int,
    surfaceY: Int,
    leavesType: BlockType,
) {
    val trunkH = 7 + (vegetationHash(generator, wx, wz, 99) * 3).toInt()
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
    if (blocks[idx] == BlockRegistry.wireIndex(BlockType.AIR).toByte()) {
        blocks[idx] = BlockRegistry.wireIndex(type).toByte()
    }
}
