package org.micoli.micraft.command.commands

import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessage.PlayerUpdate
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SkinCommand::class.java)

private val skinsRoot = Path.of("resources/skins")

fun availablePlayerSkins(): List<String> =
    runCatching {
            skinsRoot
                .listDirectoryEntries()
                .filter { dir ->
                    dir.isDirectory() && dir.resolve("${dir.name}.bbmodel").toFile().exists()
                }
                .map { it.name }
                .sorted()
        }
        .getOrDefault(listOf("player"))

class SkinCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a7f3e2d1-4b5c-4e6f-8a9b-0c1d2e3f4a5b")
    override val name = "skin"
    override val description = "Changes your player skin."
    override val usage = "$command <skinName>"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        if (argIndex == 0)
            availablePlayerSkins().filter { it.contains(partial, ignoreCase = true) }
        else emptyList()

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val lang = session.state.language
        val skin = args.trim().lowercase()

        if (skin.isBlank()) {
            val available = availablePlayerSkins().joinToString(", ")
            session.send(ServerMessage.Notification(i18n.t(lang, "skin:server:usage", available)))
            return
        }

        val available = availablePlayerSkins()
        if (skin !in available) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(lang, "skin:server:unknown", skin, available.joinToString(", "))))
            return
        }

        if (skin == session.state.skin) {
            session.send(ServerMessage.Notification(i18n.t(lang, "skin:server:already", skin)))
            return
        }

        session.state = session.state.copy(skin = skin)
        context.savePlayer(session)
        context.broadcast(PlayerUpdate(session.state))
        session.send(ServerMessage.Notification(i18n.t(lang, "skin:server:set", skin)))
        log.info("{} changed skin to {}", session.state.name, skin)
    }
}
