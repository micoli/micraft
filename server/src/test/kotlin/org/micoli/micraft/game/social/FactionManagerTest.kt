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

    private fun mgr(sessions: List<FakePlayerSession>, section: FactionsSection): FactionManager {
        val cm = ChatChannelManager()
        val chat = ChatService(cm, {}, { sessions })
        return FactionManager({ sessions }, {}, chat, cm, testI18n(), {}).also {
            it.applyConfig(section)
        }
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
