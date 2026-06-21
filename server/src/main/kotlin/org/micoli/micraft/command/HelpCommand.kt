package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class HelpCommand : CommandHandler {
    override val command = "/help"
    override val description = "Affiche la liste des commandes disponibles."
    override val usage = "/help [commande]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val all = context.commands().sortedBy { it.command }
        val target = args.trim().let { if (it.isNotBlank()) it else null }

        if (target != null) {
            val cmd = all.find { it.command == target || it.command == "/$target" }
            if (cmd == null) {
                session.send(ServerMessage.Notification("Commande inconnue : $target"))
                return
            }
            val sb = StringBuilder()
            sb.appendLine("${cmd.command} — ${cmd.description.ifBlank { "(pas de description)" }}")
            sb.appendLine("  Usage : ${cmd.usage}")
            if (cmd.options.isNotEmpty()) {
                sb.appendLine("  Options :")
                cmd.options.forEach { sb.appendLine("    • $it") }
            }
            session.send(ServerMessage.Notification(sb.toString().trimEnd()))
            return
        }

        val sb = StringBuilder("Commandes disponibles :\n")
        all.forEach { cmd ->
            sb.append("  ${cmd.usage.padEnd(24)}")
            if (cmd.description.isNotBlank()) sb.append(" — ${cmd.description}")
            sb.appendLine()
        }
        sb.append("Tape /help <commande> pour plus de détails.")
        session.send(ServerMessage.Notification(sb.toString().trimEnd()))
    }
}
