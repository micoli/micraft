package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.isSolid

class SpawnCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-012345678901")
    override val command = "/spawn"
    override val description =
        "Spawn an NPC of the given model on the solid block you are looking at. (admin)"
    override val usage = "/spawn <npc_model> [x y z]"
    override val options = emptyList<String>()
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        if (argIndex == 0)
            context.npcManager
                ?.getDefinitions()
                ?.keys
                ?.filter { it.startsWith(partial, ignoreCase = true) }
                ?.map { it.lowercase() } ?: emptyList()
        else emptyList()

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val npcManager = context.npcManager
        if (npcManager == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "spawn:server:unavailable")))
            return
        }

        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size != 4) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "spawn:server:no_solid_target")))
            return
        }

        val modelArg = parts[0]
        val type =
            npcManager.getDefinitions().keys.firstOrNull { it.equals(modelArg, ignoreCase = true) }
        if (type == null) {
            val available = npcManager.getDefinitions().keys.joinToString(", ") { it.lowercase() }
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "spawn:server:unknown_model", modelArg, available)))
            return
        }

        val x = parts[1].toIntOrNull()
        val y = parts[2].toIntOrNull()
        val z = parts[3].toIntOrNull()
        if (x == null || y == null || z == null) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "spawn:server:usage")))
            return
        }

        if (y !in WorldConstants.WORLD_MIN_Y..WorldConstants.WORLD_MAX_Y) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "spawn:server:out_of_bounds")))
            return
        }

        val blockBelow = context.world.getBlock(x, y - 1, z)
        if (!blockBelow.isSolid) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "spawn:server:no_solid_below")))
            return
        }

        val existing = context.world.getBlock(x, y, z)
        if (existing.isSolid) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "spawn:server:not_air")))
            return
        }

        val name =
            "${type.lowercase().replaceFirstChar { it.uppercase() }} #${UUID.randomUUID().toString().take(4)}"
        val pos = Vec3(x + 0.5f, y.toFloat(), z + 0.5f)
        npcManager.spawnNpc(name, type, pos)

        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "spawn:server:spawned", type.lowercase(), x, y, z)))
    }
}
