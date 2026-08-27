package org.micoli.micraft.game.auction

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.mail.MailManager
import org.micoli.micraft.game.mail.MailPersistence
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.AuctionDuration
import org.micoli.micraft.protocol.AuctionFilter
import org.micoli.micraft.protocol.AuctionStatus
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class AuctionManagerTest {
    private val dirt = ItemType("DIRT")

    private fun manager(
        sessions: List<PlayerSession>,
        mailManager: MailManager? = null,
        config: AuctionConfig = AuctionConfig(),
    ) =
        AuctionManager(
            getSessions = { sessions },
            i18n = testI18n(),
            savePlayer = {},
            persistence = AuctionPersistence(Files.createTempDirectory("auction-manager")),
            mailManager = mailManager,
            config = config,
        )

    @Test
    fun createListing_escrowsItemFromInventory() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 5
        val mgr = manager(listOf(alice))

        mgr.createListing(alice, dirt, 3, AuctionDuration.H12, 10L, null)

        assertEquals(2, alice.inventory[dirt])
        assertEquals(1, mgr.getAll().size)
    }

    @Test
    fun createListing_insufficientItems_rejectsAndSendsNotification() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 1
        val mgr = manager(listOf(alice))

        mgr.createListing(alice, dirt, 5, AuctionDuration.H12, 10L, null)

        assertEquals(1, alice.inventory[dirt])
        assertTrue(mgr.getAll().isEmpty())
        assertTrue(alice.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun placeBid_belowFloor_isRejected() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 1
        val bob = testSession(id = "bob-id", name = "Bob")
        bob.state = bob.state.copy(wallet = 100L)
        val mgr = manager(listOf(alice, bob))
        mgr.createListing(alice, dirt, 1, AuctionDuration.H12, 10L, null)
        val listingId = mgr.getAll().first().id

        mgr.placeBid(bob, listingId, 10L)

        assertNull(mgr.getAll().first().currentBid)
        assertTrue(bob.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun placeBid_higherBid_refundsPreviousBidderImmediately() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 1
        val bob = testSession(id = "bob-id", name = "Bob")
        bob.state = bob.state.copy(wallet = 100L)
        val carol = testSession(id = "carol-id", name = "Carol")
        carol.state = carol.state.copy(wallet = 100L)
        val mgr = manager(listOf(alice, bob, carol))
        mgr.createListing(alice, dirt, 1, AuctionDuration.H12, 10L, null)
        val listingId = mgr.getAll().first().id

        mgr.placeBid(bob, listingId, 20L)
        assertEquals(80L, bob.state.wallet)

        mgr.placeBid(carol, listingId, 30L)

        // Bob's 20 must be refunded the instant Carol's higher bid supersedes him.
        assertEquals(100L, bob.state.wallet)
        assertEquals(70L, carol.state.wallet)
        assertTrue(
            bob.sent.filterIsInstance<ServerMessage.WalletUpdate>().any { it.copper == 100L })
        assertEquals(30L, mgr.getAll().first().currentBid)
    }

    @Test
    fun placeBid_recordsEachBidInHistory() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 1
        val bob = testSession(id = "bob-id", name = "Bob")
        bob.state = bob.state.copy(wallet = 100L)
        val carol = testSession(id = "carol-id", name = "Carol")
        carol.state = carol.state.copy(wallet = 100L)
        val mgr = manager(listOf(alice, bob, carol))
        mgr.createListing(alice, dirt, 1, AuctionDuration.H12, 10L, null)
        val listingId = mgr.getAll().first().id

        mgr.placeBid(bob, listingId, 20L)
        mgr.placeBid(carol, listingId, 30L)

        val history = mgr.getAll().first().bidHistory
        assertEquals(2, history.size)
        assertEquals("bob-id", history[0].bidderId)
        assertEquals(20L, history[0].amount)
        assertEquals("carol-id", history[1].bidderId)
        assertEquals(30L, history[1].amount)
    }

    @Test
    fun setFilter_mineOnly_serverSideExcludesOthersListings() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 2
        val bob = testSession(id = "bob-id", name = "Bob")
        bob.inventory[dirt] = 1
        val mgr = manager(listOf(alice, bob))
        mgr.createListing(alice, dirt, 1, AuctionDuration.H12, 10L, null)
        mgr.createListing(bob, dirt, 1, AuctionDuration.H12, 10L, null)

        mgr.setFilter(alice, AuctionFilter(mineOnly = true))

        val update = alice.sent.filterIsInstance<ServerMessage.AuctionListingsUpdate>().last()
        assertEquals(1, update.listings.size)
        assertEquals("alice-id", update.listings.first().sellerId)
    }

    @Test
    fun buyNow_notifiesOtherViewersOfListingsChange() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 1
        val bob = testSession(id = "bob-id", name = "Bob")
        bob.state = bob.state.copy(wallet = 200L)
        val carol = testSession(id = "carol-id", name = "Carol")
        val mgr = manager(listOf(alice, bob, carol))
        mgr.createListing(alice, dirt, 1, AuctionDuration.H12, 10L, 100L)
        val listingId = mgr.getAll().first().id
        mgr.setFilter(carol, AuctionFilter(expiredOnly = true))
        val updatesBefore = carol.sent.filterIsInstance<ServerMessage.AuctionListingsUpdate>().size

        mgr.buyNow(bob, listingId)

        val updatesAfter = carol.sent.filterIsInstance<ServerMessage.AuctionListingsUpdate>()
        assertTrue(updatesAfter.size > updatesBefore)
        assertEquals(AuctionStatus.SOLD, updatesAfter.last().listings.first().status)
    }

    @Test
    fun buyNow_settlesInstantlyAndAppliesTax() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 1
        val bob = testSession(id = "bob-id", name = "Bob")
        bob.state = bob.state.copy(wallet = 200L)
        val mgr = manager(listOf(alice, bob), config = AuctionConfig(tax12h = 10))
        mgr.createListing(alice, dirt, 1, AuctionDuration.H12, 10L, 100L)
        val listingId = mgr.getAll().first().id

        mgr.buyNow(bob, listingId)

        assertEquals(100L, bob.state.wallet)
        assertEquals(90L, alice.state.wallet) // 100 - 10% tax
        assertEquals(1, bob.inventory[dirt])
        assertEquals(AuctionStatus.SOLD, mgr.getAll().first().status)
    }

    @Test
    fun cancel_ownerOnly_returnsItemWithZeroTax() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 2
        val mgr = manager(listOf(alice))
        mgr.createListing(alice, dirt, 2, AuctionDuration.H12, 10L, null)
        val listingId = mgr.getAll().first().id

        mgr.cancel(alice, listingId)

        assertEquals(2, alice.inventory[dirt])
        assertEquals(AuctionStatus.CANCELLED, mgr.getAll().first().status)
    }

    @Test
    fun cancel_nonOwner_isRejected() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 2
        val bob = testSession(id = "bob-id", name = "Bob")
        val mgr = manager(listOf(alice, bob))
        mgr.createListing(alice, dirt, 2, AuctionDuration.H12, 10L, null)
        val listingId = mgr.getAll().first().id

        mgr.cancel(bob, listingId)

        assertEquals(AuctionStatus.ACTIVE, mgr.getAll().first().status)
        assertTrue(bob.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun tick_settlesExpiredListingWithBid_appliesTax() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 1
        val bob = testSession(id = "bob-id", name = "Bob")
        bob.state = bob.state.copy(wallet = 100L)
        val mgr = manager(listOf(alice, bob), config = AuctionConfig(tax12h = 20))
        mgr.createListing(alice, dirt, 1, AuctionDuration.H12, 10L, null)
        val listingId = mgr.getAll().first().id
        mgr.placeBid(bob, listingId, 50L)

        mgr.tick(nowMs = System.currentTimeMillis() + AuctionDuration.H12.millis + 1)

        assertEquals(AuctionStatus.SOLD, mgr.getAll().first().status)
        assertEquals(1, bob.inventory[dirt])
        assertEquals(40L, alice.state.wallet) // 50 - 20% tax
    }

    @Test
    fun tick_expiresListingWithNoBid_returnsItemZeroTax() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 1
        val mgr = manager(listOf(alice))
        mgr.createListing(alice, dirt, 1, AuctionDuration.H12, 10L, null)

        mgr.tick(nowMs = System.currentTimeMillis() + AuctionDuration.H12.millis + 1)

        assertEquals(AuctionStatus.EXPIRED, mgr.getAll().first().status)
        assertEquals(1, alice.inventory[dirt])
    }

    @Test
    fun placeBid_offlinePreviousBidder_deliversRefundViaMail() = runBlocking {
        val mailDir = Files.createTempDirectory("auction-mail")
        mailDir.resolve("bob.yaml").toFile().writeText("state:\n  id: bob-id\n  name: Bob\n")
        val mailPersistence = MailPersistence(mailDir)
        val registry = SessionRegistry()
        val mailManager = MailManager(mailPersistence, registry, testI18n(), savePlayer = {})

        val alice = testSession(id = "alice-id", name = "Alice")
        alice.inventory[dirt] = 1
        val bob = testSession(id = "bob-id", name = "Bob")
        bob.state = bob.state.copy(wallet = 100L)
        val carol = testSession(id = "carol-id", name = "Carol")
        carol.state = carol.state.copy(wallet = 100L)

        // getSessions() reflects who's "online": only Alice and Carol are registered.
        registry[alice.id] = alice
        registry[carol.id] = carol
        val mgr =
            AuctionManager(
                getSessions = { registry.all() },
                i18n = testI18n(),
                savePlayer = {},
                persistence = AuctionPersistence(Files.createTempDirectory("auction-manager2")),
                mailManager = mailManager,
                config = AuctionConfig(),
            )
        mgr.createListing(alice, dirt, 1, AuctionDuration.H12, 10L, null)
        val listingId = mgr.getAll().first().id

        // Bob bids while online, then disconnects before being outbid.
        registry[bob.id] = bob
        mgr.placeBid(bob, listingId, 20L)
        registry.remove(bob.id)
        mgr.placeBid(carol, listingId, 30L)

        val bobMails = mailPersistence.loadMails("Bob")
        assertEquals(1, bobMails.size)
        assertEquals(20L, bobMails.first().copperAmount)
    }
}
