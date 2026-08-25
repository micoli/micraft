package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

/**
 * `/siege_weapon <rotation|pitch|power> <value>` — precise absolute adjustment of the siege weapon
 * currently targeted (Tab), in addition to the R/Shift+R/Ctrl+R cycle. Target resolution: the
 * player's `session.combatState.targetId` is a placeable id (Tab-cycle target pool is placeable
 * ids, see PlaceableManager); this command resolves the siege instance linked to it.
 */
class SiegeWeaponCommand : CommandHandler {
    override val id: UUID = UUID.fromString("7c3e9a51-2b4d-4e6a-8f1c-9d2b6a4e7f10")
    override val name = "siege_weapon"
    override val description = "Set the targeted siege weapon's rotation, pitch, or power."
    override val usage = "$command <rotation|pitch|power> <value>"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        when (argIndex) {
            0 ->
                listOf("rotation", "pitch", "power").filter {
                    it.contains(partial, ignoreCase = true)
                }
            else -> emptyList()
        }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val subcommand = parts.getOrNull(0)?.lowercase()
        val valueStr = parts.getOrNull(1)

        if (subcommand == null || valueStr == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "siege_weapon:server:usage")))
            return
        }

        val value = valueStr.toIntOrNull()
        if (value == null) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(lang, "siege_weapon:server:invalid_value", valueStr)))
            return
        }

        val placeableManager = context.placeableManager
        val siegeWeaponManager = context.siegeWeaponManager
        if (placeableManager == null || siegeWeaponManager == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "siege_weapon:server:no_target")))
            return
        }

        val targetId = session.combatState.targetId
        val weapon = targetId?.let { siegeWeaponManager.getByPlaceableId(it) }
        if (weapon == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "siege_weapon:server:no_target")))
            return
        }

        when (subcommand) {
            "rotation" -> {
                placeableManager.setRotationStep(weapon.placeableId, value)
                session.send(
                    ServerMessage.Notification(
                        i18n.t(lang, "siege_weapon:server:done", "rotation", value)))
            }
            "pitch" -> {
                siegeWeaponManager.handleSetPitch(weapon.id, value)
                session.send(
                    ServerMessage.Notification(
                        i18n.t(lang, "siege_weapon:server:done", "pitch", value)))
            }
            "power" -> {
                siegeWeaponManager.handleSetPower(weapon.id, value)
                session.send(
                    ServerMessage.Notification(
                        i18n.t(lang, "siege_weapon:server:done", "power", value)))
            }
            else ->
                session.send(ServerMessage.Notification(i18n.t(lang, "siege_weapon:server:usage")))
        }
    }
}
