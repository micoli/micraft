package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.protocol.ServerMessage

/**
 * Self-service land-ownership commands. Creating a claim (drawing the region) is done via the
 * client's claim-selection tool ([org.micoli.micraft.protocol.ClientMessage.ClaimCreate]) — this
 * command only manages an existing claim the player is standing in: trusting/untrusting other
 * players, abandoning it, or inspecting it. Ownership of the target claim (not the global RBAC
 * `permission` field) gates trust/untrust/abandon, mirroring how `InstanceZone.ownerName` is
 * enforced at the call site rather than via a group.
 */
class ClaimCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d3f1a6b2-4c7e-4a1d-9f2b-6a8e5c0d7f31")
    override val name = "claim"
    override val permission = "player"
    override val description = "Manage the land claim you're standing in."
    override val usage = "$command <trust|untrust|abandon|info> [playerName]"
    override val autocompleteArgs = listOf(0, 1)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        when (argIndex) {
            0 ->
                listOf("trust", "untrust", "abandon", "info").filter {
                    it.contains(partial, ignoreCase = true)
                }
            1 ->
                context
                    .sessions()
                    .map { it.state.name }
                    .filter { it.contains(partial, ignoreCase = true) }
            else -> emptyList()
        }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val claimManager = context.claimManager
        val claimRegistry = context.claimRegistry
        if (claimManager == null || claimRegistry == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "claim:server:claim_not_found")))
            return
        }

        val parts = args.trim().split(Regex("\\s+"))
        val subcommand = parts.getOrNull(0).orEmpty().lowercase()
        val playerName = parts.getOrNull(1).orEmpty()

        val pos = session.state.pos
        val here = BlockPos(pos.x.toInt(), pos.y.toInt(), pos.z.toInt())
        val claim = claimRegistry.claimAt(here.x, here.y, here.z)
        if (claim == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "claim:server:claim_not_found")))
            return
        }

        when (subcommand) {
            "trust" -> claimManager.setTrusted(session, claim.id, playerName, trusted = true)
            "untrust" -> claimManager.setTrusted(session, claim.id, playerName, trusted = false)
            "abandon" -> claimManager.abandonClaim(session, claim.id)
            "info" ->
                session.send(
                    ServerMessage.Notification(
                        "${claim.ownerName} — trusted: ${claim.trustedPlayerNames.joinToString(", ")}"))
            else ->
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "claim:server:claim_not_found")))
        }
    }
}
