package org.micoli.micraft.game.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.social.GuildPermission
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class GuildManagerTest {

    private class Ctx(sessions: List<FakePlayerSession>) {
        val cm = ChatChannelManager()
        val chat = ChatService(cm, {}, { sessions })
        val registry = GuildRegistry(null)
        val returned = mutableMapOf<String, Map<ItemType, Int>>()
        val gm =
            GuildManager(registry, { sessions }, {}, chat, cm, testI18n()) { name, items ->
                returned[name] = items
            }
    }

    private fun denied(s: FakePlayerSession) = s.sent.filterIsInstance<ServerMessage.SocialDenied>()

    @Test
    fun `founder gets top rank and one guild per player is enforced`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val ctx = Ctx(listOf(a))
        ctx.gm.create(a, "Testers", "TST")
        assertEquals("Master", a.state.guildRank)
        ctx.gm.create(a, "Other", "OTH")
        assertTrue(denied(a).isNotEmpty())
        assertNull(ctx.registry.byName("Other"))
    }

    @Test
    fun `name and tag uniqueness`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val ctx = Ctx(listOf(a, b))
        ctx.gm.create(a, "Guild", "AAA")
        ctx.gm.create(b, "guild", "BBB")
        assertNull(ctx.registry.guildOf(b.id))
    }

    @Test
    fun `withdraw requires the flag`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val ctx = Ctx(listOf(a, b))
        ctx.gm.create(a, "Bankers", "BNK")
        ctx.gm.invite(a, "B")
        ctx.gm.respondInvite(b, ctx.gm.pendingGuildIdFor(b.id)!!, true)

        val stone = ItemType("STONE")
        a.inventory[stone] = 10
        ctx.gm.bankDeposit(a, stone, 6)
        assertEquals(4, a.inventory[stone])
        assertEquals(6, ctx.registry.guildOf(a.id)!!.bank[stone])

        // b is Recruit → no BANK_WITHDRAW
        ctx.gm.bankWithdraw(b, stone, 1)
        assertTrue(denied(b).isNotEmpty())

        // promote b to Officer → withdraw works
        ctx.gm.setRank(a, b.id, "Officer")
        ctx.gm.bankWithdraw(b, stone, 2)
        assertEquals(2, b.inventory[stone])
        assertEquals(4, ctx.registry.guildOf(a.id)!!.bank[stone])
    }

    @Test
    fun `disband clears members and mails bank to owner`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val ctx = Ctx(listOf(a))
        ctx.gm.create(a, "Solo", "SOL")
        val stone = ItemType("STONE")
        a.inventory[stone] = 5
        ctx.gm.bankDeposit(a, stone, 5)
        ctx.gm.disband(a)
        assertNull(a.state.guildId)
        assertNull(ctx.registry.get(ctx.registry.all().firstOrNull()?.id ?: "x"))
        assertEquals(mapOf(stone to 5), ctx.returned["A"])
    }

    @Test
    fun `owner cannot leave with members present`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val ctx = Ctx(listOf(a, b))
        ctx.gm.create(a, "Stay", "STY")
        ctx.gm.invite(a, "B")
        ctx.gm.respondInvite(b, ctx.gm.pendingGuildIdFor(b.id)!!, true)
        ctx.gm.leave(a)
        assertTrue(denied(a).isNotEmpty())
        assertEquals("Stay", ctx.registry.guildOf(a.id)!!.name)
        assertTrue(GuildPermission.DISBAND in ctx.registry.guildOf(a.id)!!.flagsOf(a.id))
    }
}
