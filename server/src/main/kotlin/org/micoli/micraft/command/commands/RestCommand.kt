package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.equipmentBonuses
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class RestCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a3c8f2e1-7b4d-4f9a-b5c6-1e2d3f4a5b6c")
    override val name = "rest"
    override val description = "Take a short rest: restore rage and tokens to maximum."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val charData =
            session.characterData
                ?: run {
                    session.send(ServerMessage.Notification("No character — use /createcharacter"))
                    return
                }

        val armors =
            session.state.equipmentBonuses(
                context.armorRegistry(), context.weaponRegistry(), context.toolRegistry())
        val derived = DerivedStatsCalculator.compute(charData, armors)

        val restored =
            charData.copy(
                currentRage = 100,
                currentTokens = derived.maxTokens,
            )
        session.characterData = restored
        context.clearAccumulators?.invoke(session.id)
        context.savePlayer(session)
        context.sendStatusUpdate?.invoke(session)
        session.send(
            ServerMessage.Notification(context.i18n.t(session.state.language, "rest:server:done")))
    }
}
