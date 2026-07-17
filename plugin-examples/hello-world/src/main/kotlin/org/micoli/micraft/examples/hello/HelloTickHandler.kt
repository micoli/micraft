package org.micoli.micraft.examples.hello

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.micoli.micraft.plugin.TickContext
import org.micoli.micraft.plugin.TickHandler
import org.micoli.micraft.protocol.ServerMessage
import java.util.logging.Logger

private const val TICKS_PER_GAME_HOUR = 3_000L // TICKS_PER_DAY(72_000) / 24
private val log = Logger.getLogger(HelloTickHandler::class.java.name)

class HelloTickHandler : TickHandler {
    override val id: UUID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000002")
    override val name = "hello-ticker"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    override suspend fun tick(context: TickContext) {
        if (context.gameTicks % TICKS_PER_GAME_HOUR != 0L) return
        val time = LocalTime.now().format(timeFormatter)
        val notification = ServerMessage.Notification("tick from hello $time")
        log.info(notification.message)
        context.sessionRegistry.all().forEach { it.send(notification) }
    }
}
