package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

/**
 * `/mount` — toggle: mounts the currently targeted vehicle (see `combat_target_cycle`/X-interact,
 * same `session.combatState.targetId`), or dismounts if already mounted. Independent of the
 * vehicle's own moving/stopped state (toggled separately via X-interact) — dismounting never stops
 * the vehicle.
 */
class MountCommand : CommandHandler {
    override val id: UUID = UUID.fromString("2f6b8b3a-1a4c-4f2a-9c3a-7e6d0a2b5f91")
    override val name = "mount"
    override val description = "Mount or dismount the vehicle you're targeting."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val vehicleManager = context.vehicleManager ?: return

        if (session.mountedVehicleId != null) {
            vehicleManager.dismount(session)
            session.mountedVehicleId = null
            session.state = session.state.copy(mounted = false)
            session.send(ServerMessage.MountUpdate(null))
            context.broadcast(ServerMessage.PlayerUpdate(session.state))
            session.send(ServerMessage.Notification(i18n.t(lang, "mount:server:dismounted")))
            return
        }

        val targetId = session.combatState.targetId
        val vehicle = targetId?.let { vehicleManager.get(it) }
        if (vehicle == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "mount:server:no_target")))
            return
        }

        if (!vehicleManager.mount(vehicle.id, session)) {
            session.send(ServerMessage.Notification(i18n.t(lang, "mount:server:occupied")))
            return
        }

        session.mountedVehicleId = vehicle.id
        session.state = session.state.copy(mounted = true)
        session.send(ServerMessage.MountUpdate(vehicle.id))
        context.broadcast(ServerMessage.PlayerUpdate(session.state))
        session.send(ServerMessage.Notification(i18n.t(lang, "mount:server:mounted")))
    }
}
