package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession
import org.micoli.micraft.trade.TradeManager

class TradeOfferCommandTest {
    private val cmd = TradeOfferCommand()

    @Test
    fun noTradeManager_doesNothing() = runBlocking {
        val session = testSession()
        cmd.execute(session, "trade-id {\"COBBLESTONE\":1}", testContext(tradeManager = null))
        assertEquals(0, session.sent.size)
    }

    @Test
    fun blankTradeId_doesNothing() = runBlocking {
        val session = testSession()
        val mgr = TradeManager(getSessions = { emptyList() }, i18n = testI18n(), savePlayer = {})
        cmd.execute(session, "  {\"COBBLESTONE\":1}", testContext(tradeManager = mgr))
        assertEquals(0, session.sent.size)
    }

    @Test
    fun invalidJson_doesNothing() = runBlocking {
        val session = testSession()
        val mgr = TradeManager(getSessions = { emptyList() }, i18n = testI18n(), savePlayer = {})
        cmd.execute(session, "trade-id notjson", testContext(tradeManager = mgr))
        assertEquals(0, session.sent.size)
    }

    @Test
    fun blankJsonPart_doesNothing() = runBlocking {
        val session = testSession()
        val mgr = TradeManager(getSessions = { emptyList() }, i18n = testI18n(), savePlayer = {})
        cmd.execute(session, "trade-id", testContext(tradeManager = mgr))
        assertEquals(0, session.sent.size)
    }
}
