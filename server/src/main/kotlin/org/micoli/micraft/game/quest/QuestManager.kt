package org.micoli.micraft.game.quest

import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.quest.QuestProgress
import org.micoli.micraft.quest.QuestStatus
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(QuestManager::class.java)

class QuestManager(
    private val getSessions: () -> Collection<PlayerSession>,
    private val savePlayer: (PlayerSession) -> Unit,
    private val grantXp: suspend (PlayerSession, Int) -> Unit = { _, _ -> },
    private val subscribeToChannel: suspend (PlayerSession, String) -> Unit = { _, _ -> },
    private val i18n: I18nConfig? = null,
) {
    @Volatile private var definitions: Map<String, QuestDefinition> = emptyMap()

    fun reloadDefinitions(defs: Map<String, QuestDefinition>) {
        definitions = defs
        log.info("Quest definitions reloaded: {} quests", defs.size)
    }

    fun getDefinitions(): Map<String, QuestDefinition> = definitions

    suspend fun sendQuestSync(session: PlayerSession) {
        session.send(ServerMessage.QuestSync(session.state.quests))
    }

    suspend fun accept(session: PlayerSession, questId: String) {
        val def = definitions[questId]
        if (def == null) {
            session.send(
                ServerMessage.Notification(
                    i18n?.t(session.state.language, "quest:server:not_found", questId)
                        ?: "Quest not found: $questId"))
            return
        }
        val current = session.state.quests[questId]
        when (current?.status) {
            QuestStatus.IN_PROGRESS -> {
                session.send(
                    ServerMessage.Notification(
                        i18n?.t(session.state.language, "quest:server:already_active")
                            ?: "Quest already active."))
                return
            }
            QuestStatus.COMPLETED -> {
                if (!def.repeatable) {
                    session.send(
                        ServerMessage.Notification(
                            i18n?.t(session.state.language, "quest:server:already_done")
                                ?: "Quest already completed (not repeatable)."))
                    return
                }
            }
            else -> {}
        }
        // Cooldown check for repeatable quests (status may be TODO after reset or COMPLETED)
        if (def.repeatable && def.cooldownSeconds > 0) {
            val lastCompleted = current?.lastCompletedAt
            if (lastCompleted != null) {
                val remaining =
                    (lastCompleted + def.cooldownSeconds * 1000L - System.currentTimeMillis()) /
                        1000L
                if (remaining > 0) {
                    session.send(
                        ServerMessage.Notification(
                            i18n?.t(session.state.language, "quest:server:cooldown", remaining)
                                ?: "Quest on cooldown for $remaining more seconds."))
                    return
                }
            }
        }
        val missingPrereq =
            def.dependsOn.firstOrNull { prereqId ->
                session.state.quests[prereqId]?.status != QuestStatus.COMPLETED
            }
        if (missingPrereq != null) {
            session.send(
                ServerMessage.Notification(
                    i18n?.t(session.state.language, "quest:server:prereq_missing")
                        ?: "Complete prerequisites first."))
            return
        }
        subscribeToChannel(session, "quest")
        val newProgress =
            QuestProgress(
                status = QuestStatus.IN_PROGRESS,
                progress = emptyMap(),
                acceptedAt = System.currentTimeMillis(),
            )
        updateQuestState(session, questId, newProgress)
        session.send(
            ServerMessage.Notification(
                i18n?.t(session.state.language, "quest:server:accepted", def.title)
                    ?: "Quest accepted: ${def.title}"))
        session.send(
            ServerMessage.ChatMessage(
                channel = "quest",
                sender = "",
                message = "[Quest] ${def.title}: ${def.description}",
            ))
    }

    suspend fun abandon(session: PlayerSession, questId: String) {
        val def = definitions[questId] ?: return
        if (session.state.quests[questId]?.status != QuestStatus.IN_PROGRESS) return
        val updatedProgress =
            (session.state.quests[questId] ?: QuestProgress()).copy(status = QuestStatus.ABANDONED)
        updateQuestState(session, questId, updatedProgress)
        session.send(
            ServerMessage.Notification(
                i18n?.t(session.state.language, "quest:server:abandoned", def.title)
                    ?: "Quest abandoned: ${def.title}."))
    }

    suspend fun onNpcKilled(npc: NpcInstance) {
        log.debug(
            "onNpcKilled: type={} contributors={}", npc.state.type, npc.damageContributors.keys)
        val sessions = getSessions()
        for (contributorId in npc.damageContributors.keys) {
            val session = sessions.find { it.id == contributorId } ?: continue
            log.debug(
                "onNpcKilled: player={} quests={}", session.state.name, session.state.quests.keys)
            for ((questId, progress) in session.state.quests) {
                if (progress.status != QuestStatus.IN_PROGRESS) continue
                val def = definitions[questId] ?: continue
                if (def.type != QuestType.KILL && def.type != QuestType.BOSS) continue
                val matchingObjective =
                    def.objectives.firstOrNull { it.npcType == npc.state.type } ?: continue
                if (def.area != null) {
                    val dx = npc.state.pos.x - def.area.x
                    val dz = npc.state.pos.z - def.area.z
                    if (dx * dx + dz * dz > def.area.radius * def.area.radius) continue
                }
                val newCount = (progress.progress[npc.state.type] ?: 0) + 1
                val newProgress =
                    progress.copy(
                        progress =
                            progress.progress.toMutableMap().also { it[npc.state.type] = newCount })
                updateQuestState(session, questId, newProgress)
                session.send(
                    ServerMessage.ChatMessage(
                        channel = "quest",
                        sender = "",
                        message =
                            "[Quest] ${def.title} — ${npc.state.name}: $newCount / ${matchingObjective.requiredCount}",
                    ))
                checkCompletion(session, questId, def, newProgress)
            }
        }
    }

    suspend fun onItemCollected(session: PlayerSession, itemType: ItemType, count: Int) {
        for ((questId, progress) in session.state.quests) {
            if (progress.status != QuestStatus.IN_PROGRESS) continue
            val def = definitions[questId] ?: continue
            if (def.type != QuestType.FETCH) continue
            if (def.itemType != itemType.id) continue
            val newCount = (progress.progress[itemType.id] ?: 0) + count
            val newProgress =
                progress.copy(
                    progress = progress.progress.toMutableMap().also { it[itemType.id] = newCount })
            updateQuestState(session, questId, newProgress)
            session.send(
                ServerMessage.ChatMessage(
                    channel = "quest",
                    sender = "",
                    message =
                        "[Quest] ${def.title}: $newCount / ${def.requiredCount} ${itemType.id}",
                ))
            checkCompletion(session, questId, def, newProgress)
        }
    }

    private suspend fun checkCompletion(
        session: PlayerSession,
        questId: String,
        def: QuestDefinition,
        progress: QuestProgress,
    ) {
        val completed =
            when (def.type) {
                QuestType.KILL,
                QuestType.BOSS ->
                    def.objectives.all { obj ->
                        (progress.progress[obj.npcType] ?: 0) >= obj.requiredCount
                    }
                QuestType.FETCH ->
                    def.itemType != null &&
                        (progress.progress[def.itemType] ?: 0) >= def.requiredCount
                else -> false
            }
        if (!completed) return

        val now = System.currentTimeMillis()
        val finalProgress =
            progress.copy(
                status = QuestStatus.COMPLETED,
                completedAt = now,
                lastCompletedAt = now,
            )
        updateQuestState(session, questId, finalProgress)

        if (def.rewards.xp > 0) grantXp(session, def.rewards.xp)
        if (def.rewards.items.isNotEmpty()) {
            for (item in def.rewards.items) {
                session.inventory.merge(ItemType(item.type), item.count, Int::plus)
            }
            session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        }

        session.send(
            ServerMessage.Notification(
                i18n?.t(session.state.language, "quest:server:completed", def.title)
                    ?: "Quest completed: ${def.title}!"))
        val rewardSummary = buildString {
            if (def.rewards.xp > 0) append("${def.rewards.xp} XP")
            if (def.rewards.items.isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append(def.rewards.items.joinToString(", ") { "${it.count}x ${it.type}" })
            }
        }
        session.send(
            ServerMessage.ChatMessage(
                channel = "quest",
                sender = "",
                message =
                    "[Quest] ✓ ${def.title} completed!" +
                        if (rewardSummary.isNotEmpty()) " Rewards: $rewardSummary" else "",
            ))
        log.info("Player {} completed quest {}", session.state.name, questId)

        if (def.repeatable) {
            updateQuestState(
                session,
                questId,
                QuestProgress(status = QuestStatus.TODO, lastCompletedAt = now),
            )
        }
    }

    private suspend fun updateQuestState(
        session: PlayerSession,
        questId: String,
        progress: QuestProgress,
    ) {
        val updatedQuests = session.state.quests.toMutableMap()
        updatedQuests[questId] = progress
        session.state = session.state.copy(quests = updatedQuests)
        session.send(ServerMessage.QuestUpdate(questId, progress))
        savePlayer(session)
    }
}
