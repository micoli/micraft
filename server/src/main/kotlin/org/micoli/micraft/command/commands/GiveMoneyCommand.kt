package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.npc.CurrencyUtils
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class GiveMoneyCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d2e3f4a5-b6c7-8901-bcde-f12345678901")
    override val name = "give:money"
    override val permission = "admin"
    override val description = "Give copper to a player (or yourself if name omitted)."
    override val usage = "$command <amount> [playerName]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val parts = args.trim().split(Regex("\\s+"))
        val amount = parts.getOrNull(0)?.toLongOrNull()
        val targetName = parts.getOrNull(1)

        if (amount == null || amount <= 0) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "give_money:server:usage")))
            return
        }

        val target =
            if (targetName == null) {
                session
            } else {
                context.sessions().firstOrNull {
                    it.state.name.equals(targetName, ignoreCase = true)
                }
                    ?: run {
                        session.send(
                            ServerMessage.Notification(
                                context.i18n.t(
                                    lang, "give_money:server:player_not_found", targetName)))
                        return
                    }
            }

        val newWallet = CurrencyUtils.addCopper(target.state.wallet, amount)
        target.state = target.state.copy(wallet = newWallet)
        context.savePlayer(target)
        target.send(ServerMessage.WalletUpdate(newWallet))
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "give_money:server:done", amount, target.state.name)))
    }
}
