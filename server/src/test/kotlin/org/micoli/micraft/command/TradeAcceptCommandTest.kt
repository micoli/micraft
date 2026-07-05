package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession
import org.micoli.micraft.trade.TradeManager

class TradeAcceptCommandTest {
    private val cmd = TradeAcceptCommand()

    @Test
    fun noTradeManager_doesNothing() = runBlocking {
        val session = testSession()
        cmd.execute(session, "trade-id", testContext(tradeManager = null))
        assertEquals(0, session.sent.size)
    }

    @Test
    fun unknownTradeId_doesNothing() = runBlocking {
        val session = testSession()
        val mgr =
            TradeManager(getSessions = { listOf(session) }, i18n = testI18n(), savePlayer = {})
        cmd.execute(session, "nonexistent-id", testContext(tradeManager = mgr))
        assertEquals(0, session.sent.size)
    }
}
