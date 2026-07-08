package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.weather.WeatherType
import org.micoli.micraft.protocol.ServerMessage

class WeatherCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b3e7f2d1-4a8c-4e9b-b5f6-7c1d2e3a4b5c")
    override val name = "weather"
    override val permission = "admin"
    override val description = "Force a weather zone at your position or clear all zones. (admin)"
    override val usage = "$command [rain|storm|snow|fog|none]"
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

        if (!manager.forceWeather(type, session.state.pos.x, session.state.pos.z, context.world)) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "weather:server:no_discovered_chunk")))
            return
        }
        context.broadcast(ServerMessage.WeatherUpdate(manager.getZones()))
        session.send(
            ServerMessage.Notification(context.i18n.t(lang, "weather:server:forced", type.name)))
    }
}
