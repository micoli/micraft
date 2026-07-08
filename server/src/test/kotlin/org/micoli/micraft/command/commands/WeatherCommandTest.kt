package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWeatherManager

class WeatherCommandTest {
    private val cmd = WeatherCommand()

    @Test
    fun noWeatherManager_sendsUnavailable() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(weatherManager = null))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun noArgs_noZones_sendsNoZones() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(weatherManager = testWeatherManager()))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun none_clearAllZones_broadcastsWeatherUpdate() = runBlocking {
        val session = testSession()
        val broadcasts = mutableListOf<ServerMessage>()
        cmd.execute(
            session,
            "none",
            testContext(weatherManager = testWeatherManager(), broadcast = { broadcasts.add(it) }))
        assertTrue(broadcasts.filterIsInstance<ServerMessage.WeatherUpdate>().isNotEmpty())
    }

    @Test
    fun none_sendsCleared_notification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "none", testContext(weatherManager = testWeatherManager()))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun invalidType_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "invalidtype", testContext(weatherManager = testWeatherManager()))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun rain_noDiscoveredChunk_sendsNoDiscoveredChunk() = runBlocking {
        val session = testSession()
        // testWorld() has no discovered chunks → forceWeather returns false
        cmd.execute(session, "rain", testContext(weatherManager = testWeatherManager()))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }
}
