package org.micoli.micraft.game.world

import org.micoli.micraft.game.world.biome.BiomeDefinition

/** A named Voronoi cell of the World, with one Biome and one Danger level. See CONTEXT.md. */
data class Region(
    val seedX: Int,
    val seedZ: Int,
    val biome: BiomeDefinition,
    val name: String,
    val dangerLevel: Int,
) {
    val dangerTier: ZoneTier
        get() = ZoneTier.fromZoneLevel(dangerLevel)
}
