package org.micoli.micraft.game.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.FactionsSection
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.social.FactionDefinition
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class FactionManagerTest {

    private fun mgr(
        sessions: List<FakePlayerSession>,
        section: FactionsSection,
        zoneLevelAt: (Int, Int) -> Int = { _, _ -> 1 },
        spawnSlots: (Int, Double) -> List<Pair<Int, Int>> = { count, _ ->
            List(count) { i -> (100 + i * 50) to (200 + i * 50) }
        },
    ): FactionManager {
        val cm = ChatChannelManager()
        val chat = ChatService(cm, {}, { sessions })
        return FactionManager(
                { sessions },
                {},
                chat,
                cm,
                testI18n(),
                {},
                zoneLevelAt = zoneLevelAt,
                lowLevelSpawnSlots = spawnSlots,
            )
            .also { it.applyConfig(section) }
    }

    private val twoFactions =
        FactionsSection(
            enabled = true,
            list =
                listOf(
                    FactionDefinition("red", "Red"),
                    FactionDefinition("blue", "Blue"),
                ))

    @Test
    fun `join and leave updates affiliation`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val fm = mgr(listOf(a), twoFactions)
        fm.setAffiliation(a, "red")
        assertEquals("red", a.state.factionId)
        fm.setAffiliation(a, null)
        assertNull(a.state.factionId)
    }

    @Test
    fun `unknown faction is rejected`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val fm = mgr(listOf(a), twoFactions)
        fm.setAffiliation(a, "green")
        assertNull(a.state.factionId)
        assertTrue(a.sent.any { it is ServerMessage.SocialDenied })
    }

    @Test
    fun `cooldown blocks quick re-affiliation`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val fm = mgr(listOf(a), twoFactions.copy(changeCooldownSeconds = 3600))
        fm.setAffiliation(a, "red")
        fm.setAffiliation(a, "blue")
        assertEquals("red", a.state.factionId)
    }

    @Test
    fun `reconcile drops affiliation to removed faction`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val fm = mgr(listOf(a), twoFactions)
        fm.setAffiliation(a, "red")
        fm.applyConfig(
            FactionsSection(enabled = true, list = listOf(FactionDefinition("blue", "Blue"))))
        fm.reconcile()
        assertNull(a.state.factionId)
    }

    private fun org.micoli.micraft.player.Vec3.block() =
        kotlin.math.floor(x).toInt() to kotlin.math.floor(z).toInt()

    @Test
    fun `spawn map uses auto ring for factions without explicit coords`() {
        val fm = mgr(emptyList(), twoFactions)
        assertEquals(100 to 200, fm.spawnFor("red")?.block())
        assertEquals(150 to 250, fm.spawnFor("blue")?.block())
    }

    @Test
    fun `explicit faction spawn coords win over auto ring`() {
        val section =
            FactionsSection(
                enabled = true,
                list =
                    listOf(
                        FactionDefinition("red", "Red", spawnX = -300, spawnZ = 40),
                        FactionDefinition("blue", "Blue"),
                    ))
        val fm = mgr(emptyList(), section)
        assertEquals(-300 to 40, fm.spawnFor("red")?.block())
        assertEquals(100 to 200, fm.spawnFor("blue")?.block())
    }

    @Test
    fun `first affiliation teleports player to faction spawn`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val fm = mgr(listOf(a), twoFactions)
        fm.setAffiliation(a, "red")
        assertEquals(100 to 200, a.state.pos.block())
    }

    @Test
    fun `sameFaction is false across different factions`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val fm = mgr(listOf(a, b), twoFactions)
        fm.setAffiliation(a, "red")
        fm.setAffiliation(b, "blue")
        assertTrue(!fm.sameFaction(a, b))
        fm.setAffiliation(b, "red")
        // cooldown 0 here → allowed
        assertTrue(fm.sameFaction(a, b))
    }
}
