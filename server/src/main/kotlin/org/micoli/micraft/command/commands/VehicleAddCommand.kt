package org.micoli.micraft.command.commands

import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.rail.RailConnection
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.vehicle.VehicleRegistry

/**
 * `/vehicule:add <vehiculeName>` — spawns a vehicle on the rail block the player is standing on
 * (analysis §2.1 called for a raycast-resolved look target like block placement, but there is no
 * server-side raycast anywhere in this codebase — block place/break trust the client-computed
 * position instead. Reusing "the block under the player's feet" needs no new client wiring and no
 * unverifiable trigonometry).
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
        val typeName = args.trim()

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

        val pos =
            BlockPos(
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
         * +1/-1 — whichever of the rail's two directions best matches the player's facing (yaw in
         * degrees, best-effort convention: 0 = -Z/north, increasing clockwise). Direction.entries
         * order gives the first (index 0) declared connection direction as index 0 of the rail's
         * connection set — that direction maps to +1, its opposite to -1. Purely a starting-facing
         * nicety: a vehicle reaching a dead end always reverses (see analysis §2.2), so getting
         * this "wrong" only affects which way it goes first, never whether it works.
         */
        fun initialDirectionFrom(
            yawDegrees: Float,
            pos: BlockPos,
            world: org.micoli.micraft.game.world.WorldState,
        ): Int {
            val blockType = world.getBlock(pos.x, pos.y, pos.z)
            val state = world.getBlockState(pos.x, pos.y, pos.z)
            val connections = RailConnection.all(blockType, state).toList()
            if (connections.size < 2) return 1
            val yawRad = Math.toRadians(yawDegrees.toDouble())
            val facingX = -sin(yawRad)
            val facingZ = -cos(yawRad)
            val forward = connections[0]
            val dot = forward.dx * facingX + forward.dz * facingZ
            return if (dot >= 0) 1 else -1
        }
    }
}
