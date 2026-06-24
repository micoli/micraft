package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class HelpCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a458fe30-07ab-42dc-b47c-9e7ed09253bd")
    override val command = "/help"
    override val description = "Lists available commands."
    override val usage = "/help [command]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val all = context.commands().sortedBy { it.command }
        val target = args.trim().let { if (it.isNotBlank()) it else null }

        if (target != null) {
            val cmd = all.find { it.command == target || it.command == "/$target" }
            if (cmd == null) {
                session.send(
                    ServerMessage.Notification(i18n.t(lang, "help:server:unknown_cmd", target)))
                return
            }
            val sb = StringBuilder()
            val desc = cmd.description.ifBlank { i18n.t(lang, "help:server:no_description") }
            sb.appendLine("${cmd.command} — $desc")
            sb.appendLine(i18n.t(lang, "help:server:usage_line", cmd.usage))
            if (cmd.options.isNotEmpty()) {
                sb.appendLine(i18n.t(lang, "help:server:options_header"))
                cmd.options.forEach { sb.appendLine(i18n.t(lang, "help:server:option_item", it)) }
            }
            session.send(ServerMessage.Notification(sb.toString().trimEnd()))
            return
        }

        val sb = StringBuilder(i18n.t(lang, "help:server:available_header") + "\n")
        all.forEach { cmd ->
            sb.append("  ${cmd.usage.padEnd(24)}")
            if (cmd.description.isNotBlank()) sb.append(" — ${cmd.description}")
            sb.appendLine()
        }
        sb.append(i18n.t(lang, "help:server:details_hint"))
        session.send(ServerMessage.Notification(sb.toString().trimEnd()))
    }
}
