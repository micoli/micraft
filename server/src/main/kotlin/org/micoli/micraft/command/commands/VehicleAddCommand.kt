package org.micoli.micraft.command.commands

import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.game.world.rail.RailConnection
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.vehicle.VehicleRegistry

/**
 * `/vehicule:add <vehiculeName> [x y z]` — spawns a vehicle on the rail block the player is looking
 * at. There is no server-side raycast anywhere in this codebase — like block place/break, the
 * client resolves the look target and appends it to the command (see `enrichCommand` in
 * LocalPlayerController). The trailing coordinates are omitted when typed manually or dispatched
 * without a hover target, in which case the block under the player's feet is used instead.
 */
class VehicleAddCommand : CommandHandler {
    override val id: UUID = UUID.fromString("6b2b6a34-6d8b-4b0b-9a6a-6a2f9a2b6f3a")
    override val name = "vehicule:add"
    override val permission = "admin"
    override val description = "Spawn a vehicle on the rail block you're standing on."
    override val usage = "$command <vehiculeName>"
    override val options
        get() = VehicleRegistry.keys().map { it.id.lowercase() }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val typeName = parts.getOrNull(0) ?: ""

        if (typeName.isBlank()) {
            val available = VehicleRegistry.keys().joinToString(", ") { it.id.lowercase() }
            session.send(
                ServerMessage.Notification(i18n.t(lang, "vehicule_add:server:usage", available)))
            return
        }

        val type = VehicleRegistry.keys().firstOrNull { it.id.equals(typeName, ignoreCase = true) }
        if (type == null) {
            val available = VehicleRegistry.keys().joinToString(", ") { it.id.lowercase() }
            session.send(
                ServerMessage.Notification(
                    i18n.t(lang, "vehicule_add:server:unknown", typeName, available)))
            return
        }

        val targeted =
            if (parts.size == 4) {
                val x = parts[1].toIntOrNull()
                val y = parts[2].toIntOrNull()
                val z = parts[3].toIntOrNull()
                if (x != null && y != null && z != null) BlockPos(x, y, z) else null
            } else null
        val pos =
            targeted
                ?: BlockPos(
                    session.state.pos.x.toInt(),
                    (session.state.pos.y - 0.1f).toInt(),
                    session.state.pos.z.toInt())

        val vehicleManager = context.vehicleManager
        if (vehicleManager == null) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "vehicule_add:server:unavailable")))
            return
        }

        val direction = initialDirectionFrom(session.state.orientation.yaw, pos, context.world)
        val spawned = vehicleManager.spawnVehicle(type, pos, context.world, direction)
        if (spawned == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "vehicule_add:server:not_a_rail")))
            return
        }
        session.send(
            ServerMessage.Notification(
                i18n.t(lang, "vehicule_add:server:done", type.id.lowercase())))
    }

    companion object {
        /**
         * Whichever of the rail's declared connection directions best matches the player's facing
         * (yaw in degrees, best-effort convention: 0 = -Z/north, increasing clockwise — see
         * [org.micoli.micraft.game.world.rail.Direction]). Purely a starting-facing nicety: a
         * vehicle reaching a dead end always reverses (see analysis §2.2), so getting this "wrong"
         * only affects which way it goes first, never whether it works.
         */
        fun initialDirectionFrom(yawDegrees: Float, pos: BlockPos, world: WorldState): Direction {
            val blockType = world.getBlock(pos.x, pos.y, pos.z)
            val state = world.getBlockState(pos.x, pos.y, pos.z)
            val connections = RailConnection.all(blockType, state).toList()
            val yawRad = Math.toRadians(yawDegrees.toDouble())
            val facingX = -sin(yawRad)
            val facingZ = -cos(yawRad)
            return connections.maxByOrNull { it.dx * facingX + it.dz * facingZ } ?: Direction.NORTH
        }
    }
}
