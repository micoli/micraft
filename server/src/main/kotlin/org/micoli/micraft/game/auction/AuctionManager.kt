package org.micoli.micraft.game.auction

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.mail.MailManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.AuctionBid
import org.micoli.micraft.protocol.AuctionDuration
import org.micoli.micraft.protocol.AuctionListing
import org.micoli.micraft.protocol.AuctionStatus
import org.micoli.micraft.protocol.ServerMessage

class AuctionManager(
    private val getSessions: () -> Collection<PlayerSession>,
    private val i18n: I18nConfig,
    private val savePlayer: (PlayerSession) -> Unit,
    private val persistence: AuctionPersistence,
    private val mailManager: MailManager?,
    private val config: AuctionConfig,
    private val broadcast: suspend (ServerMessage) -> Unit,
) {
    private val listings = ConcurrentHashMap<String, AuctionListing>()

    init {
        persistence.loadListings().forEach { listings[it.id] = it }
    }

    fun getAll(): Collection<AuctionListing> = listings.values

    suspend fun createListing(
        session: PlayerSession,
        itemType: ItemType,
        quantity: Int,
        duration: AuctionDuration,
        startingPrice: Long,
        buyNowPrice: Long?,
    ) {
        val lang = session.state.language
        if (quantity <= 0) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:invalid_quantity")))
            return
        }
        if (startingPrice <= 0 || (buyNowPrice != null && buyNowPrice <= startingPrice)) {
            session.send(ServerMessage.Notification(i18n.t(lang, "auction:server:invalid_price")))
            return
        }
        if ((session.inventory[itemType] ?: 0) < quantity) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:not_enough_items")))
            return
        }
        val activeCount =
            listings.values.count { it.sellerId == session.id && it.status == AuctionStatus.ACTIVE }
        if (activeCount >= config.maxActiveListingsPerPlayer) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:max_active_auctions")))
            return
        }

        session.inventory.merge(itemType, -quantity, Int::plus)
        if ((session.inventory[itemType] ?: 0) <= 0) session.inventory.remove(itemType)
        savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))

        val now = System.currentTimeMillis()
        val listing =
            AuctionListing(
                id = UUID.randomUUID().toString(),
                sellerId = session.id,
                sellerName = session.state.name,
                itemType = itemType,
                quantity = quantity,
                createdAtMs = now,
                expiresAtMs = now + duration.millis,
                duration = duration,
                startingPrice = startingPrice,
                buyNowPrice = buyNowPrice,
            )
        listings[listing.id] = listing
        persistence.addListing(listing)
        broadcastListingsUpdate()
    }

    suspend fun placeBid(session: PlayerSession, listingId: String, amount: Long) {
        val lang = session.state.language
        val listing = listings[listingId]
        if (listing == null || listing.status != AuctionStatus.ACTIVE) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:auction_not_found")))
            return
        }
        if (listing.sellerId == session.id) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:cannot_bid_own_auction")))
            return
        }
        val floor = listing.currentBid ?: listing.startingPrice
        if (amount <= floor) {
            session.send(ServerMessage.Notification(i18n.t(lang, "auction:server:bid_too_low")))
            return
        }
        if (session.state.wallet < amount) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:insufficient_funds")))
            return
        }

        session.state = session.state.copy(wallet = session.state.wallet - amount)
        savePlayer(session)
        session.send(ServerMessage.WalletUpdate(session.state.wallet))

        val previousBidderId = listing.currentBidderId
        val previousBidderName = listing.currentBidderName
        val previousBid = listing.currentBid
        if (previousBidderId != null && previousBidderName != null && previousBid != null) {
            refundBidder(
                previousBidderId, previousBidderName, previousBid, "auction:server:outbid_subject")
        }

        val updated =
            listing.copy(
                currentBid = amount,
                currentBidderId = session.id,
                currentBidderName = session.state.name,
                bidHistory =
                    listing.bidHistory +
                        AuctionBid(
                            session.id, session.state.name, amount, System.currentTimeMillis()))
        listings[listingId] = updated
        persistence.updateListing(updated)
        session.send(ServerMessage.Notification(i18n.t(lang, "auction:server:bid_placed_success")))
        broadcastListingsUpdate()
    }

    suspend fun buyNow(session: PlayerSession, listingId: String) {
        val lang = session.state.language
        val listing = listings[listingId]
        if (listing == null ||
            listing.status != AuctionStatus.ACTIVE ||
            listing.buyNowPrice == null) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:auction_not_found")))
            return
        }
        if (listing.sellerId == session.id) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:cannot_bid_own_auction")))
            return
        }
        val price = listing.buyNowPrice!!
        if (session.state.wallet < price) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:insufficient_funds")))
            return
        }

        val previousBidderId = listing.currentBidderId
        val previousBidderName = listing.currentBidderName
        val previousBid = listing.currentBid
        if (previousBidderId != null && previousBidderName != null && previousBid != null) {
            refundBidder(
                previousBidderId, previousBidderName, previousBid, "auction:server:outbid_subject")
        }

        session.state = session.state.copy(wallet = session.state.wallet - price)
        savePlayer(session)
        session.send(ServerMessage.WalletUpdate(session.state.wallet))
        session.send(ServerMessage.Notification(i18n.t(lang, "auction:server:bought_now_success")))

        settle(
            listing.copy(
                currentBid = price,
                currentBidderId = session.id,
                currentBidderName = session.state.name,
                bidHistory =
                    listing.bidHistory +
                        AuctionBid(
                            session.id, session.state.name, price, System.currentTimeMillis())))
    }

    suspend fun cancel(session: PlayerSession, listingId: String) {
        val lang = session.state.language
        val listing = listings[listingId]
        if (listing == null || listing.sellerId != session.id) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:not_your_auction")))
            return
        }
        if (listing.status != AuctionStatus.ACTIVE) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "auction:server:auction_not_active")))
            return
        }

        val bidderId = listing.currentBidderId
        val bidderName = listing.currentBidderName
        val bid = listing.currentBid
        if (bidderId != null && bidderName != null && bid != null) {
            refundBidder(bidderId, bidderName, bid, "auction:server:outbid_subject")
        }

        deliverItem(session.id, session.state.name, listing.itemType, listing.quantity)
        val updated = listing.copy(status = AuctionStatus.CANCELLED)
        listings[listingId] = updated
        persistence.updateListing(updated)
        session.send(ServerMessage.Notification(i18n.t(lang, "auction:server:auction_cancelled")))
        broadcastListingsUpdate()
    }

    suspend fun tick(nowMs: Long = System.currentTimeMillis()) {
        val expired =
            listings.values.filter { it.status == AuctionStatus.ACTIVE && it.expiresAtMs <= nowMs }
        if (expired.isEmpty()) return
        for (listing in expired) {
            if (listing.currentBid != null &&
                listing.currentBidderId != null &&
                listing.currentBidderName != null) {
                settle(listing)
            } else {
                deliverItem(
                    listing.sellerId, listing.sellerName, listing.itemType, listing.quantity)
                val updated = listing.copy(status = AuctionStatus.EXPIRED)
                listings[listing.id] = updated
                persistence.updateListing(updated)
            }
        }
        broadcastListingsUpdate()
    }

    suspend fun adminForceCancel(listingId: String): Boolean {
        val listing = listings[listingId] ?: return false
        if (listing.status != AuctionStatus.ACTIVE) return false

        val bidderId = listing.currentBidderId
        val bidderName = listing.currentBidderName
        val bid = listing.currentBid
        if (bidderId != null && bidderName != null && bid != null) {
            refundBidder(bidderId, bidderName, bid, "auction:server:outbid_subject")
        }
        deliverItem(listing.sellerId, listing.sellerName, listing.itemType, listing.quantity)

        val updated = listing.copy(status = AuctionStatus.CANCELLED)
        listings[listingId] = updated
        persistence.updateListing(updated)
        return true
    }

    private suspend fun settle(listing: AuctionListing) {
        val bidderId = listing.currentBidderId ?: return
        val bidderName = listing.currentBidderName ?: return
        val price = listing.currentBid ?: return
        val taxPercent = config.taxPercentFor(listing.duration)
        val net = price - (price * taxPercent / 100)

        creditWallet(
            listing.sellerId, listing.sellerName, net, "auction:server:auction_settled_seller")
        deliverItem(
            bidderId, bidderName, listing.itemType, listing.quantity, "auction:server:auction_won")

        val updated = listing.copy(status = AuctionStatus.SOLD)
        listings[listing.id] = updated
        persistence.updateListing(updated)
    }

    private suspend fun refundBidder(
        bidderId: String,
        bidderName: String,
        amount: Long,
        subjectKey: String
    ) = creditWallet(bidderId, bidderName, amount, subjectKey)

    private suspend fun creditWallet(
        recipientId: String,
        recipientName: String,
        amount: Long,
        subjectKey: String
    ) {
        val online = getSessions().find { it.id == recipientId }
        if (online != null) {
            online.state = online.state.copy(wallet = online.state.wallet + amount)
            savePlayer(online)
            online.send(ServerMessage.WalletUpdate(online.state.wallet))
            online.send(ServerMessage.Notification(i18n.t(online.state.language, subjectKey)))
            return
        }
        mailManager?.deliverSystemMail(
            to = recipientName,
            subject = i18n.t("en", subjectKey),
            body = i18n.t("en", subjectKey),
            copperAmount = amount,
        )
    }

    private suspend fun deliverItem(
        recipientId: String,
        recipientName: String,
        itemType: ItemType,
        quantity: Int,
        subjectKey: String = "auction:server:auction_expired_returned",
    ) {
        val online = getSessions().find { it.id == recipientId }
        if (online != null) {
            online.inventory.merge(itemType, quantity, Int::plus)
            savePlayer(online)
            online.send(ServerMessage.InventoryUpdate(online.inventory.toMap()))
            return
        }
        mailManager?.deliverSystemMail(
            to = recipientName,
            subject = i18n.t("en", subjectKey),
            body = i18n.t("en", subjectKey),
            attachments = mapOf(itemType to quantity),
        )
    }

    private suspend fun broadcastListingsUpdate() {
        broadcast(ServerMessage.AuctionListingsUpdate(listings.values.toList()))
    }
}
