package org.micoli.micraft.game.quest

import kotlinx.serialization.Serializable

@Serializable
data class QuestYamlEntry(
    val title: String = "",
    val description: String = "",
    val type: QuestType = QuestType.KILL,
    val level: Int = 1,
    val objectives: List<KillObjective> = emptyList(),
    val itemType: String? = null,
    val requiredCount: Int = 1,
    val area: QuestArea? = null,
    val rewards: QuestReward = QuestReward(),
    val dependsOn: List<String> = emptyList(),
    val repeatable: Boolean = false,
    val cooldownSeconds: Long = 0L,
) {
    fun toDefinition(id: String) =
        QuestDefinition(
            id = id,
            title = title,
            description = description,
            type = type,
            level = level,
            objectives = objectives,
            itemType = itemType,
            requiredCount = requiredCount,
            area = area,
            rewards = rewards,
            dependsOn = dependsOn,
            repeatable = repeatable,
            cooldownSeconds = cooldownSeconds,
        )
}
