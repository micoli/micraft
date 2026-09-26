package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.auth.CorePermissions
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.playerCompletions
import org.micoli.micraft.command.resolvePlayerSession
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class SetCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c4e7a2d1-83f5-4b9e-a0c6-d1e2f3a4b5c6")
    override val name = "set"
    override val permission = CorePermissions.ADMIN
    override val description = "Set a player stat."
    override val usage = "$command <hp|mana> <playerName> <value>"
    override val autocompleteArgs = listOf(0, 1)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        if (argIndex == 0) listOf("hp", "mana").filter { it.contains(partial, ignoreCase = true) }
        else emptyList()

    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<Completion>? = if (argIndex == 1) context.playerCompletions(partial) else null

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val parts = args.trim().split(Regex("\\s+"))
        val subcommand = parts.getOrNull(0).orEmpty()
        val playerName = parts.getOrNull(1).orEmpty()
        val valueStr = parts.getOrNull(2).orEmpty()

        if (subcommand.isBlank() || playerName.isBlank() || valueStr.isBlank()) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "set:server:usage")))
            return
        }

        val value = valueStr.toIntOrNull()
        if (value == null || value < 0) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "set:server:invalid_value", valueStr)))
            return
        }

        val target =
            context.resolvePlayerSession(playerName)
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(lang, "set:server:not_found", playerName)))
                    return
                }

        val charData =
            target.characterData
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(lang, "set:server:no_character", target.state.name)))
                    return
                }

        val derived = context.characterStats.derived(target, charData)

        when (subcommand.lowercase()) {
            "hp" -> {
                val newHp = value.coerceIn(0, derived.maxHp)
                target.characterData = charData.copy(currentHp = newHp)
                context.broadcast(
                    ServerMessage.HealthUpdate(target.id, false, newHp, derived.maxHp))
                context.characterStats.resync(target)
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "set:server:done_hp", target.state.name, newHp)))
            }
            "mana" -> {
                val newMana = value.coerceIn(0, derived.maxMana)
                target.characterData = charData.copy(currentMana = newMana)
                context.characterStats.resync(target)
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "set:server:done_mana", target.state.name, newMana)))
            }
            else ->
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "set:server:unknown_stat", subcommand)))
        }
        context.savePlayer(target)
    }
}
