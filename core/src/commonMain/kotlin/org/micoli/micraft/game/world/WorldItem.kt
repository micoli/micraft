package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.Vec3

@Serializable
data class WorldItem(
    val id: String,
    val pos: Vec3,
    val type: ItemType,
    val count: Int,
)
