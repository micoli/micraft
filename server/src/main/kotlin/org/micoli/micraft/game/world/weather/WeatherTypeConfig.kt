package org.micoli.micraft.game.world.weather

import kotlinx.serialization.Serializable

@Serializable
data class WeatherTypeConfig(
    val type: String,
    val biomes: List<String>,
    val enabled: Boolean = true,
    val spawnRatePerBiomeTick: Double = 0.0002,
    val minDurationTicks: Long = 1200,
    val maxDurationTicks: Long = 12000,
    val minRadius: Float = 48f,
    val maxRadius: Float = 192f,
    val driftSpeed: Float = 0.1f,
)
