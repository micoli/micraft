package org.micoli.micraft.auth

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class RemoveGroupCommand : PluginCommand {
    override val id: UUID = UUID.fromString("d5e6f7a8-b9c0-4123-d456-e7f8a9b0c123")
    override val name = "rbac:removegroup"
    override val permission = "admin"
    override val description = "Remove groups from a user."
    override val usage = "$command <email> <group1,group2,...>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val provider = context.authProvider as? LocalAuthProvider
        if (provider == null) {
            session.send(ServerMessage.Notification("Local auth provider not active."))
            return
        }
        val parts = args.trim().split(" ", limit = 2)
        if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            session.send(ServerMessage.Notification(usage))
            return
        }
        val email = parts[0]
        val toRemove = parts[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
        runCatching {
                val current = provider.getUserGroups(email) ?: error("User not found: $email")
                val updated = current.filter { it !in toRemove }
                provider.setUserGroups(email, updated)
            }
            .onSuccess {
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(
                            session.state.language,
                            "rbac:server:group_removed",
                            email,
                            toRemove.joinToString(", "))))
                context
                    .sessions()
                    .filter { it.userName.equals(email, ignoreCase = true) }
                    .forEach { affected ->
                        affected.send(
                            ServerMessage.Notification(
                                context.i18n.t(
                                    affected.state.language, "rbac:server:your_groups_updated")))
                    }
            }
            .onFailure { e -> session.send(ServerMessage.Notification("Failed: ${e.message}")) }
    }
}
