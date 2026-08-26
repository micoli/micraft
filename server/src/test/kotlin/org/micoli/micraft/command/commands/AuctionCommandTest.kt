package org.micoli.micraft.command.commands

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.auction.AuctionConfig
import org.micoli.micraft.game.auction.AuctionManager
import org.micoli.micraft.game.auction.AuctionPersistence
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class AuctionCommandTest {
    private val cmd = AuctionCommand()

    @Test
    fun noAuctionManager_sendsNotification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(auctionManager = null))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun withAuctionManager_opensUi() = runBlocking {
        val session = testSession()
        val manager =
            AuctionManager(
                getSessions = { listOf(session) },
                i18n = testI18n(),
                savePlayer = {},
                persistence = AuctionPersistence(Files.createTempDirectory("auction-cmd")),
                mailManager = null,
                config = AuctionConfig(),
                broadcast = {},
            )
        cmd.execute(session, "", testContext(auctionManager = manager))
        assertTrue(session.sent.filterIsInstance<ServerMessage.OpenAuctionHouse>().isNotEmpty())
        assertTrue(
            session.sent.filterIsInstance<ServerMessage.AuctionListingsUpdate>().isNotEmpty())
    }
}
