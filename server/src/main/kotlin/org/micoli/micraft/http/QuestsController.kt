package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.quest.KillObjective
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestReward

@Serializable
data class QuestDto(
    val id: String,
    val title: String,
    val description: String,
    val type: String,
    val level: Int,
    val objectives: List<KillObjective>,
    val itemType: String?,
    val requiredCount: Int,
    val rewards: QuestReward,
    val dependsOn: List<String>,
    val repeatable: Boolean,
    val cooldownSeconds: Long,
)

class QuestsController(private val questManager: QuestManager) {
    fun register(routing: Route) {
        routing.get("/api/quests") {
            val dtos =
                questManager.getDefinitions().map { (id, def) ->
                    QuestDto(
                        id = id,
                        title = def.title,
                        description = def.description,
                        type = def.type.name,
                        level = def.level,
                        objectives = def.objectives,
                        itemType = def.itemType,
                        requiredCount = def.requiredCount,
                        rewards = def.rewards,
                        dependsOn = def.dependsOn,
                        repeatable = def.repeatable,
                        cooldownSeconds = def.cooldownSeconds,
                    )
                }
            call.respondText(
                Json.encodeToString(ListSerializer(QuestDto.serializer()), dtos),
                ContentType.Application.Json)
        }
    }
}
