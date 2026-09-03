package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.actionblock.toInfo
import org.micoli.micraft.protocol.ServerMessage

/**
 * `/actionblock:activate [x y z]` — turns the block the player is looking at (client appends the
 * hovered cell, see `enrichCommand`) into an
 * [org.micoli.micraft.game.world.actionblock.ActionBlock] with an auto-generated name. Falls back
 * to the block under the player's feet.
 */
class ActionBlockActivateCommand : CommandHandler {
    override val id: UUID = UUID.fromString("2b1e6b7a-4c3d-4e5f-9a1b-1c2d3e4f5a6b")
    override val name = "actionblock:activate"
    override val description = "Turn the targeted block into a named action block."
    override val usage = "$command [x y z]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val registry = context.actionBlockRegistry
        if (registry == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:unavailable")))
            return
        }
        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val pos =
            if (parts.size == 3) {
                val x = parts[0].toIntOrNull()
                val y = parts[1].toIntOrNull()
                val z = parts[2].toIntOrNull()
                if (x == null || y == null || z == null) null else BlockPos(x, y, z)
            } else {
                BlockPos(
                    session.state.pos.x.toInt(),
                    (session.state.pos.y - 0.1f).toInt(),
                    session.state.pos.z.toInt())
            }
        if (pos == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:usage")))
            return
        }
        val block =
            registry.create(pos, session.state.name) { p -> context.world.getBlock(p.x, p.y, p.z) }
        if (block == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:not_a_block")))
            return
        }
        context.broadcast(ServerMessage.ActionBlockUpsert(block.toInfo()))
        session.send(
            ServerMessage.Notification(i18n.t(lang, "actionblock:server:created", block.name)))
    }
}
