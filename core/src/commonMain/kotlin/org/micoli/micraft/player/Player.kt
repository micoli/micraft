package org.micoli.micraft.player

import kotlinx.serialization.Serializable

@Serializable
data class Vec3(val x: Float, val y: Float, val z: Float)

@Serializable
data class Orientation(val yaw: Float, val pitch: Float)

@Serializable
data class PlayerState(
    val id: String,
    val name: String,
    val pos: Vec3,
    val orientation: Orientation,
)
