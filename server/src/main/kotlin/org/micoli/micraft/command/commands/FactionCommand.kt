package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class FactionCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b1d3a1f0-0003-4a00-9000-000000000003")
    override val name = "faction"
    override val description = "View and change your faction affiliation."
    override val usage = "$command list|join <id>|leave|info"
    override val options = listOf("list", "join", "leave", "info")

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> {
        if (argIndex == 0) return options.filter { it.contains(partial, ignoreCase = true) }
        return context.factionManager
            ?.definitions()
            ?.map { it.id }
            ?.filter { it.contains(partial, ignoreCase = true) } ?: emptyList()
    }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val fm = context.factionManager ?: return
        val lang = session.state.language
        val trimmed = args.trim()
        val sub = trimmed.substringBefore(' ').lowercase()
        val rest = trimmed.substringAfter(' ', "").trim()
        when (sub) {
            "list",
            "info",
            "" -> {
                fm.sendSync(session)
                val listing =
                    fm.definitions().joinToString(", ") { "${it.id} (${it.name})" }.ifBlank { "-" }
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "faction:server:list", listing), "system"))
            }
            "join" -> fm.setAffiliation(session, rest.ifBlank { null })
            "leave" -> fm.setAffiliation(session, null)
            else ->
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "faction:server:usage"), "system"))
        }
    }
}
