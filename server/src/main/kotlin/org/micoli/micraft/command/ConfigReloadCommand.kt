package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ConfigReloadCommand::class.java)

class ConfigReloadCommand : CommandHandler {
    override val id: UUID = UUID.fromString("3b4c5d6e-7f80-4a1b-9c2d-3e4f5a6b7c8d")
    override val command = "/config:reload"
    override val description = "Reloads block or NPC definitions from resource files."
    override val options = listOf("block", "npc")

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val target = args.trim().lowercase()
        val doBlocks = target.isEmpty() || target == "block"
        val doNpcs = target.isEmpty() || target == "npc"
        if (!doBlocks && !doNpcs) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "config_reload:server:usage")))
            return
        }
        val reloaded = mutableListOf<String>()
        if (doBlocks) {
            val fn = context.reloadBlocks
            if (fn != null) {
                fn()
                reloaded += "blocks"
            } else {
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "config_reload:server:unavailable", "blocks")))
            }
        }
        if (doNpcs) {
            val fn = context.reloadNpcs
            if (fn != null) {
                fn()
                reloaded += "npcs"
            } else {
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "config_reload:server:unavailable", "npcs")))
            }
        }
        if (reloaded.isNotEmpty()) {
            log.info("Config reloaded by {}: {}", session.state.name, reloaded)
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "config_reload:server:done", reloaded.joinToString(", "))))
        }
    }
}
