package org.micoli.micraft.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import org.koin.ksp.generated.module
import org.micoli.micraft.game.auction.AuctionManager
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.AuctionDuration
import org.micoli.micraft.protocol.AuctionListing
import org.micoli.micraft.protocol.AuctionStatus
import org.micoli.micraft.quest.QuestStatus
import org.micoli.micraft.support.testSession

/**
 * Regression coverage for the production `MailManager` unification (defect 3 in
 * `.scratch/architecture-review/issues/12-production-wiring-defects.md`): before the fix, the Koin
 * `OptionalMailManager` (which has a `QuestManager`) only fed `AuctionManager`, while client mail
 * used a separate, `GameLoop`-built instance with no `QuestManager`.
 */
class MailWiringTest {
    @Test
    fun `production mail is a single instance, shared by the auction, and credits quests on claim`() =
        runBlocking {
            val koin = koinApplication { modules(AppModule().module) }.koin
            val mailManager = koin.get<OptionalMailManager>().value!!
            val auctionManager = koin.get<OptionalAuctionManager>().value!!
            val questManager = koin.get<QuestManager>()

            val alice = testSession(id = "alice-id", name = "Alice")
            // Deliberately not registered in SessionRegistry: AuctionManager mails an offline
            // seller instead of crediting them directly.
            questManager.reloadDefinitions(
                mapOf(
                    "flint_run" to
                        QuestDefinition(
                            id = "flint_run",
                            title = "Flint Run",
                            description = "Collect flint.",
                            type = QuestType.FETCH,
                            itemType = "FLINT",
                            requiredCount = 10,
                        )))
            questManager.accept(alice, "flint_run")

            // An expired, un-bid listing: AuctionManager.tick() returns the item to its offline
            // seller by mail.
            val listing =
                AuctionListing(
                    id = "listing-1",
                    sellerId = "alice-id",
                    sellerName = "Alice",
                    itemType = ItemType("FLINT"),
                    quantity = 10,
                    createdAtMs = 0L,
                    expiresAtMs = 0L,
                    duration = AuctionDuration.H12,
                    startingPrice = 1L,
                    status = AuctionStatus.ACTIVE,
                )
            val listingsField = AuctionManager::class.java.getDeclaredField("listings")
            listingsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val listings =
                listingsField.get(auctionManager)
                    as java.util.concurrent.ConcurrentHashMap<String, AuctionListing>
            listings[listing.id] = listing

            auctionManager.tick(nowMs = 1L)

            // The mail landed in the exact instance the player's own mailbox reads from.
            val mail = mailManager.loadForPlayer("Alice").single()
            assertEquals(mapOf(ItemType("FLINT") to 10), mail.attachments)

            mailManager.handleClaimAttachments(alice, mail.id)

            assertEquals(10, alice.inventory[ItemType("FLINT")])
            assertEquals(QuestStatus.COMPLETED, alice.state.quests["flint_run"]?.status)
        }
}
