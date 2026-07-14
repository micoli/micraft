package org.micoli.micraft.plugins.goto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

private fun testNpcManager(vararg defs: Pair<String, NpcDefinition>): NpcManager {
    val m = NpcManager(broadcast = {})
    m.loadDefinitions(defs.toMap())
    return m
}

private fun staticDef(type: String = "SELLER") =
    NpcDefinition(
        type = type,
        behavior = StaticNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
    )

class GotoCommandTest {
    private val cmd = GotoCommand()

    @Test
    fun blankArgs_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Usage") || it.message.contains("goto") })
    }

    @Test
    fun unknownPlayer_sendsError() = runBlocking {
        val session = testSession()
        cmd.execute(session, "Ghost", testContext(sessions = listOf(session)))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("not found") || it.message.contains("Ghost") })
    }

    @Test
    fun validPlayer_updatesCallerPos() = runBlocking {
        val target = testSession(id = "target-id", name = "Bob", pos = Vec3(100f, 10f, 200f))
        val session = testSession()
        cmd.execute(session, "Bob", testContext(sessions = listOf(session, target)))
        assertEquals(100f, session.state.pos.x)
        assertEquals(200f, session.state.pos.z)
    }

    @Test
    fun validPlayer_resetsVy() = runBlocking {
        val target = testSession(id = "target-id", name = "Bob", pos = Vec3(100f, 10f, 200f))
        val session = testSession()
        session.vy = 5f
        cmd.execute(session, "Bob", testContext(sessions = listOf(session, target)))
        assertEquals(0f, session.vy)
    }

    @Test
    fun validPlayer_sendsPlayerUpdateAndNotification() = runBlocking {
        val target = testSession(id = "target-id", name = "Bob", pos = Vec3(100f, 10f, 200f))
        val session = testSession()
        cmd.execute(session, "Bob", testContext(sessions = listOf(session, target)))
        assertTrue(session.sent.any { it is ServerMessage.PlayerUpdate })
        assertTrue(
            session.sent.filterIsInstance<ServerMessage.Notification>().any {
                it.message.contains("Bob")
            })
    }

    @Test
    fun npcTarget_teleportsToNpc() = runBlocking {
        val npcManager = testNpcManager("SELLER" to staticDef())
        npcManager.spawnNpc("Guard", "SELLER", Vec3(50f, 10f, 60f))
        val session = testSession()
        cmd.execute(session, "Guard", testContext(npcManager = npcManager))
        assertEquals(50f, session.state.pos.x)
        assertEquals(60f, session.state.pos.z)
    }

    @Test
    fun npcTarget_playerTakesPriorityOverNpc() = runBlocking {
        val npcManager = testNpcManager("SELLER" to staticDef())
        npcManager.spawnNpc("Bob", "SELLER", Vec3(999f, 10f, 999f))
        val target = testSession(id = "target-id", name = "Bob", pos = Vec3(100f, 10f, 200f))
        val session = testSession()
        cmd.execute(
            session,
            "Bob",
            testContext(sessions = listOf(session, target), npcManager = npcManager))
        assertEquals(100f, session.state.pos.x)
        assertEquals(200f, session.state.pos.z)
    }

    @Test
    fun unknownTarget_withNpcManager_sendsNotFound() = runBlocking {
        val npcManager = testNpcManager("SELLER" to staticDef())
        val session = testSession()
        cmd.execute(session, "Ghost", testContext(npcManager = npcManager))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Ghost") })
    }

    @Test
    fun namedPoint_teleportsToPoint() = runBlocking {
        val session = testSession()
        val points = mapOf("cavern - forest_0" to Vec3(300f, 20f, 400f))
        cmd.execute(session, "cavern - forest_0", testContext(namedPoints = { points }))
        assertEquals(300f, session.state.pos.x)
        assertEquals(400f, session.state.pos.z)
    }

    @Test
    fun namedPoint_appearsInAutocomplete() = runBlocking {
        val points =
            mapOf(
                "cavern - desert_0" to Vec3(10f, 5f, 10f),
                "cavern - forest_1" to Vec3(20f, 5f, 20f))
        val result = cmd.completeArg(0, "cavern", null, testContext(namedPoints = { points }))
        assertTrue(result.contains("cavern - desert_0"))
        assertTrue(result.contains("cavern - forest_1"))
    }

    @Test
    fun unknownNamedPoint_sendsNotFound() = runBlocking {
        val session = testSession()
        cmd.execute(session, "cavern - snow_99", testContext(namedPoints = { emptyMap() }))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notif.any {
                it.message.contains("cavern - snow_99") || it.message.contains("not found")
            })
    }
}
