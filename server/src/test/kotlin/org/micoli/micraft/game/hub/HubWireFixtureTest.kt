package org.micoli.micraft.game.hub

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.ChannelSubscription
import org.micoli.micraft.protocol.AuctionBid
import org.micoli.micraft.protocol.AuctionDuration
import org.micoli.micraft.protocol.AuctionListing
import org.micoli.micraft.protocol.AuctionStatus
import org.micoli.micraft.protocol.ClaimInfo
import org.micoli.micraft.protocol.MailMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessageCodec

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * Cross-language contract guard for the `/hub` wire codec (see the web-companion plan's "Encodage
 * des messages" section). Two things per message in the subset:
 * 1. A Kotlin-side round trip (`decode(encode(x)) == x`) — catches a `ServerMessageCodec`
 *    regression regardless of the hub.
 * 2. The hex dump printed to stdout is pasted verbatim into
 *    `app/webApp/ts-src/hub/__tests__/hubCodec.test.ts` as its fixture — REAL bytes the production
 *    codec produced, not hand-guessed ones. If a message in the subset gains/loses/reorders a
 *    field, re-run this test, copy the new hex, and update the TS fixture in the same commit.
 */
class HubWireFixtureTest {

    @Test
    fun notification() {
        val msg = ServerMessage.Notification("hi", "world")
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] Notification = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun chatMessage() {
        val msg = ServerMessage.ChatMessage("world", "Alice", "hi")
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] ChatMessage = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun channelsSync() {
        val msg =
            ServerMessage.ChannelsSync(
                subscribedChannels = listOf(ChannelSubscription("world", autoFocus = true)),
                knownChannels = listOf("world", "system"),
            )
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] ChannelsSync = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun inventoryUpdate() {
        val msg = ServerMessage.InventoryUpdate(mapOf(ItemType("COBBLESTONE") to 5))
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] InventoryUpdate = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun walletUpdate() {
        val msg = ServerMessage.WalletUpdate(1500L)
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] WalletUpdate = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun mailSync() {
        val mail =
            MailMessage(
                id = "m1", from = "Alice", to = "Bob", subject = "hi", body = "text", sentAt = 42L)
        val msg = ServerMessage.MailSync(listOf(mail))
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] MailSync = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun mailReceived() {
        val mail =
            MailMessage(
                id = "m2",
                from = "system",
                to = "Bob",
                subject = "welcome",
                body = "hi",
                sentAt = 99L,
                attachments = mapOf(ItemType("FLINT") to 2),
                copperAmount = 50L,
            )
        val msg = ServerMessage.MailReceived(mail)
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] MailReceived = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun mailDeleted() {
        val msg = ServerMessage.MailDeleted("m1")
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] MailDeleted = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun claimSync() {
        val claim =
            ClaimInfo(
                id = "c1",
                chunks = emptyList(),
                yMin = 0,
                yMax = 64,
                ownerId = "alice-id",
                ownerName = "Alice",
                trustedPlayerNames = listOf("Bob"),
            )
        val msg = ServerMessage.ClaimSync(listOf(claim))
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] ClaimSync = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun claimDenied() {
        val msg = ServerMessage.ClaimDenied("nope")
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] ClaimDenied = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }

    @Test
    fun auctionListingsUpdate() {
        val listing =
            AuctionListing(
                id = "l1",
                sellerId = "alice-id",
                sellerName = "Alice",
                itemType = ItemType("COBBLESTONE"),
                quantity = 5,
                createdAtMs = 1000L,
                expiresAtMs = 2000L,
                duration = AuctionDuration.H24,
                startingPrice = 100L,
                status = AuctionStatus.ACTIVE,
                bidHistory = listOf(AuctionBid("bob-id", "Bob", 150L, 1500L)),
            )
        val msg = ServerMessage.AuctionListingsUpdate(listOf(listing))
        val bytes = ServerMessageCodec.encode(msg)
        println("[hub-fixture] AuctionListingsUpdate = ${bytes.toHex()}")
        assertEquals(msg, ServerMessageCodec.decode(bytes))
    }
}
