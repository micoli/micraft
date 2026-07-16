package org.micoli.micraft.game.quest

import kotlinx.serialization.Serializable

enum class QuestType {
    KILL,
    FETCH,
    ESCORT,
    EXPLORE,
    BOSS
}

@Serializable data class QuestArea(val x: Float, val z: Float, val radius: Float)

@Serializable data class KillObjective(val npcType: String, val requiredCount: Int)

@Serializable data class RewardItem(val type: String, val count: Int)

@Serializable data class QuestReward(val xp: Int = 0, val items: List<RewardItem> = emptyList())

data class QuestDefinition(
    val id: String,
    val title: String,
    val description: String,
    val type: QuestType,
    val level: Int = 1,
    val objectives: List<KillObjective> = emptyList(),
    val itemType: String? = null,
    val requiredCount: Int = 1,
    val area: QuestArea? = null,
    val rewards: QuestReward = QuestReward(),
    val dependsOn: List<String> = emptyList(),
    val repeatable: Boolean = false,
    val cooldownSeconds: Long = 0L,
)
