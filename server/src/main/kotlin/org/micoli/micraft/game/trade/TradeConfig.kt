package org.micoli.micraft.game.trade

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TradeConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val maxDistance: Float = 10f,
)
