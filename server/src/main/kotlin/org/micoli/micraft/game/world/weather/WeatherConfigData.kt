package org.micoli.micraft.game.world.weather

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
@JsonSchemaRoot(file = "weather.schema.json")
data class WeatherConfigData(
    val enabled: Boolean = true,
    val weatherTypes: List<WeatherTypeConfig> = emptyList(),
)
