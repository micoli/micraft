package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class BuffCommand : CommandHandler {
    override val id: UUID = UUID.fromString("3a7c1f82-9e4b-4d2a-b6f5-8c0e2d1a4b7f")
    override val name = "buff"
    override val description = "Apply a temporary buff to yourself."
    override val usage = "$command <hp|mana|hpregen|manaregen>"
    override val options = listOf("hp", "mana", "hpregen", "manaregen")

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val applyBuff = context.applyBuff
        if (applyBuff == null) {
            session.send(ServerMessage.Notification("Buff system unavailable."))
            return
        }

        val (effect, label) =
            when (args.trim().lowercase()) {
                "hp" -> StatusEffect.HpBoost to "HP Boost (+20 max HP)"
                "mana" -> StatusEffect.ManaBoost to "Mana Boost (+20 max mana)"
                "hpregen" -> StatusEffect.HpRegenBoost to "HP Regen Boost (+10%)"
                "manaregen" -> StatusEffect.ManaRegenBoost to "Mana Regen Boost (+10%)"
                else -> {
                    session.send(
                        ServerMessage.Notification(
                            "Usage: ${usage}. Types: hp, mana, hpregen, manaregen"))
                    return
                }
            }

        applyBuff(session, effect, effect.durationSec)
        session.send(
            ServerMessage.Notification("$label applied for ${effect.durationSec.toInt()}s."))
    }
}
