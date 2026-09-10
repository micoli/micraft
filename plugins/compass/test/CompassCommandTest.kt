package org.micoli.micraft.plugins.compass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.completions
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class CompassCommandTest {
    private val cmd = CompassCommand()

    @Test
    fun blankArgs_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        assertTrue(
            session.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("Usage") || it.message.contains("compass")
            })
        assertTrue(session.sent.none { it is ServerMessage.CompassUpdate })
    }

    @Test
    fun coords_sendActiveCompassUpdate() = runBlocking {
        val session = testSession()
        cmd.execute(session, "100 64 200", testContext())
        val update = session.sent.filterIsInstance<ServerMessage.CompassUpdate>().single()
        assertEquals(100f, update.x)
        assertEquals(64f, update.y)
        assertEquals(200f, update.z)
        assertTrue(update.active)
    }

    @Test
    fun namedPoint_resolvesToCoords() = runBlocking {
        val session = testSession()
        val points = mapOf("cavern - forest_0" to Vec3(300f, 20f, 400f))
        cmd.execute(session, "cavern - forest_0", testContext(namedPoints = { points }))
        val update = session.sent.filterIsInstance<ServerMessage.CompassUpdate>().single()
        assertEquals(300f, update.x)
        assertEquals(400f, update.z)
        assertEquals("cavern - forest_0", update.label)
    }

    @Test
    fun unknownNamedPoint_sendsNotFound() = runBlocking {
        val session = testSession()
        cmd.execute(session, "nowhere", testContext(namedPoints = { emptyMap() }))
        assertTrue(
            session.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("nowhere")
            })
        assertTrue(session.sent.none { it is ServerMessage.CompassUpdate })
    }

    @Test
    fun clear_sendsInactiveCompassUpdate() = runBlocking {
        val session = testSession()
        cmd.execute(session, "clear", testContext())
        val update = session.sent.filterIsInstance<ServerMessage.CompassUpdate>().single()
        assertTrue(!update.active)
    }

    @Test
    fun namedPoint_appearsInAutocomplete() = runBlocking {
        val points = mapOf("cavern - desert_0" to Vec3(10f, 5f, 10f))
        val result =
            cmd.completions(0, "cavern", testContext(namedPoints = { points })).map { it.label }
        assertTrue(result.contains("cavern - desert_0"))
    }
}
