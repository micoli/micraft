package org.micoli.micraft.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.auth.Permission
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

/** A command must never be listed and refused, nor hidden and runnable. */
class CommandGateParityTest {

    @Test
    fun visibleCommandFilterAndExecutionGate_agreeOnEveryCommand() = runBlocking {
        val gameLoop = GameLoop(testWorld())
        val gated = gameLoop.knownCommandPermissions().toList()
        assertTrue(gated.size >= 2, "need at least two distinct gated permissions to split on")
        val session = testSession(id = "p1", name = "Scout")
        session.permissions = setOf(gated.first())
        val refusal = gameLoop.i18n.t(session.state.language, "rbac:server:no_permission")

        val visible = gameLoop.buildPreferencesSync(session).commands.map { it.command }.toSet()
        val allCommands = gameLoop.registeredCommands()
        assertTrue(allCommands.any { it !in visible }, "the permission set must hide something")

        allCommands.forEach { name ->
            session.sent.clear()
            runCatching { gameLoop.handleCommand(session, name) }
            val refused =
                session.sent.any { it is ServerMessage.Notification && it.message == refusal }
            assertEquals(name !in visible, refused, "gates disagree on $name")
        }
    }

    @Test
    fun wildcardSession_seesAndRunsEverything() = runBlocking {
        val gameLoop = GameLoop(testWorld())
        val session = testSession(id = "p2", name = "Root")
        session.permissions = setOf(Permission.WILDCARD)
        val refusal = gameLoop.i18n.t(session.state.language, "rbac:server:no_permission")

        gameLoop.registeredCommands().forEach { name ->
            session.sent.clear()
            runCatching { gameLoop.handleCommand(session, name) }
            assertTrue(
                session.sent.none { it is ServerMessage.Notification && it.message == refusal })
        }
    }
}
