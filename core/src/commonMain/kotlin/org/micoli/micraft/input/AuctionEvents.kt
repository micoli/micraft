package org.micoli.micraft.input

import org.micoli.micraft.protocol.ClientMessage

/** Auction-house payloads — grouped so `LocalPlayerController` can dispatch them in one branch. */
sealed interface AuctionEvent

data class AuctionCreate(val json: String) : ClientInputEvent(), AuctionEvent

data class AuctionBid(val json: String) : ClientInputEvent(), AuctionEvent

data class AuctionBuyNow(val listingId: String) : ClientInputEvent(), AuctionEvent

data class AuctionCancel(val listingId: String) : ClientInputEvent(), AuctionEvent

data class AuctionSetFilter(val json: String) : ClientInputEvent(), AuctionEvent

class AuctionEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: AuctionEvent) {
        when (event) {
            is AuctionCreate ->
                ctx.decodeLogged<ClientMessage.AuctionCreateListing>(event.json, "auction_create")
            is AuctionBid ->
                ctx.decodeLogged<ClientMessage.AuctionPlaceBid>(event.json, "auction_bid")
            is AuctionBuyNow ->
                ctx.outMessages.trySend(ClientMessage.AuctionBuyNow(event.listingId))
            is AuctionCancel ->
                ctx.outMessages.trySend(ClientMessage.AuctionCancelListing(event.listingId))
            is AuctionSetFilter ->
                ctx.decodeLogged<ClientMessage.AuctionSetFilter>(event.json, "auction_set_filter")
        }
    }
}
