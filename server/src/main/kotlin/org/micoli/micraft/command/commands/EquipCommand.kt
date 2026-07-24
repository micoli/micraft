package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessage.Notification
import org.micoli.micraft.protocol.ServerMessage.PlayerUpdate

class EquipCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b3e8f1a2-5c6d-4f7e-9b0c-1d2e3f4a5b6c")
    override val name = "equip"
    override val description = "Equip an armor piece."
    override val usage = "$command <armorName>"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        if (argIndex == 0)
            context.armorRegistry().keys.filter { it.contains(partial, ignoreCase = true) }
        else emptyList()

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val name = args.trim()

        if (name.isBlank()) {
            val available = context.armorRegistry().keys.sorted().joinToString(", ")
            session.send(Notification(i18n.t(lang, "equip:server:usage")))
            return
        }

        val armorDef = context.armorRegistry()[name]
        if (armorDef == null) {
            val available = context.armorRegistry().keys.sorted().joinToString(", ")
            session.send(Notification(i18n.t(lang, "equip:server:unknown", name, available)))
            return
        }

        if (name in session.state.armors) {
            session.send(Notification(i18n.t(lang, "equip:server:already", name)))
            return
        }

        val conflict =
            session.state.armors.firstOrNull { worn ->
                context.armorRegistry()[worn]?.wearable?.overlaps(armorDef.wearable) == true
            }
        if (conflict != null) {
            session.send(Notification(i18n.t(lang, "equip:server:overlap", name, conflict)))
            return
        }

        session.state = session.state.copy(armors = session.state.armors + name)
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
        session.send(Notification(i18n.t(lang, "equip:server:equipped", name)))
    }
}
