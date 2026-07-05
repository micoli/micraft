package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWeatherManager

class WeatherForecastCommandTest {
    private val cmd = WeatherForecastCommand()

    @Test
    fun noWeatherManager_sendsUnavailable() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(weatherManager = null))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun noZones_sendsNoZones() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(weatherManager = testWeatherManager()))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }
}
