@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.game

import kotlin.js.JsAny
import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsAoEEffect
import org.micoli.micraft.babylon.jsBreathUpdate
import org.micoli.micraft.babylon.jsCharacterSync
import org.micoli.micraft.babylon.jsCompassUpdate
import org.micoli.micraft.babylon.jsHealthUpdate
import org.micoli.micraft.babylon.jsOpenActionBlockForm
import org.micoli.micraft.babylon.jsOpenQuestJournal
import org.micoli.micraft.babylon.jsPlayerDowned
import org.micoli.micraft.babylon.jsPlayerRespawned
import org.micoli.micraft.babylon.jsPlayerStatusUpdate
import org.micoli.micraft.babylon.jsQuestSync
import org.micoli.micraft.babylon.jsQuestUpdate
import org.micoli.micraft.babylon.jsSetWeatherZones
import org.micoli.micraft.babylon.jsShowCharacterCreation
import org.micoli.micraft.babylon.jsStatusEffectUpdate
import org.micoli.micraft.babylon.jsXpGained
import org.micoli.micraft.protocol.ServerMessage

/** Character sheet, combat/status effects, quests, weather and other server-pushed UI opens. */
class CharacterStatusHandler(private val scene: JsAny) : ServerMessageHandler {
    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.CharacterCreationRequired -> jsShowCharacterCreation()
            is ServerMessage.CharacterSync -> jsCharacterSync(Json.encodeToString(msg))
            is ServerMessage.HealthUpdate -> jsHealthUpdate(Json.encodeToString(msg))
            is ServerMessage.PlayerStatusUpdate -> jsPlayerStatusUpdate(Json.encodeToString(msg))
            is ServerMessage.StatusEffectUpdate -> jsStatusEffectUpdate(Json.encodeToString(msg))
            is ServerMessage.BreathUpdate -> jsBreathUpdate(Json.encodeToString(msg))
            is ServerMessage.CompassUpdate -> jsCompassUpdate(Json.encodeToString(msg))
            is ServerMessage.PlayerDowned -> jsPlayerDowned(msg.playerId)
            is ServerMessage.PlayerRespawned -> jsPlayerRespawned(Json.encodeToString(msg))
            is ServerMessage.XpGained -> jsXpGained(Json.encodeToString(msg))
            is ServerMessage.QuestSync -> jsQuestSync(Json.encodeToString(msg))
            is ServerMessage.QuestUpdate -> jsQuestUpdate(Json.encodeToString(msg))
            is ServerMessage.OpenQuestJournal -> jsOpenQuestJournal()
            is ServerMessage.AoEEffect -> jsAoEEffect(scene, msg.x, msg.y, msg.z, msg.radius)
            is ServerMessage.WeatherUpdate -> jsSetWeatherZones(Json.encodeToString(msg.zones))
            is ServerMessage.ActionBlockPayload -> jsOpenActionBlockForm(Json.encodeToString(msg))
            else -> Unit
        }
}
