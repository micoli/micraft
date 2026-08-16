package org.micoli.micraft.game.world

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@JvmInline
@Serializable(with = BlockTypeSerializer::class)
value class BlockType(val id: String) {
    val hardness: Float
        get() = BlockRegistry.get(this).hardness.let { if (it == -1f) Float.MAX_VALUE else it }

    val isSolid: Boolean
        get() = BlockRegistry.get(this).solid

    val isLiquid: Boolean
        get() = BlockRegistry.get(this).liquid

    val viscosity: Int
        get() = BlockRegistry.get(this).viscosity

    val isReplaceable: Boolean
        get() = BlockRegistry.get(this).replaceable

    val isVegetationHost: Boolean
        get() = BlockRegistry.get(this).vegetationHost

    val treeAllowed: Boolean
        get() = BlockRegistry.get(this).treeAllowed

    companion object {
        val AIR = BlockType("AIR")
        val BEDROCK = BlockType("BEDROCK")
        val STONE = BlockType("STONE")
        val DIRT = BlockType("DIRT")
        val GRASS = BlockType("GRASS")
        val SAND = BlockType("SAND")
        val SANDSTONE = BlockType("SANDSTONE")
        val GRAVEL = BlockType("GRAVEL")
        val SNOW = BlockType("SNOW")
        val OAK_LOG = BlockType("OAK_LOG")
        val OAK_LEAVES = BlockType("OAK_LEAVES")
        val PINE_LOG = BlockType("PINE_LOG")
        val PINE_LEAVES = BlockType("PINE_LEAVES")
        val PINE_LEAVES_SNOW = BlockType("PINE_LEAVES_SNOW")
        val FLOWER = BlockType("FLOWER")
        val WEED = BlockType("WEED")
        val WATER = BlockType("WATER")
        val SEED = BlockType("SEED")
        val SPROUT = BlockType("SPROUT")
        val SAPLING = BlockType("SAPLING")
        val LEGO_BRICK = BlockType("LEGO_BRICK")
        val LEGO_SLOPE = BlockType("LEGO_SLOPE")
        val LEGO_PLATE = BlockType("LEGO_PLATE")
        val LEGO_CORNER = BlockType("LEGO_CORNER")
        val LEGO_BRICK_2X1 = BlockType("LEGO_BRICK_2X1")
        val LEGO_BRICK_1X2 = BlockType("LEGO_BRICK_1X2")
        val LEGO_PLATE_2X2 = BlockType("LEGO_PLATE_2X2")
        val LEGO_PLATE_2X4 = BlockType("LEGO_PLATE_2X4")
        val LEGO_BRICK_4X1 = BlockType("LEGO_BRICK_4X1")
        val LEGO_PIECE = BlockType("LEGO_PIECE")
        val RAIL_STRAIGHT = BlockType("RAIL_STRAIGHT")
        val RAIL_CURVE_90 = BlockType("RAIL_CURVE_90")
        val RAIL_CURVE_60 = BlockType("RAIL_CURVE_60")
        val RAIL_SLOPE_45 = BlockType("RAIL_SLOPE_45")
        val RAIL_SLOPE_22 = BlockType("RAIL_SLOPE_22")
        val RAIL_Y_SPLIT_90 = BlockType("RAIL_Y_SPLIT_90")
    }

    override fun toString(): String = id
}
