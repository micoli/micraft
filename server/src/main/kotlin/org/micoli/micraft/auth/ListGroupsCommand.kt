package org.micoli.micraft.auth

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.PluginCommand
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class ListGroupsCommand : PluginCommand {
    override val id: UUID = UUID.fromString("e6f7a8b9-c0d1-4234-e567-f8a9b0c1d234")
    override val name = "rbac:listgroups"
    override val permission = "admin"
    override val description = "List all groups and their permissions."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val groupsConfig = context.groupsConfig
        if (groupsConfig == null) {
            session.send(ServerMessage.Notification("Groups config not available."))
            return
        }
        val lines =
            groupsConfig.allGroups.joinToString("\n") { g ->
                val perms =
                    if (g.permissions.isEmpty()) "(none)" else g.permissions.joinToString(", ")
                "  ${g.name}: $perms"
            }
        session.send(
            ServerMessage.Notification(
                context.i18n.t(session.state.language, "rbac:server:groups_list", "\n$lines")))
    }
}
