package org.micoli.micraft.game.auction

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.micoli.micraft.protocol.AuctionDuration

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AuctionConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tax12h: Int = 3,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tax24h: Int = 6,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tax48h: Int = 10,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tax96h: Int = 15,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val maxActiveListingsPerPlayer: Int = 10,
) {
    fun taxPercentFor(duration: AuctionDuration): Int =
        when (duration) {
            AuctionDuration.H12 -> tax12h
            AuctionDuration.H24 -> tax24h
            AuctionDuration.H48 -> tax48h
            AuctionDuration.H96 -> tax96h
        }
}
