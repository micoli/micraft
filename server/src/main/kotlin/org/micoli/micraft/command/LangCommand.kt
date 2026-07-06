package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(LangCommand::class.java)

class LangCommand : CommandHandler {
    override val id: UUID = UUID.fromString("062ef1bc-100c-4a80-aab0-a160bda112b1")
    override val name = "lang"
    override val description = "Changes your language preference."
    override val usage = "$command [locale]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val locale = args.trim().lowercase()
        val i18n = context.i18n

        if (locale.isBlank()) {
            val available = i18n.locales.sorted().joinToString(", ")
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "lang:server:available", available)))
            return
        }

        if (locale !in i18n.locales) {
            val available = i18n.locales.sorted().joinToString(", ")
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "lang:server:unknown", locale, available)))
            return
        }

        if (locale == session.state.language) {
            session.send(ServerMessage.Notification(i18n.t(locale, "lang:server:already", locale)))
            return
        }

        session.state = session.state.copy(language = locale)
        context.savePlayer(session)
        session.send(ServerMessage.Notification(i18n.t(locale, "lang:server:set", locale)))
        log.info("{} changed language to {}", session.state.name, locale)
    }
}
