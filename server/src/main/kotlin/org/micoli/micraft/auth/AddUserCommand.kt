package org.micoli.micraft.auth

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class AddUserCommand : PluginCommand {
    override val id: UUID = UUID.fromString("b3c4d5e6-f7a8-4901-b234-c5d6e7f8a901")
    override val name = "adduser"
    override val permission = "admin"
    override val description =
        "Add a local auth user. Usage: /adduser <email> <password> [displayName] [group1,group2,...]"
    override val usage = "$command <email> <password> [displayName] [group1,group2,...]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val provider = context.authProvider as? LocalAuthProvider
        if (provider == null) {
            session.send(ServerMessage.Notification("Local auth provider not active."))
            return
        }
        val parts = args.trim().split(" ", limit = 4)
        if (parts.size < 2) {
            session.send(
                ServerMessage.Notification(
                    "Usage: /adduser <email> <password> [displayName] [group1,group2,...]"))
            return
        }
        val email = parts[0]
        val password = parts[1]
        val displayName = if (parts.size >= 3) parts[2] else email
        val groups =
            if (parts.size >= 4) parts[3].split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else emptyList()
        runCatching { provider.addUser(email, password, displayName, groups) }
            .onSuccess { session.send(ServerMessage.Notification("User added: $email")) }
            .onFailure { e -> session.send(ServerMessage.Notification("Failed: ${e.message}")) }
    }
}
