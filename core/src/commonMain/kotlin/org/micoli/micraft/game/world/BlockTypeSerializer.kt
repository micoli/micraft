package org.micoli.micraft.game.world

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object BlockTypeSerializer : KSerializer<BlockType> {
    override val descriptor = PrimitiveSerialDescriptor("BlockType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BlockType) = encoder.encodeString(value.id)

    override fun deserialize(decoder: Decoder) = BlockType(decoder.decodeString())
}
