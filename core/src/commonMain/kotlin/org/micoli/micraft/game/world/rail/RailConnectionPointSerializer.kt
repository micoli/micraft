package org.micoli.micraft.game.world.rail

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object RailConnectionPointSerializer : KSerializer<RailConnectionPoint> {
    override val descriptor = PrimitiveSerialDescriptor("RailConnectionPoint", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RailConnectionPoint) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder) = RailConnectionPoint.parse(decoder.decodeString())
}
