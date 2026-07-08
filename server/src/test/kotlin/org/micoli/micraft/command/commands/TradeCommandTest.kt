package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class TradeCommandTest {
    private val cmd = TradeCommand()

    @Test
    fun noTradeManager_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "Bob", testContext(tradeManager = null))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun withTradeManager_targetNotFound_sendsNotFound() = runBlocking {
        val session = testSession(name = "Alice")
        val mgr = TradeManager(getSessions = { emptyList() }, i18n = testI18n(), savePlayer = {})
        cmd.execute(session, "Bob", testContext(tradeManager = mgr, sessions = emptyList()))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("Bob") ||
                    it.message.contains("not found") ||
                    it.message.contains("introuvable")
            })
    }

    @Test
    fun withTradeManager_selfTarget_sendsUsage() = runBlocking {
        val session = testSession(name = "Alice")
        val mgr =
            TradeManager(getSessions = { listOf(session) }, i18n = testI18n(), savePlayer = {})
        cmd.execute(session, "Alice", testContext(tradeManager = mgr, sessions = listOf(session)))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun withTradeManager_success_sendsOpenTrade() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        val bob = testSession(id = "bob-id", name = "Bob")
        val sessions = listOf(alice, bob)
        val mgr = TradeManager(getSessions = { sessions }, i18n = testI18n(), savePlayer = {})
        cmd.execute(alice, "Bob", testContext(tradeManager = mgr, sessions = sessions))
        assertTrue(alice.sent.filterIsInstance<ServerMessage.OpenTrade>().isNotEmpty())
    }

    @Test
    fun completeArg_excludesSelf() = runBlocking {
        val alice = testSession(name = "Alice")
        val bob = testSession(id = "bob-id", name = "Bob")
        val ctx = testContext(sessions = listOf(alice, bob))
        val results = cmd.completeArg(0, "", alice, ctx)
        assertFalse(results.contains("Alice"))
        assertTrue(results.contains("Bob"))
    }
}
