package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

@Serializable
data class WeatherZoneInfo(
    val id: String,
    val type: String,
    val cx: Float,
    val cz: Float,
    val radius: Float,
    val intensity: Float,
)
