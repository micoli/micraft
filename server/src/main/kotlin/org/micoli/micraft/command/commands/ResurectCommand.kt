package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.combat.CombatState
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.resolvePlayerSession
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.equipmentBonuses
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class ResurectCommand : CommandHandler {
    override val id: UUID = UUID.fromString("3e7a1f2c-9b4d-4e8a-b1c5-d2e3f4a5b6c7")
    override val name = "resurect"
    override val description = "Resurrect a downed player (self if no name given)."
    override val usage = "$command [playerName]"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<Completion> =
        if (argIndex != 0) emptyList()
        else
            context
                .sessions()
                .filter { it.isDowned && it.state.name.contains(partial, ignoreCase = true) }
                .map { Completion(it.state.name, "@" + it.state.id) }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val targetToken = args.trim().ifBlank { session.state.name }
        val target =
            context.resolvePlayerSession(targetToken)
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(lang, "resurect:server:not_found", targetToken)))
                    return
                }

        if (!target.isDowned) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "resurect:server:not_downed", target.state.name)))
            return
        }

        val charData =
            target.characterData
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(
                                lang, "resurect:server:no_character", target.state.name)))
                    return
                }

        val armors =
            target.state.equipmentBonuses(
                context.armorRegistry(), context.weaponRegistry(), context.toolRegistry())
        val derived = DerivedStatsCalculator.compute(charData, armors)
        val newHp = derived.maxHp
        val newMana = derived.maxMana
        val respawnPos = charData.restPoint.firstOrNull() ?: target.state.pos

        target.characterData = charData.copy(currentHp = newHp, currentMana = newMana)
        target.combatState = CombatState(downingSuccesses = 0, downingFailures = 0)

        context.sessions().forEach {
            it.send(ServerMessage.PlayerRespawned(target.id, respawnPos, newHp, newMana))
        }
        context.savePlayer(target)

        if (target.id != session.id) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "resurect:server:done", target.state.name)))
        }
    }
}
