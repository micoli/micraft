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

@Serializable
data class AuctionBid(
    val bidderId: String,
    val bidderName: String,
    val amount: Long,
    val atMs: Long,
)

@Serializable
data class AuctionFilter(
    val itemType: String? = null,
    val sellerName: String? = null,
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val mineOnly: Boolean = false,
    val expiredOnly: Boolean = false,
    val myBidsOnly: Boolean = false,
)

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
    val buyNowPrice: Long? = null,
    val currentBid: Long? = null,
    val currentBidderId: String? = null,
    val currentBidderName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val status: AuctionStatus = AuctionStatus.ACTIVE,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val bidHistory: List<AuctionBid> = emptyList(),
)
