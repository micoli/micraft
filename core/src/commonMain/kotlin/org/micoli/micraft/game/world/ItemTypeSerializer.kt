package org.micoli.micraft.game.world

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ItemTypeSerializer : KSerializer<ItemType> {
    override val descriptor = PrimitiveSerialDescriptor("ItemType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ItemType) = encoder.encodeString(value.id)

    override fun deserialize(decoder: Decoder) = ItemType(decoder.decodeString())
}
