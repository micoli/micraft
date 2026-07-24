package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class ConfigCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c3d4e5f6-a7b8-4c9d-8e0f-1a2b3c4d5e6f")
    override val name = "config"
    override val description = "Get or set a runtime config value."
    override val usage = "$command <get|set> <key> [value]"
    override val autocompleteArgs = listOf(0, 1)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        when (argIndex) {
            0 -> listOf("get", "set").filter { it.contains(partial, ignoreCase = true) }
            1 ->
                context.configRegistry?.keys()?.filter { it.contains(partial, ignoreCase = true) }
                    ?: emptyList()
            else -> emptyList()
        }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val parts = args.trim().split(Regex("\\s+"), limit = 3)
        val op = parts.getOrNull(0)?.lowercase().orEmpty()
        val key = parts.getOrNull(1).orEmpty()
        val registry = context.configRegistry

        if (registry == null || op.isEmpty()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "config:server:usage")))
            return
        }

        when (op) {
            "get" -> {
                if (key.isEmpty()) {
                    session.send(ServerMessage.Notification(i18n.t(lang, "config:server:usage")))
                    return
                }
                val value = registry.get(key)
                if (value == null) {
                    session.send(
                        ServerMessage.Notification(i18n.t(lang, "config:server:unknown_key", key)))
                } else {
                    session.send(
                        ServerMessage.Notification(
                            i18n.t(lang, "config:server:get_result", key, value)))
                }
            }
            "set" -> {
                if (key.isEmpty()) {
                    session.send(ServerMessage.Notification(i18n.t(lang, "config:server:usage")))
                    return
                }
                if (!registry.has(key)) {
                    session.send(
                        ServerMessage.Notification(i18n.t(lang, "config:server:unknown_key", key)))
                    return
                }
                if (registry.isReadOnly(key)) {
                    session.send(
                        ServerMessage.Notification(i18n.t(lang, "config:server:read_only", key)))
                    return
                }
                val value = parts.getOrNull(2)
                if (value == null) {
                    session.send(
                        ServerMessage.Notification(i18n.t(lang, "config:server:set_usage")))
                    return
                }
                val ok = registry.set(key, value)
                if (ok) {
                    session.send(
                        ServerMessage.Notification(
                            i18n.t(lang, "config:server:set_ok", key, value)))
                } else {
                    session.send(
                        ServerMessage.Notification(
                            i18n.t(lang, "config:server:set_invalid", key, value)))
                }
            }
            else -> session.send(ServerMessage.Notification(i18n.t(lang, "config:server:usage")))
        }
    }
}
