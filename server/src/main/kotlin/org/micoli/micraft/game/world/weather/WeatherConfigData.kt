package org.micoli.micraft.game.world.weather

import kotlinx.serialization.Serializable

@Serializable
data class WeatherConfigData(
    val enabled: Boolean = true,
    val weatherTypes: List<WeatherTypeConfig> = emptyList(),
)
