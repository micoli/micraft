package org.micoli.micraft.game.rpg.character

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class SkipRpgCommandTest {
    @Test
    fun execute_setsRpgOptOut_true() =
        runBlocking<Unit> {
            val session = testSession()
            session.state = session.state.copy(rpgOptOut = false)
            assertFalse(session.state.rpgOptOut)
            val context = testContext(sessions = listOf(session), savePlayer = {})
            SkipRpgCommand().execute(session, "", context)
            assertTrue(session.state.rpgOptOut)
        }

    @Test
    fun execute_sendsNotification() =
        runBlocking<Unit> {
            val session = testSession()
            val context = testContext(sessions = listOf(session), savePlayer = {})
            SkipRpgCommand().execute(session, "", context)
            val notifications = session.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(notifications.isNotEmpty())
        }

    @Test
    fun execute_callsSavePlayer() =
        runBlocking<Unit> {
            val session = testSession()
            var saved = false
            val context = testContext(sessions = listOf(session), savePlayer = { saved = true })
            SkipRpgCommand().execute(session, "", context)
            assertTrue(saved)
        }
}
