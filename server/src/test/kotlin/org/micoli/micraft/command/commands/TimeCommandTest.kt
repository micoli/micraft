package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class TimeCommandTest {
    private val cmd = TimeCommand()

    @Test
    fun noArgs_showsCurrentTime_noon() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(getGameTime = { 36_000L }))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().last()
        assertTrue(notif.message.contains("12:00"), "Expected 12:00 in '${notif.message}'")
    }

    @Test
    fun noArgs_showsMidnight() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(getGameTime = { 0L }))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().last()
        assertTrue(notif.message.contains("00:00"), "Expected 00:00 in '${notif.message}'")
    }

    @Test
    fun setHour_6_setsCorrectTicks() = runBlocking {
        var capturedTicks = -1L
        val session = testSession()
        cmd.execute(session, "6", testContext(setGameTime = { capturedTicks = it }))
        assertEquals(18_000L, capturedTicks)
    }

    @Test
    fun setHour_0_setsMidnight() = runBlocking {
        var capturedTicks = -1L
        val session = testSession()
        cmd.execute(session, "0", testContext(setGameTime = { capturedTicks = it }))
        assertEquals(0L, capturedTicks)
    }

    @Test
    fun setHour_23_setsLateNight() = runBlocking {
        var capturedTicks = -1L
        val session = testSession()
        cmd.execute(session, "23", testContext(setGameTime = { capturedTicks = it }))
        assertEquals(72_000L * 23 / 24, capturedTicks)
    }

    @Test
    fun setHour_broadcasts_TimeUpdate() = runBlocking {
        val broadcast = mutableListOf<ServerMessage>()
        val session = testSession()
        cmd.execute(session, "12", testContext(broadcast = { broadcast.add(it) }))
        val updates = broadcast.filterIsInstance<ServerMessage.TimeUpdate>()
        assertEquals(1, updates.size)
        assertEquals(36_000L, updates[0].gameTicks)
    }

    @Test
    fun setHour_sendsNotification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "6", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun invalidText_sendsUsage_noBroadcast() = runBlocking {
        val broadcast = mutableListOf<ServerMessage>()
        val session = testSession()
        cmd.execute(session, "noon", testContext(broadcast = { broadcast.add(it) }))
        assertTrue(broadcast.filterIsInstance<ServerMessage.TimeUpdate>().isEmpty())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun outOfRange_25_sendsUsage() = runBlocking {
        val broadcast = mutableListOf<ServerMessage>()
        val session = testSession()
        cmd.execute(session, "25", testContext(broadcast = { broadcast.add(it) }))
        assertTrue(broadcast.filterIsInstance<ServerMessage.TimeUpdate>().isEmpty())
    }

    @Test
    fun negative_sendsUsage() = runBlocking {
        val broadcast = mutableListOf<ServerMessage>()
        val session = testSession()
        cmd.execute(session, "-1", testContext(broadcast = { broadcast.add(it) }))
        assertTrue(broadcast.filterIsInstance<ServerMessage.TimeUpdate>().isEmpty())
    }

    @Test
    fun options_is_0_to_23() {
        assertEquals((0..23).map { it.toString() }, cmd.options)
    }
}
