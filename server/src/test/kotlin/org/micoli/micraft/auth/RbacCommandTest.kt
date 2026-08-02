package org.micoli.micraft.auth

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testAuthProvider
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class RbacCommandTest {

    private fun setupProvider(usersFile: java.nio.file.Path): LocalAuthProvider {
        usersFile.toFile().writeText("users: []\n")
        val groupsConfig =
            GroupsConfig(
                groups =
                    listOf(
                        GroupEntry("player", listOf("action.break")),
                        GroupEntry("moderator", listOf("give")),
                    ))
        val provider = testAuthProvider(usersFile, groupsConfig)
        provider.addUser("alice@test.com", "pass", "Alice", listOf("player"))
        return provider
    }

    @Test
    fun `setgroup notifies connected user`() =
        runBlocking<Unit> {
            val tmp = Files.createTempFile("micraft-users", ".yaml")
            val provider = setupProvider(tmp)

            val adminSession = testSession(id = "admin-id", name = "Admin")
            val aliceSession =
                testSession(id = "alice-id", name = "Alice", userName = "alice@test.com")

            val context =
                testContext(
                    sessions = listOf(adminSession, aliceSession),
                    authProvider = provider,
                    groupsConfig = provider.groupsConfig,
                )

            SetGroupCommand().execute(adminSession, "alice@test.com moderator", context)

            val aliceMessages = aliceSession.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(
                aliceMessages.any { it.message.contains("group", ignoreCase = true) },
                "Alice should receive group-update notification, got: ${aliceMessages.map { it.message }}")

            val adminMessages = adminSession.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(
                adminMessages.any { it.message.contains("alice@test.com", ignoreCase = true) },
                "Admin should receive confirmation")

            tmp.toFile().delete()
        }

    @Test
    fun `removegroup notifies connected user`() =
        runBlocking<Unit> {
            val tmp = Files.createTempFile("micraft-users", ".yaml")
            val provider = setupProvider(tmp)
            provider.setUserGroups("alice@test.com", listOf("player", "moderator"))

            val adminSession = testSession(id = "admin-id", name = "Admin")
            val aliceSession =
                testSession(id = "alice-id", name = "Alice", userName = "alice@test.com")

            val context =
                testContext(
                    sessions = listOf(adminSession, aliceSession),
                    authProvider = provider,
                    groupsConfig = provider.groupsConfig,
                )

            RemoveGroupCommand().execute(adminSession, "alice@test.com moderator", context)

            val aliceMessages = aliceSession.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(
                aliceMessages.any { it.message.contains("group", ignoreCase = true) },
                "Alice should receive group-update notification, got: ${aliceMessages.map { it.message }}")

            tmp.toFile().delete()
        }

    @Test
    fun `setgroup does not notify offline user`() =
        runBlocking<Unit> {
            val tmp = Files.createTempFile("micraft-users", ".yaml")
            val provider = setupProvider(tmp)

            val adminSession = testSession(id = "admin-id", name = "Admin")

            val context =
                testContext(
                    sessions = listOf(adminSession),
                    authProvider = provider,
                    groupsConfig = provider.groupsConfig,
                )

            SetGroupCommand().execute(adminSession, "alice@test.com moderator", context)

            val adminMessages = adminSession.sent.filterIsInstance<ServerMessage.Notification>()
            assertEquals(1, adminMessages.size, "Only admin confirmation, no extra messages")
            assertTrue(adminMessages[0].message.contains("alice@test.com", ignoreCase = true))

            tmp.toFile().delete()
        }
}
