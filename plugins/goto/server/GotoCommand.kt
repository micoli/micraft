package org.micoli.micraft.plugins.goto

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.command.playerCompletions
import org.micoli.micraft.command.resolvePlayerSession
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.plugins.teleport.safeTeleportPos
import org.micoli.micraft.protocol.ServerMessage

class GotoCommand : PluginCommand {
    override val id: UUID = UUID.fromString("b7d4d94a-3403-4565-864e-ec2eb7f87941")
    override val name = "goto"
    override val command = "/goto"
    override val description = "Teleports you to a player or NPC."
    override val usage = "/goto <playerName|npcName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<Completion> {
        val liveNpcs = context.npcManager?.getAll()?.filterNot { it.isDead } ?: emptyList()
        val questGivers =
            liveNpcs
                .filter { it.definition.behaviorKey == "quest_giver" }
                .filter { it.state.name.contains(partial, ignoreCase = true) }
                .map { Completion("${it.state.name} (quest giver)", it.state.name) }
        val npcs =
            liveNpcs.filterNot { it.definition.behaviorKey == "quest_giver" }.map { it.state.name }
        val named = context.namedPoints().keys.toList()
        val rest =
            (npcs + named).filter { it.contains(partial, ignoreCase = true) }.map { Completion(it) }
        return context.playerCompletions(partial) + questGivers + rest
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
            context.resolvePlayerSession(target)?.state?.pos
                ?: context.npcManager?.findByNameOrId(target)?.state?.pos
                ?: context.namedPoints()[target]
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
