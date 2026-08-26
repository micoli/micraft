package org.micoli.micraft.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.ItemType

enum class AuctionDuration(val hours: Int) {
    H12(12),
    H24(24),
    H48(48),
    H96(96);

    val millis: Long
        get() = hours * 3_600_000L
}

enum class AuctionStatus {
    ACTIVE,
    SOLD,
    EXPIRED,
    CANCELLED,
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AuctionListing(
    val id: String,
    val sellerId: String,
    val sellerName: String,
    val itemType: ItemType,
    val quantity: Int,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val duration: AuctionDuration,
    val startingPrice: Long,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val buyNowPrice: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val currentBid: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val currentBidderId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val currentBidderName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val status: AuctionStatus = AuctionStatus.ACTIVE,
)
