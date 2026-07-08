package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessage.Notification
import org.micoli.micraft.protocol.ServerMessage.PlayerUpdate

class UnequipCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c4f9a2b3-6d7e-4a8f-9c1d-2e3f4a5b6c7d")
    override val name = "unequip"
    override val description = "Remove an equipped armor piece."
    override val usage = "$command <armorName>"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        if (argIndex == 0)
            (session?.state?.armors ?: emptyList()).filter {
                it.startsWith(partial, ignoreCase = true)
            }
        else emptyList()

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val name = args.trim()

        if (name.isBlank()) {
            session.send(Notification(i18n.t(lang, "unequip:server:usage")))
            return
        }

        if (name !in session.state.armors) {
            session.send(Notification(i18n.t(lang, "unequip:server:not_wearing", name)))
            return
        }

        session.state = session.state.copy(armors = session.state.armors - name)
        context.broadcast(PlayerUpdate(session.state))
        context.savePlayer(session)
        session.characterData?.let { char ->
            val bonuses = session.state.armors.mapNotNull { context.armorRegistry()[it]?.statBonus }
            session.send(
                ServerMessage.CharacterSync(
                    char,
                    DerivedStatsCalculator.compute(char, bonuses),
                    DerivedStatsCalculator.effectiveBaseStats(char, bonuses)))
        }
        session.send(Notification(i18n.t(lang, "unequip:server:unequipped", name)))
    }
}
