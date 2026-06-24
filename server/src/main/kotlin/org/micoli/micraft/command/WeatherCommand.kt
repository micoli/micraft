package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.WeatherType

class WeatherCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b3e7f2d1-4a8c-4e9b-b5f6-7c1d2e3a4b5c")
    override val command = "/weather"
    override val description = "Force a weather zone at your position or clear all zones. (admin)"
    override val usage = "/weather [rain|storm|snow|fog|none]"
    override val options = listOf("rain", "storm", "snow", "fog", "none")

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val manager = context.weatherManager
        if (manager == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "weather:server:unavailable")))
            return
        }
        val trimmed = args.trim().lowercase()

        if (trimmed.isEmpty()) {
            val zones = manager.getZones()
            if (zones.isEmpty()) {
                session.send(
                    ServerMessage.Notification(context.i18n.t(lang, "weather:server:no_zones")))
            } else {
                val list =
                    zones.joinToString(", ") { z ->
                        val dist =
                            manager.distanceTo(z, session.state.pos.x, session.state.pos.z).toInt()
                        "${z.type}@${dist}m"
                    }
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(lang, "weather:server:zone_list", list)))
            }
            return
        }

        if (trimmed == "none") {
            manager.clearAllZones()
            context.broadcast(ServerMessage.WeatherUpdate(emptyList()))
            session.send(ServerMessage.Notification(context.i18n.t(lang, "weather:server:cleared")))
            return
        }

        val type = runCatching { WeatherType.valueOf(trimmed.uppercase()) }.getOrNull()
        if (type == null || type == WeatherType.NONE) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "weather:server:usage")))
            return
        }

        manager.forceWeather(type, session.state.pos.x, session.state.pos.z)
        context.broadcast(ServerMessage.WeatherUpdate(manager.getZones()))
        session.send(
            ServerMessage.Notification(context.i18n.t(lang, "weather:server:forced", type.name)))
    }
}
