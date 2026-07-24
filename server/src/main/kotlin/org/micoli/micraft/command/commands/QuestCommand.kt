package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.quest.QuestStatus

class QuestCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a3e1c0d2-7f4b-4a89-b256-1e3f8c9d0e5a")
    override val name = "quest"
    override val description = "Manage your quests."
    override val usage = "$command [list|accept|abandon|status] [id]"
    override val autocompleteArgs = listOf(0, 1)

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val qm = context.questManager
        if (qm == null) {
            session.send(ServerMessage.Notification("Quest system unavailable."))
            return
        }
        val parts = args.trim().split("\\s+".toRegex(), limit = 2)
        val sub = parts.getOrNull(0)?.lowercase() ?: ""
        val rest = parts.getOrNull(1)?.trim() ?: ""

        when (sub) {
            "",
            "ui",
            "journal" -> session.send(ServerMessage.OpenQuestJournal)
            "list" -> {
                val statusFilter =
                    rest.uppercase().let { s -> QuestStatus.entries.find { it.name == s } }
                val quests = session.state.quests
                if (quests.isEmpty()) {
                    session.send(ServerMessage.Notification("You have no quests."))
                    return
                }
                val lines =
                    quests.entries
                        .filter { statusFilter == null || it.value.status == statusFilter }
                        .joinToString("\n") { (id, p) ->
                            val def = qm.getDefinitions()[id]
                            val title = def?.title ?: id
                            "[${ p.status}] $title"
                        }
                session.send(
                    ServerMessage.Notification(if (lines.isBlank()) "No quests match." else lines))
            }
            "accept" -> {
                if (rest.isBlank()) {
                    session.send(ServerMessage.Notification("Usage: /quest accept <id>"))
                    return
                }
                qm.accept(session, rest)
            }
            "abandon" -> {
                if (rest.isBlank()) {
                    session.send(ServerMessage.Notification("Usage: /quest abandon <id>"))
                    return
                }
                qm.abandon(session, rest)
            }
            "status" -> {
                val questId = rest.ifBlank { null }
                if (questId == null) {
                    session.send(ServerMessage.Notification("Usage: /quest status <id>"))
                    return
                }
                val progress = session.state.quests[questId]
                val def = qm.getDefinitions()[questId]
                if (progress == null || def == null) {
                    session.send(ServerMessage.Notification("Quest not found: $questId"))
                    return
                }
                val lines =
                    buildString {
                            appendLine("${def.title} [${progress.status}] Lv${def.level}")
                            when (def.type) {
                                QuestType.KILL,
                                QuestType.BOSS ->
                                    def.objectives.forEach { obj ->
                                        val count = progress.progress[obj.npcType] ?: 0
                                        appendLine(
                                            "  ${obj.npcType}: $count / ${obj.requiredCount}")
                                    }
                                QuestType.FETCH ->
                                    def.itemType?.let { item ->
                                        val count = progress.progress[item] ?: 0
                                        appendLine("  $item: $count / ${def.requiredCount}")
                                    }
                                else -> {}
                            }
                        }
                        .trimEnd()
                session.send(ServerMessage.Notification(lines))
            }
            else ->
                session.send(
                    ServerMessage.Notification(
                        "Usage: /quest [list|accept|abandon|status|ui] [id]"))
        }
    }

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> {
        val qm = context.questManager ?: return emptyList()
        return when (argIndex) {
            0 ->
                listOf("list", "accept", "abandon", "status", "ui").filter {
                    it.contains(partial, ignoreCase = true)
                }
            1 -> qm.getDefinitions().keys.filter { it.contains(partial, ignoreCase = true) }
            else -> emptyList()
        }
    }
}
