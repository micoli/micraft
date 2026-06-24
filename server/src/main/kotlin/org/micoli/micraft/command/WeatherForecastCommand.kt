package org.micoli.micraft.command

import java.util.UUID
import kotlin.math.roundToInt
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class WeatherForecastCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c4f8a3e2-5b9d-4f0c-c6a7-8d2e3f4a5b6c")
    override val command = "/weather-forecast"
    override val description = "Shows active weather zones and their location."
    override val usage = "/weather-forecast"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val manager = context.weatherManager
        if (manager == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "weather:server:unavailable")))
            return
        }
        val zones = manager.getZones()
        if (zones.isEmpty()) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "weather:server:no_zones")))
            return
        }

        session.send(
            ServerMessage.Notification(context.i18n.t(lang, "weather:server:forecast_header")))
        zones.forEach { z ->
            val dist = manager.distanceTo(z, session.state.pos.x, session.state.pos.z).roundToInt()
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(
                        lang,
                        "weather:server:forecast_zone",
                        z.type,
                        z.cx.roundToInt(),
                        z.cz.roundToInt(),
                        z.radius.roundToInt(),
                        dist,
                    )))
        }
    }
}
