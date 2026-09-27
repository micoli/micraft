package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.equipment.WieldResult
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Hand
import org.micoli.micraft.protocol.ServerMessage.Notification

private fun parseHand(raw: String): Hand? =
    when (raw.lowercase()) {
        "left" -> Hand.LEFT
        "right" -> Hand.RIGHT
        else -> null
    }

class WieldCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d5a0b2c3-7e8f-4a9b-8c1d-3e4f5a6b7c8d")
    override val name = "wield"
    override val description = "Wield a weapon or tool in a hand."
    override val usage = "$command <name> [hand]"
    override val autocompleteArgs = listOf(0, 1)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        when (argIndex) {
            0 ->
                ((session?.state?.ownedWeapons ?: emptyList()) +
                        (session?.state?.ownedTools ?: emptyList()))
                    .filter { it.contains(partial, ignoreCase = true) }
            1 -> listOf("left", "right").filter { it.contains(partial, ignoreCase = true) }
            else -> emptyList()
        }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val parts = args.trim().split(Regex("\\s+"), limit = 2).filter { it.isNotBlank() }
        val name = parts.getOrNull(0) ?: ""

        if (name.isBlank()) {
            session.send(Notification(i18n.t(lang, "wield:server:usage")))
            return
        }

        val explicitHand = parts.getOrNull(1)?.let(::parseHand)
        if (parts.size > 1 && explicitHand == null) {
            session.send(Notification(i18n.t(lang, "wield:server:usage")))
            return
        }

        val message =
            when (context.loadout.wield(session, name, explicitHand)) {
                is WieldResult.Wielded -> i18n.t(lang, "wield:server:wielded", name)
                WieldResult.Unknown -> {
                    val catalog = context.equipmentCatalog
                    val available =
                        (catalog.weapons.keys + catalog.tools.keys).sorted().joinToString(", ")
                    i18n.t(lang, "wield:server:unknown", name, available)
                }
                WieldResult.NotOwned -> i18n.t(lang, "wield:server:not_owned", name)
                WieldResult.WrongClass -> i18n.t(lang, "wield:server:wrong_class", name)
                WieldResult.HandsFull -> i18n.t(lang, "wield:server:hands_full")
                WieldResult.WrongHand -> i18n.t(lang, "wield:server:wrong_hand", name)
            }
        session.send(Notification(message))
    }
}
