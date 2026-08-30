package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

/**
 * `/pet <list|spawn|dismiss|resurrect|rename>` — manage the tamed-pet roster. Only one pet may be
 * summoned at a time; `spawn` retires the current one first.
 */
class PetCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c8f5d2b3-4e6a-4b7c-9d0e-1f2a3b4c5d6e")
    override val name = "pet"
    override val description = "Manage your tamed pets (list, spawn, dismiss, resurrect, rename)."
    override val usage = "$command <list|spawn|dismiss|resurrect|rename> [name] [newName]"
    override val autocompleteArgs = listOf(0, 1)

    private val subs = listOf("list", "spawn", "dismiss", "resurrect", "rename")

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> {
        if (argIndex == 0) return subs.filter { it.startsWith(partial, ignoreCase = true) }
        if (argIndex == 1 && session != null) {
            return session.state.pets
                .map { it.name }
                .filter { it.contains(partial, ignoreCase = true) }
        }
        return emptyList()
    }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val petManager = context.petManager ?: return
        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val sub = parts.getOrNull(0)?.lowercase()

        when (sub) {
            "list" -> {
                if (session.state.pets.isEmpty()) {
                    session.send(ServerMessage.Notification(i18n.t(lang, "pet:server:none_owned")))
                } else {
                    session.send(ServerMessage.Notification(i18n.t(lang, "pet:server:list_header")))
                    session.state.pets.forEach { r ->
                        val status =
                            when {
                                r.id == session.state.activePetId -> "active"
                                r.dead -> "dead"
                                else -> "idle"
                            }
                        session.send(
                            ServerMessage.Notification(
                                i18n.t(
                                    lang,
                                    "pet:server:list_line",
                                    r.name,
                                    r.level,
                                    r.npcType,
                                    status)))
                    }
                }
                petManager.rosterSyncFor(session)
            }
            "spawn" -> {
                val name = parts.drop(1).joinToString(" ")
                if (name.isBlank()) {
                    session.send(ServerMessage.Notification(i18n.t(lang, "pet:server:usage")))
                    return
                }
                petManager.summon(session, name)
            }
            "dismiss" -> petManager.dismiss(session)
            "resurrect" -> {
                val name = parts.drop(1).joinToString(" ")
                if (name.isBlank()) {
                    session.send(ServerMessage.Notification(i18n.t(lang, "pet:server:usage")))
                    return
                }
                petManager.resurrect(session, name)
            }
            "rename" -> {
                if (parts.size < 3) {
                    session.send(ServerMessage.Notification(i18n.t(lang, "pet:server:usage")))
                    return
                }
                petManager.rename(session, parts[1], parts.drop(2).joinToString(" "))
            }
            else -> session.send(ServerMessage.Notification(i18n.t(lang, "pet:server:usage")))
        }
    }
}
