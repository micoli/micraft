package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class PreferencesCommandTest {
    private val cmd = PreferencesCommand()

    @Test
    fun sendsOpenPreferences() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(sessions = listOf(session)))
        val sent = session.sent.filterIsInstance<ServerMessage.OpenPreferences>()
        assertEquals(1, sent.size)
    }

    @Test
    fun sendsOnlyOpenPreferences() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(sessions = listOf(session)))
        assertTrue(session.sent.all { it is ServerMessage.OpenPreferences })
    }
}
