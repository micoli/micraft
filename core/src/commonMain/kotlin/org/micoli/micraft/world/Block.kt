package org.micoli.micraft.world

import kotlin.jvm.JvmInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class ItemType {
    COBBLESTONE,
    DIRT,
    SAND,
    GRAVEL,
    SANDSTONE,
    SNOWBALL,
    FLINT,
    SEED,
    GRASS,
    SNOW_BLOCK,
    OAK_LOG,
    OAK_LEAVES,
    PINE_LOG,
    PINE_LEAVES,
    PINE_LEAVES_SNOW,
    FLOWER,
    WEED,
}

@JvmInline
@Serializable(with = BlockTypeSerializer::class)
value class BlockType(val id: String) {
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
    }

    override fun toString(): String = id
}

object BlockTypeSerializer : KSerializer<BlockType> {
    override val descriptor = PrimitiveSerialDescriptor("BlockType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BlockType) = encoder.encodeString(value.id)

    override fun deserialize(decoder: Decoder) = BlockType(decoder.decodeString())
}

val BlockType.hardness: Float
    get() = BlockRegistry.get(this).hardness.let { if (it == -1f) Float.MAX_VALUE else it }

val BlockType.isSolid: Boolean
    get() = BlockRegistry.get(this).solid

val BlockType.isLiquid: Boolean
    get() = BlockRegistry.get(this).liquid

val BlockType.viscosity: Int
    get() = BlockRegistry.get(this).viscosity

val BlockType.isReplaceable: Boolean
    get() = BlockRegistry.get(this).replaceable

val BlockType.isVegetationHost: Boolean
    get() = BlockRegistry.get(this).vegetationHost

val BlockType.treeAllowed: Boolean
    get() = BlockRegistry.get(this).treeAllowed

val ItemType.buildable: Boolean
    get() = ItemRegistry.get(this).buildable

val ItemType.placesBlock: BlockType?
    get() = ItemRegistry.get(this).placesBlock

@Serializable
data class BlockPos(val x: Int, val y: Int, val z: Int) {
    init {
        require(y in WorldConstants.WORLD_MIN_Y..WorldConstants.WORLD_MAX_Y) {
            "y=$y out of bounds [${WorldConstants.WORLD_MIN_Y}, ${WorldConstants.WORLD_MAX_Y}]"
        }
    }
}
