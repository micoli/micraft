package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.command.commands.NpcBuyCommand
import org.micoli.micraft.command.commands.NpcSellCommand
import org.micoli.micraft.game.npc.behaviors.SellerNpcBehavior
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class NpcSellerTest {

    // ─── CurrencyUtils ────────────────────────────────────────────────────────

    @Test
    fun currency_deductCopper_sufficient() {
        val newWallet = CurrencyUtils.deductCopper(100L, 37)
        assertEquals(63L, newWallet)
    }

    @Test
    fun currency_deductCopper_exact() {
        val newWallet = CurrencyUtils.deductCopper(50L, 50)
        assertEquals(0L, newWallet)
    }

    @Test
    fun currency_deductCopper_insufficient_throws() {
        assertFailsWith<IllegalArgumentException> { CurrencyUtils.deductCopper(10L, 11) }
    }

    @Test
    fun currency_addCopper() {
        assertEquals(150L, CurrencyUtils.addCopper(100L, 50))
    }

    @Test
    fun currency_addCopper_fromZero() {
        assertEquals(42L, CurrencyUtils.addCopper(0L, 42))
    }

    // ─── SellerNpcBehavior ────────────────────────────────────────────────────

    private fun makeSellerNpc(
        pos: Vec3 = Vec3(0f, 100f, 0f),
        shopItems: List<ShopItemEntry> =
            listOf(ShopItemEntry("COBBLESTONE", buyPrice = 5, sellPrice = 2)),
    ): NpcInstance {
        val def =
            NpcDefinition(
                type = "seller",
                behavior = SellerNpcBehavior(),
                behaviorKey = "seller",
                bbmodelFile = "seller",
                width = 0.6f,
                height = 1.8f,
                wanderSpeed = 0f,
                wanderRadius = 0f,
                shopItems = shopItems,
            )
        return NpcInstance(
            state =
                NpcState(id = "npc-seller-1", name = "Bob", type = "seller", pos = pos, yaw = 0f),
            definition = def,
            spawnPos = pos,
        )
    }

    @Test
    fun sellerBehavior_interact_sendsResult() = runBlocking {
        val npc = makeSellerNpc(pos = Vec3(0f, 100f, 0f))
        val session = testSession(pos = Vec3(1f, 100f, 1f))
        val sent = mutableListOf<ServerMessage>()

        SellerNpcBehavior().onInteract(npc, session, NpcTickContext.live) { sent.add(it) }

        val result = sent.filterIsInstance<ServerMessage.NpcInteractResult>().firstOrNull()
        assertNotNull(result)
        val payload = Json.parseToJsonElement(result.payload).jsonObject
        assertEquals("seller", payload["type"]?.jsonPrimitive?.content)
        assertEquals("Bob", payload["name"]?.jsonPrimitive?.content)
        val items = payload["shopItems"]?.jsonArray
        assertNotNull(items)
        assertEquals(1, items.size)
        assertEquals("COBBLESTONE", items[0].jsonObject["itemType"]?.jsonPrimitive?.content)
    }

    @Test
    fun sellerBehavior_interact_outOfRange_sendsNothing() = runBlocking {
        val npc = makeSellerNpc(pos = Vec3(0f, 100f, 0f))
        val farSession = testSession(pos = Vec3(100f, 100f, 100f))
        val sent = mutableListOf<ServerMessage>()

        SellerNpcBehavior().onInteract(npc, farSession, NpcTickContext.live) { sent.add(it) }

        assertTrue(sent.isEmpty())
    }

    // ─── NpcBuyCommand error paths ────────────────────────────────────────────

    @Test
    fun npcBuyCommand_noNpcManager_sendsUnavailableNotification() = runBlocking {
        val session = testSession()
        val ctx = testContext(npcManager = null)

        NpcBuyCommand().execute(session, "npc-1 COBBLESTONE 1", ctx)

        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().firstOrNull()
        assertNotNull(notif)
        assertTrue(notif.message.isNotBlank())
    }

    // ─── NpcSellCommand error paths ───────────────────────────────────────────

    @Test
    fun npcSellCommand_noNpcManager_sendsUnavailableNotification() = runBlocking {
        val session = testSession()
        val ctx = testContext(npcManager = null)

        NpcSellCommand().execute(session, "npc-1 COBBLESTONE 1", ctx)

        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().firstOrNull()
        assertNotNull(notif)
        assertTrue(notif.message.isNotBlank())
    }
}
