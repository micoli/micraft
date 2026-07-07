package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.player.rpg.ClassResource
import org.micoli.micraft.player.rpg.DerivedStats
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.rpg.character.DerivedStatsCalculator
import org.micoli.micraft.session.PlayerSession

private const val MAX_RAGE_DEFAULT = 100

class SetCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c4e7a2d1-83f5-4b9e-a0c6-d1e2f3a4b5c6")
    override val name = "set"
    override val permission = "admin"
    override val description = "Set a player stat."
    override val usage = "$command <hp|mana> <playerName> <value>"
    override val autocompleteArgs = listOf(0, 1)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        when (argIndex) {
            0 -> listOf("hp", "mana").filter { it.startsWith(partial, ignoreCase = true) }
            1 ->
                context
                    .sessions()
                    .map { it.state.name }
                    .filter { it.startsWith(partial, ignoreCase = true) }
            else -> emptyList()
        }

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
            context.sessions().find { it.state.name.equals(playerName, ignoreCase = true) }
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

        val armors = target.state.armors.mapNotNull { context.armorRegistry()[it]?.statBonus }
        val derived = DerivedStatsCalculator.compute(charData, armors)
        val isRage = charData.characterClass.classResource == ClassResource.RAGE

        when (subcommand.lowercase()) {
            "hp" -> {
                val newHp = value.coerceIn(0, derived.maxHp)
                target.characterData = charData.copy(currentHp = newHp)
                context.broadcast(
                    ServerMessage.HealthUpdate(target.id, false, newHp, derived.maxHp))
                target.send(statusUpdate(target, derived, isRage))
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "set:server:done_hp", target.state.name, newHp)))
            }
            "mana" -> {
                val newMana = value.coerceIn(0, derived.maxMana)
                target.characterData = charData.copy(currentMana = newMana)
                target.send(statusUpdate(target, derived, isRage))
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

    private fun statusUpdate(
        target: PlayerSession,
        derived: DerivedStats,
        isRage: Boolean,
    ): ServerMessage.PlayerStatusUpdate {
        val c = target.characterData!!
        return ServerMessage.PlayerStatusUpdate(
            currentHp = c.currentHp,
            maxHp = derived.maxHp,
            currentMana = if (isRage) 0 else c.currentMana,
            maxMana = if (isRage) 0 else derived.maxMana,
            currentRage = if (isRage) c.currentRage else 0,
            maxRage = if (isRage) MAX_RAGE_DEFAULT else 0,
            stance = target.state.stance,
            globalCooldownRemainingMs =
                (target.combatState.attackCooldownUntilMs - System.currentTimeMillis())
                    .coerceAtLeast(0),
        )
    }
}
