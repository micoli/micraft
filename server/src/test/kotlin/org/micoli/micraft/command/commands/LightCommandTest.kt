package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class LightCommandTest {
    @Test
    fun lightOn_sendsLightBoostUpdate_enabled() = runBlocking {
        val session = testSession()
        LightOnCommand().execute(session, "", testContext())
        assertTrue(session.sent.any { it is ServerMessage.LightBoostUpdate && it.enabled })
    }

    @Test
    fun lightOn_sendsNotification() = runBlocking {
        val session = testSession()
        LightOnCommand().execute(session, "", testContext())
        assertTrue(session.sent.any { it is ServerMessage.Notification })
    }

    @Test
    fun lightOff_sendsLightBoostUpdate_disabled() = runBlocking {
        val session = testSession()
        LightOffCommand().execute(session, "", testContext())
        assertTrue(session.sent.any { it is ServerMessage.LightBoostUpdate && !it.enabled })
    }

    @Test
    fun lightOff_sendsNotification() = runBlocking {
        val session = testSession()
        LightOffCommand().execute(session, "", testContext())
        assertTrue(session.sent.any { it is ServerMessage.Notification })
    }
}
