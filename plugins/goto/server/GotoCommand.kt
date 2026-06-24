package org.micoli.micraft.plugins.goto

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.PluginCommand
import org.micoli.micraft.plugins.teleport.safeTeleportPos
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class GotoCommand : PluginCommand {
    override val id = UUID.fromString("b7d4d94a-3403-4565-864e-ec2eb7f87941")
    override val name = "goto"
    override val command = "/goto"
    override val description = "Teleports you to a player or NPC."
    override val usage = "/goto <playerName|npcName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<String> {
        val players = context.sessions().map { it.state.name }
        val npcs = context.npcManager?.getAll()?.map { it.state.name } ?: emptyList()
        return (players + npcs).filter { it.startsWith(partial, ignoreCase = true) }
    }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val target = args.trim()
        if (target.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "goto:server:usage")))
            return
        }
        val targetPos =
            context.sessions().find { it.state.name == target }?.state?.pos
                ?: context.npcManager?.findByNameOrId(target)?.state?.pos
        if (targetPos == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "goto:server:not_found", target)))
            return
        }
        session.state = session.state.copy(pos = safeTeleportPos(context.world, targetPos))
        session.vy = 0f
        session.send(ServerMessage.PlayerUpdate(session.state))
        session.send(ServerMessage.Notification(i18n.t(lang, "goto:server:done", target)))
    }
}
