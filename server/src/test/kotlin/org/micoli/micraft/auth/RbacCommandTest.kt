package org.micoli.micraft.auth

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testPlayerState
import org.micoli.micraft.support.testSession

class RbacCommandTest {

    private val groupsConfig =
        GroupsConfig(
            groups =
                listOf(
                    GroupEntry("player", listOf("action.break")),
                    GroupEntry("moderator", listOf("give")),
                ))

    @Test
    fun `setgroup notifies connected character and updates live permissions`() =
        runBlocking<Unit> {
            val adminSession = testSession(id = "admin-id", name = "Admin")
            val aliceSession = testSession(id = "alice-id", name = "Alice")

            val context =
                testContext(
                    sessions = listOf(adminSession, aliceSession), groupsConfig = groupsConfig)

            SetGroupCommand().execute(adminSession, "Alice moderator", context)

            val aliceMessages = aliceSession.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(
                aliceMessages.any { it.message.contains("group", ignoreCase = true) },
                "Alice should receive a group-update notification, got: ${aliceMessages.map { it.message }}")

            val adminMessages = adminSession.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(
                adminMessages.any { it.message.contains("Alice", ignoreCase = true) },
                "Admin should receive confirmation")

            assertTrue(
                Permission("give") in aliceSession.permissions,
                "Alice's live session should immediately gain the moderator group's permissions")
            assertTrue("moderator" in aliceSession.state.groups)
        }

    @Test
    fun `removegroup notifies connected character and updates live permissions`() =
        runBlocking<Unit> {
            val adminSession = testSession(id = "admin-id", name = "Admin")
            val aliceSession = testSession(id = "alice-id", name = "Alice")
            aliceSession.state = aliceSession.state.copy(groups = listOf("player", "moderator"))
            aliceSession.permissions = groupsConfig.resolvePermissions(aliceSession.state.groups)

            val context =
                testContext(
                    sessions = listOf(adminSession, aliceSession), groupsConfig = groupsConfig)

            RemoveGroupCommand().execute(adminSession, "Alice moderator", context)

            val aliceMessages = aliceSession.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(
                aliceMessages.any { it.message.contains("group", ignoreCase = true) },
                "Alice should receive a group-update notification, got: ${aliceMessages.map { it.message }}")

            assertTrue(
                Permission("give") !in aliceSession.permissions,
                "Alice's live session should immediately lose the moderator group's permissions")
            assertTrue("moderator" !in aliceSession.state.groups)
        }

    @Test
    fun `setgroup on an offline character persists to their save file`() =
        runBlocking<Unit> {
            val worldDir = createTempDirectory("rbac-command-test-world")
            val persistence = WorldPersistence(worldDir)
            persistence.savePlayerState("Bob", testPlayerState(id = "bob-id", name = "Bob"))

            val adminSession = testSession(id = "admin-id", name = "Admin")
            val context =
                testContext(
                    sessions = listOf(adminSession),
                    persistence = persistence,
                    groupsConfig = groupsConfig)

            SetGroupCommand().execute(adminSession, "Bob moderator", context)

            val adminMessages = adminSession.sent.filterIsInstance<ServerMessage.Notification>()
            assertEquals(1, adminMessages.size, "Only admin confirmation, no live notification")
            assertTrue(adminMessages[0].message.contains("Bob", ignoreCase = true))

            val saved = persistence.loadPlayerState("Bob")
            assertTrue(saved != null && "moderator" in saved.groups)
        }

    @Test
    fun `setgroup on an unknown character sends not_found`() =
        runBlocking<Unit> {
            val adminSession = testSession(id = "admin-id", name = "Admin")
            val context = testContext(sessions = listOf(adminSession), groupsConfig = groupsConfig)

            SetGroupCommand().execute(adminSession, "Ghost moderator", context)

            val adminMessages = adminSession.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(adminMessages.any { it.message.contains("Ghost") })
        }
}
