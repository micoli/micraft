package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.placeable.panel.PanelEditing
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

/**
 * `/panel <edit|set <url>|clear>` — edits the interactive panel currently targeted (Tab). `edit`
 * opens the in-game editor; `set` points it at an allowlisted https URL; `clear` empties it.
 */
class PanelCommand : CommandHandler {
    override val id: UUID = UUID.fromString("3b8f5a0e-6d21-4c47-9a1e-5e7c2d94b6f3")
    override val name = "panel"
    override val description = "Edit the targeted interactive panel."
    override val usage = "$command <edit|set <url>|clear>"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        when (argIndex) {
            0 -> listOf("edit", "set", "clear").filter { it.contains(partial, ignoreCase = true) }
            else -> emptyList()
        }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val placeables = context.placeableManager
        val targetId = session.combatState.targetId
        if (placeables == null || targetId == null || !placeables.panels.isPanel(targetId)) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "panel:server:no_target")))
            return
        }
        val editing = PanelEditing(placeables, context.claimRegistry, context.i18n)
        val parts = args.trim().split(Regex("\\s+"), limit = 2)
        when (parts[0].lowercase()) {
            "edit" -> editing.requestEditor(session, targetId)
            "set" -> {
                val url = parts.getOrNull(1)?.trim().orEmpty()
                if (url.isEmpty()) {
                    session.send(
                        ServerMessage.Notification(context.i18n.t(lang, "panel:server:usage")))
                    return
                }
                editing.save(session, targetId, url, pages = emptyMap())
            }
            "clear" -> editing.save(session, targetId, externalUrl = "", pages = emptyMap())
            else ->
                session.send(ServerMessage.Notification(context.i18n.t(lang, "panel:server:usage")))
        }
    }
}
