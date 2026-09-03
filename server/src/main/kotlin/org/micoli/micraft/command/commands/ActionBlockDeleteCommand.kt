package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.hasPermission
import org.micoli.micraft.protocol.ServerMessage

/** `/actionblock:delete <name>` — removes the action-block logic; the block itself stays. */
class ActionBlockDeleteCommand : CommandHandler {
    override val id: UUID = UUID.fromString("4d3a8d9c-6e5f-4a7b-9c3d-3e4f5a6b7c8d")
    override val name = "actionblock:delete"
    override val description = "Delete a named action block."
    override val usage = "$command <name>"

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        if (argIndex == 0)
            context.actionBlockRegistry
                ?.all()
                ?.map { it.name }
                ?.filter { it.contains(partial, ignoreCase = true) } ?: emptyList()
        else emptyList()

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val registry = context.actionBlockRegistry
        if (registry == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:unavailable")))
            return
        }
        val name = args.trim().substringBefore(' ').trim()
        val block = registry.byName(name)
        if (name.isEmpty() || block == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:usage")))
            return
        }
        if (block.owner != session.state.name && !session.hasPermission("actionblock:edit")) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "actionblock:server:no_permission")))
            return
        }
        registry.removeAt(block.pos)
        context.broadcast(ServerMessage.ActionBlockRemove(block.pos))
        session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:deleted", name)))
    }
}
