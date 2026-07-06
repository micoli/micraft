package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessage.Notification
import org.micoli.micraft.protocol.ServerMessage.PlayerUpdate
import org.micoli.micraft.rpg.character.DerivedStatsCalculator
import org.micoli.micraft.session.PlayerSession

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
            context.armorRegistry().keys.filter { it.startsWith(partial, ignoreCase = true) }
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
