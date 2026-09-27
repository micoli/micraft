package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.equipment.EquipResult
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage.Notification

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
            (session?.state?.ownedArmors ?: emptyList()).filter {
                it.contains(partial, ignoreCase = true)
            }
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

        val message =
            when (val result = context.loadout.equip(session, name)) {
                EquipResult.Equipped -> i18n.t(lang, "equip:server:equipped", name)
                EquipResult.Unknown -> {
                    val available = context.equipmentCatalog.armors.keys.sorted().joinToString(", ")
                    i18n.t(lang, "equip:server:unknown", name, available)
                }
                EquipResult.NotOwned -> i18n.t(lang, "equip:server:not_owned", name)
                EquipResult.AlreadyWorn -> i18n.t(lang, "equip:server:already", name)
                is EquipResult.LevelTooLow ->
                    i18n.t(lang, "equip:server:level_too_low", name, result.required, result.actual)
                is EquipResult.WrongArmorType ->
                    i18n.t(lang, "equip:server:wrong_armor_type", result.armorType)
                is EquipResult.Overlap ->
                    i18n.t(lang, "equip:server:overlap", name, result.conflict)
            }
        session.send(Notification(message))
    }
}
