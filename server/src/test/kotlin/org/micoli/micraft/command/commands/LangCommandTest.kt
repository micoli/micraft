package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class LangCommandTest {
    private val cmd = LangCommand()

    @Test
    fun noArgs_listsLocales() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("en") })
    }

    @Test
    fun unknownLocale_sendsError() = runBlocking {
        val session = testSession()
        cmd.execute(session, "xx", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Unknown") || it.message.contains("xx") })
    }

    @Test
    fun alreadyCurrent_sendsAlreadyMessage() = runBlocking {
        val session = testSession(language = "en")
        cmd.execute(session, "en", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("already") || it.message.contains("en") })
    }

    @Test
    fun validLocale_updatesState() = runBlocking {
        val session = testSession(language = "en")
        // test resources include both "en" and "fr" so "fr" is a valid locale
        cmd.execute(session, "fr", testContext())
        assertEquals("fr", session.state.language)
    }

    @Test
    fun validLocale_callsSavePlayer() = runBlocking {
        val saved = mutableListOf<PlayerSession>()
        val session = testSession(language = "en")
        cmd.execute(session, "fr", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }

    @Test
    fun validLocale_doesNotSave_whenAlready() = runBlocking {
        val saved = mutableListOf<PlayerSession>()
        val session = testSession(language = "en")
        cmd.execute(session, "en", testContext(savePlayer = { saved.add(it) }))
        assertEquals(0, saved.size)
    }
}
