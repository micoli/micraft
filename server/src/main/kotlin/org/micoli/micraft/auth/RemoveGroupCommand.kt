package org.micoli.micraft.auth

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.command.canonicalPlayerName
import org.micoli.micraft.command.playerCompletions
import org.micoli.micraft.command.resolvePlayerSession
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class RemoveGroupCommand : PluginCommand {
    override val id: UUID = UUID.fromString("d5e6f7a8-b9c0-4123-d456-e7f8a9b0c123")
    override val name = "rbac:removegroup"
    override val permission = CorePermissions.ADMIN
    override val description = "Remove in-game RBAC groups from a character."
    override val usage = "$command <playerName> <group1,group2,...>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<Completion> = if (argIndex == 0) context.playerCompletions(partial) else emptyList()

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val parts = args.trim().split(" ", limit = 2)
        if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            session.send(ServerMessage.Notification(usage))
            return
        }
        val playerName = context.canonicalPlayerName(parts[0])
        val toRemove = parts[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val groupsConfig = context.groupsConfig
        if (groupsConfig == null) {
            session.send(ServerMessage.Notification("Groups config not available."))
            return
        }
        val result =
            mutatePlayerGroups(
                playerName,
                context.sessions(),
                context.persistence,
                groupsConfig,
                context.savePlayer) { current ->
                    current.filter { it !in toRemove }
                }
        when (result) {
            is PlayerRbacResult.NotFound ->
                session.send(
                    ServerMessage.Notification(
                        i18n.t(lang, "rbac:server:player_not_found", playerName)))
            is PlayerRbacResult.Applied -> {
                session.send(
                    ServerMessage.Notification(
                        i18n.t(
                            lang,
                            "rbac:server:group_removed",
                            playerName,
                            toRemove.joinToString(", "))))
                context
                    .resolvePlayerSession(playerName)
                    ?.send(
                        ServerMessage.Notification(i18n.t(lang, "rbac:server:your_groups_updated")))
            }
        }
    }
}
