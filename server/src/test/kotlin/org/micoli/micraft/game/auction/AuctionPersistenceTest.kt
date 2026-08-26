package org.micoli.micraft.game.auction

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.AuctionDuration
import org.micoli.micraft.protocol.AuctionListing
import org.micoli.micraft.protocol.AuctionStatus

class AuctionPersistenceTest {
    private fun listing(id: String = "listing-1") =
        AuctionListing(
            id = id,
            sellerId = "alice-id",
            sellerName = "Alice",
            itemType = ItemType("DIRT"),
            quantity = 4,
            createdAtMs = 0L,
            expiresAtMs = 1_000L,
            duration = AuctionDuration.H12,
            startingPrice = 10L,
        )

    @Test
    fun missingFile_loadListings_returnsEmpty() {
        val dir = Files.createTempDirectory("auction-persistence")
        assertTrue(AuctionPersistence(dir).loadListings().isEmpty())
    }

    @Test
    fun addListing_thenLoad_roundTrips() {
        val dir = Files.createTempDirectory("auction-persistence2")
        val persistence = AuctionPersistence(dir)
        persistence.addListing(listing())
        val loaded = persistence.loadListings()
        assertEquals(1, loaded.size)
        assertEquals("listing-1", loaded.first().id)
    }

    @Test
    fun updateListing_replacesMatchingEntry() {
        val dir = Files.createTempDirectory("auction-persistence3")
        val persistence = AuctionPersistence(dir)
        persistence.addListing(listing())
        val sold = listing().copy(status = AuctionStatus.SOLD)
        persistence.updateListing(sold)
        val loaded = persistence.loadListings()
        assertEquals(1, loaded.size)
        assertEquals(AuctionStatus.SOLD, loaded.first().status)
    }
}
