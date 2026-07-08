package org.micoli.micraft.game.trade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class TradeManagerTest {

    private fun makeManager(sessions: List<PlayerSession>) =
        TradeManager(
            getSessions = { sessions },
            i18n = testContext().i18n,
            savePlayer = {},
            maxDistance = 10f,
        )

    @Test
    fun initiate_unknownTarget_sendsError() = runBlocking {
        val alice = testSession(name = "Alice")
        val manager = makeManager(listOf(alice))
        manager.initiate(alice, "Bob")
        assertTrue(
            alice.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("Bob")
            })
    }

    @Test
    fun initiate_tooFar_sendsError() = runBlocking {
        val alice = testSession(name = "Alice", pos = Vec3(0f, 0f, 0f))
        val bob = testSession(id = "bob-id", name = "Bob", pos = Vec3(50f, 0f, 0f))
        val manager = makeManager(listOf(alice, bob))
        manager.initiate(alice, "Bob")
        assertTrue(
            alice.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("far") || it.message.contains("Bob")
            })
        assertFalse(alice.sent.filterIsInstance<ServerMessage.OpenTrade>().isNotEmpty())
    }

    @Test
    fun initiate_withinRange_opensTradeForBoth() = runBlocking {
        val alice = testSession(name = "Alice", pos = Vec3(0f, 0f, 0f))
        val bob = testSession(id = "bob-id", name = "Bob", pos = Vec3(5f, 0f, 0f))
        val manager = makeManager(listOf(alice, bob))
        manager.initiate(alice, "Bob")
        assertTrue(alice.sent.filterIsInstance<ServerMessage.OpenTrade>().isNotEmpty())
        assertTrue(bob.sent.filterIsInstance<ServerMessage.OpenTrade>().isNotEmpty())
    }

    @Test
    fun initiate_alreadyTrading_sendsError() = runBlocking {
        val alice = testSession(name = "Alice", pos = Vec3(0f, 0f, 0f))
        val bob = testSession(id = "bob-id", name = "Bob", pos = Vec3(5f, 0f, 0f))
        val carol = testSession(id = "carol-id", name = "Carol", pos = Vec3(3f, 0f, 0f))
        val manager = makeManager(listOf(alice, bob, carol))
        manager.initiate(alice, "Bob")
        // Alice tries to trade with Carol while already trading with Bob
        manager.initiate(alice, "Carol")
        assertTrue(
            alice.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("already") || it.message.contains("trading")
            })
    }

    @Test
    fun cancel_closesBothWindows() = runBlocking {
        val alice = testSession(name = "Alice", pos = Vec3(0f, 0f, 0f))
        val bob = testSession(id = "bob-id", name = "Bob", pos = Vec3(5f, 0f, 0f))
        val manager = makeManager(listOf(alice, bob))
        manager.initiate(alice, "Bob")
        val tradeId = alice.sent.filterIsInstance<ServerMessage.OpenTrade>().first().tradeId
        manager.cancel(alice, tradeId)
        assertTrue(alice.sent.filterIsInstance<ServerMessage.TradeClosed>().isNotEmpty())
        assertTrue(bob.sent.filterIsInstance<ServerMessage.TradeClosed>().isNotEmpty())
    }

    @Test
    fun updateOffer_resetsAcceptance() = runBlocking {
        val alice = testSession(name = "Alice", pos = Vec3(0f, 0f, 0f))
        val bob = testSession(id = "bob-id", name = "Bob", pos = Vec3(5f, 0f, 0f))
        val manager = makeManager(listOf(alice, bob))
        manager.initiate(alice, "Bob")
        val tradeId = alice.sent.filterIsInstance<ServerMessage.OpenTrade>().first().tradeId
        // Alice accepts
        manager.accept(alice, tradeId)
        // Bob updates offer → Alice's acceptance should reset
        manager.updateOffer(bob, tradeId, mapOf(ItemType("DIRT") to 1))
        val update = alice.sent.filterIsInstance<ServerMessage.TradeUpdate>().last()
        assertFalse(update.myAccepted)
        assertFalse(update.theirAccepted)
    }

    @Test
    fun executeTrade_swapsItems() = runBlocking {
        val alice = testSession(name = "Alice", pos = Vec3(0f, 0f, 0f))
        val bob = testSession(id = "bob-id", name = "Bob", pos = Vec3(5f, 0f, 0f))
        alice.inventory[ItemType("DIRT")] = 5
        bob.inventory[ItemType("SAND")] = 3
        val manager = makeManager(listOf(alice, bob))
        manager.initiate(alice, "Bob")
        val tradeId = alice.sent.filterIsInstance<ServerMessage.OpenTrade>().first().tradeId
        manager.updateOffer(alice, tradeId, mapOf(ItemType("DIRT") to 2))
        manager.updateOffer(bob, tradeId, mapOf(ItemType("SAND") to 1))
        manager.accept(alice, tradeId)
        manager.accept(bob, tradeId)
        // Alice gave 2 DIRT, got 1 SAND
        assertEquals(3, alice.inventory[ItemType("DIRT")])
        assertEquals(1, alice.inventory[ItemType("SAND")])
        // Bob gave 1 SAND, got 2 DIRT
        assertEquals(2, bob.inventory[ItemType("SAND")])
        assertEquals(2, bob.inventory[ItemType("DIRT")])
        assertTrue(
            alice.sent.filterIsInstance<ServerMessage.TradeClosed>().any {
                it.reason == "completed"
            })
        assertTrue(
            bob.sent.filterIsInstance<ServerMessage.TradeClosed>().any { it.reason == "completed" })
    }

    @Test
    fun onPlayerDisconnect_cancelsTrade() = runBlocking {
        val alice = testSession(name = "Alice", pos = Vec3(0f, 0f, 0f))
        val bob = testSession(id = "bob-id", name = "Bob", pos = Vec3(5f, 0f, 0f))
        val manager = makeManager(listOf(alice, bob))
        manager.initiate(alice, "Bob")
        manager.onPlayerDisconnect(alice.id)
        assertTrue(
            bob.sent.filterIsInstance<ServerMessage.TradeClosed>().any {
                it.reason == "disconnected"
            })
    }
}
