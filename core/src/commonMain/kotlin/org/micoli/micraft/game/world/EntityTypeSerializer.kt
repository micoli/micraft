package org.micoli.micraft.game.world

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object EntityTypeSerializer : KSerializer<EntityType> {
    override val descriptor = PrimitiveSerialDescriptor("EntityType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: EntityType) = encoder.encodeString(value.id)

    override fun deserialize(decoder: Decoder) = EntityType(decoder.decodeString())
}
