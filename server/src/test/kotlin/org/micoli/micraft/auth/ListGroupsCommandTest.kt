package org.micoli.micraft.auth

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class ListGroupsCommandTest {

    @Test
    fun execute_withoutGroupsConfig_notifiesUnavailable() = runBlocking {
        val session = testSession()
        ListGroupsCommand().execute(session, "", testContext(groupsConfig = null))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("not available", ignoreCase = true))
    }

    @Test
    fun execute_listsAdminGroupAndCustomGroups() = runBlocking {
        val groupsConfig =
            GroupsConfig(groups = listOf(GroupEntry("moderator", listOf("give", "kick"))))
        val session = testSession()
        ListGroupsCommand().execute(session, "", testContext(groupsConfig = groupsConfig))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("admin"))
        assertTrue(notif.message.contains("moderator"))
        assertTrue(notif.message.contains("give"))
    }

    @Test
    fun execute_groupWithNoPermissions_showsNone() = runBlocking {
        val groupsConfig = GroupsConfig(groups = listOf(GroupEntry("guest", emptyList())))
        val session = testSession()
        ListGroupsCommand().execute(session, "", testContext(groupsConfig = groupsConfig))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("(none)"))
    }
}
