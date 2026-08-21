package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Hand
import org.micoli.micraft.protocol.ServerMessage.Notification
import org.micoli.micraft.protocol.ServerMessage.PlayerUpdate

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
                (context.weaponRegistry().keys + context.toolRegistry().keys).filter {
                    it.contains(partial, ignoreCase = true)
                }
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

        val weaponDef = context.weaponRegistry()[name]
        val toolDef = context.toolRegistry()[name]
        if (weaponDef == null && toolDef == null) {
            val available =
                (context.weaponRegistry().keys + context.toolRegistry().keys)
                    .sorted()
                    .joinToString(", ")
            session.send(Notification(i18n.t(lang, "wield:server:unknown", name, available)))
            return
        }

        val mainHandOnly =
            if (weaponDef != null)
                context.weaponCategories()[weaponDef.category]?.mainHandOnly == true
            else context.toolCategories()[toolDef!!.category]?.mainHandOnly == true

        if (weaponDef != null) {
            val allowedClasses = context.weaponCategories()[weaponDef.category]?.allowedClasses
            val playerClass = session.characterData?.characterClass
            if (allowedClasses != null && (playerClass == null || playerClass !in allowedClasses)) {
                session.send(Notification(i18n.t(lang, "wield:server:wrong_class", name)))
                return
            }
        }

        val explicitHand = parts.getOrNull(1)?.let(::parseHand)
        if (parts.size > 1 && explicitHand == null) {
            session.send(Notification(i18n.t(lang, "wield:server:usage")))
            return
        }

        val dominant = session.state.dominantHand
        val offHand = if (dominant == Hand.RIGHT) Hand.LEFT else Hand.RIGHT

        val targetHand =
            explicitHand
                ?: if (mainHandOnly) dominant
                else if (session.handItem(offHand) == null) offHand
                else if (session.handItem(dominant) == null) dominant else null

        if (targetHand == null) {
            session.send(Notification(i18n.t(lang, "wield:server:hands_full")))
            return
        }

        if (mainHandOnly && targetHand != dominant) {
            session.send(Notification(i18n.t(lang, "wield:server:wrong_hand", name)))
            return
        }

        session.state =
            when (targetHand) {
                Hand.RIGHT -> session.state.copy(rightHandItem = name)
                Hand.LEFT -> session.state.copy(leftHandItem = name)
            }
        context.broadcast(PlayerUpdate(session.state))
        context.savePlayer(session)
        session.send(Notification(i18n.t(lang, "wield:server:wielded", name)))
    }

    private fun PlayerSession.handItem(hand: Hand): String? =
        when (hand) {
            Hand.RIGHT -> state.rightHandItem
            Hand.LEFT -> state.leftHandItem
        }
}
