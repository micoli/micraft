package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class ShadersCommandTest {
    private val cmd = ShadersCommand()

    @Test
    fun toggleOff_whenEnabled() = runBlocking {
        val session = testSession(shadersEnabled = true)
        cmd.execute(session, "", testContext())
        assertFalse(session.state.shadersEnabled)
    }

    @Test
    fun toggleOn_whenDisabled() = runBlocking {
        val session = testSession(shadersEnabled = false)
        cmd.execute(session, "", testContext())
        assertTrue(session.state.shadersEnabled)
    }

    @Test
    fun sendsNotification() = runBlocking {
        val session = testSession(shadersEnabled = true)
        cmd.execute(session, "", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun sendsShadersUpdate() = runBlocking {
        val session = testSession(shadersEnabled = true)
        cmd.execute(session, "", testContext())
        val updates = session.sent.filterIsInstance<ServerMessage.ShadersUpdate>()
        assertEquals(1, updates.size)
        assertFalse(updates[0].enabled)
    }

    @Test
    fun savesPlayer() = runBlocking {
        val saved = mutableListOf<org.micoli.micraft.session.PlayerSession>()
        val session = testSession()
        cmd.execute(session, "", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }

    @Test
    fun onArg_enablesShaders() = runBlocking {
        val session = testSession(shadersEnabled = false)
        cmd.execute(session, "on", testContext())
        assertTrue(session.state.shadersEnabled)
    }

    @Test
    fun offArg_disablesShaders() = runBlocking {
        val session = testSession(shadersEnabled = true)
        cmd.execute(session, "off", testContext())
        assertFalse(session.state.shadersEnabled)
    }

    @Test
    fun onArg_whenAlreadyEnabled_staysEnabled() = runBlocking {
        val session = testSession(shadersEnabled = true)
        cmd.execute(session, "on", testContext())
        assertTrue(session.state.shadersEnabled)
    }

    @Test
    fun invalidArg_sendsUsageNotification_doesNotSave() = runBlocking {
        val saved = mutableListOf<org.micoli.micraft.session.PlayerSession>()
        val session = testSession(shadersEnabled = true)
        cmd.execute(session, "maybe", testContext(savePlayer = { saved.add(it) }))
        assertTrue(session.state.shadersEnabled)
        assertTrue(saved.isEmpty())
        val updates = session.sent.filterIsInstance<ServerMessage.ShadersUpdate>()
        assertTrue(updates.isEmpty())
    }

    @Test
    fun options_containsOnAndOff() {
        assertEquals(listOf("on", "off"), cmd.options)
    }
}
