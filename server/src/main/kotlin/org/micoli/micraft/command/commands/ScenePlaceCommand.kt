package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.scene.ScenePlacer
import org.micoli.micraft.protocol.ServerMessage

/**
 * `/scene:place <sceneId> <rotation:0-3> <x> <y> <z>` — stamps a
 * [org.micoli.micraft.game.world.scene.Scene] into the live world. There is no server-side raycast
 * anywhere in this codebase — like block place/break, the client resolves the target cell
 * (creative-mode ghost preview) and appends it to the command before sending. Only reachable from
 * creative mode, which already requires the `admin` permission (see `ModeCommand`).
 */
class ScenePlaceCommand : CommandHandler {
    override val id: UUID = UUID.fromString("6b2b6a34-6d8b-4b0b-9a6a-6a2f9a2b6f4b")
    override val name = "scene:place"
    override val permission = "admin"
    override val description = "Stamp a scene into the live world at the given position."
    override val usage = "$command <sceneId> <rotation:0-3> <x> <y> <z>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val scenes = context.scenes
        if (scenes == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "scene_place:server:unavailable")))
            return
        }

        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size != 5) {
            session.send(ServerMessage.Notification(i18n.t(lang, "scene_place:server:usage")))
            return
        }

        val sceneId = parts[0]
        val rotation = parts[1].toIntOrNull()
        val x = parts[2].toIntOrNull()
        val y = parts[3].toIntOrNull()
        val z = parts[4].toIntOrNull()
        if (rotation == null || x == null || y == null || z == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "scene_place:server:usage")))
            return
        }

        val scene = scenes.get(sceneId)
        if (scene == null) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "scene_place:server:unknown", sceneId)))
            return
        }

        ScenePlacer.stamp(scene, BlockPos(x, y, z), rotation, context.world)
        context.sessions().forEach { context.refetchChunks?.invoke(it) }
        session.send(
            ServerMessage.Notification(i18n.t(lang, "scene_place:server:done", scene.name)))
    }
}
