package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.canonicalPlayerName
import org.micoli.micraft.command.playerCompletions
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class MiniGameCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b1d3a1f0-0002-4a00-9000-000000000001")
    override val name = "minigame"
    override val description = "Play a mini-game with other players (e.g. tic-tac-toe)."
    override val usage = "$command create <gameType>|invite <player>|accept|decline|leave|who"
    override val options = listOf("create", "invite", "accept", "decline", "leave", "who")
    override val autocompleteArgs = listOf(0, 1)

    /**
     * `completeArgRich` only receives the current arg's partial text, not the sub-command already
     * typed (see [org.micoli.micraft.game.GameLoop.autocomplete]) — so for arg 1 this offers both
     * known `gameType`s (for `create <gameType>`) and player names (for `invite <player>`), same as
     * [GuildCommand] unconditionally offering player names for its arg 1 across sub-commands.
     */
    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<Completion>? {
        if (argIndex == 0) return null
        val gameTypes =
            context.miniGameRegistry
                ?.all()
                .orEmpty()
                .map { it.gameType }
                .filter { it.contains(partial, ignoreCase = true) }
                .map { Completion(it) }
        val players = context.playerCompletions(partial, excludeSelf = session)
        return gameTypes + players
    }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val mgm = context.miniGameManager ?: return
        val parts = args.trim().split(Regex("\\s+"), limit = 2)
        val sub = parts.getOrNull(0)?.lowercase().orEmpty()
        val rest = parts.getOrNull(1)?.trim().orEmpty()
        when (sub) {
            "create" -> {
                if (rest.isBlank()) {
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(session.state.language, "minigame:server:usage"),
                            "system"))
                    return
                }
                mgm.create(session, rest)
            }
            "invite" ->
                mgm.roomOf(session.id)?.let {
                    mgm.invite(session, it.id, context.canonicalPlayerName(rest))
                }
            "accept" ->
                mgm.pendingRoomIdFor(session.id)?.let { mgm.respondInvite(session, it, true) }
            "decline" ->
                mgm.pendingRoomIdFor(session.id)?.let { mgm.respondInvite(session, it, false) }
            "leave" -> mgm.roomOf(session.id)?.let { mgm.leave(session, it.id) }
            "who" -> mgm.sendSync(session)
            else ->
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(session.state.language, "minigame:server:usage"), "system"))
        }
    }
}
