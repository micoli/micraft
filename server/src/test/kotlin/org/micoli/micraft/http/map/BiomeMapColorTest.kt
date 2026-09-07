package org.micoli.micraft.http.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.biome.BiomeRegistry
import org.micoli.micraft.http.biomeMapColor

class BiomeMapColorTest {

    @Test
    fun everyBiomeGetsADistinctMapColor() {
        val colors = BiomeRegistry.default().biomes.map { it.id to biomeMapColor(it) }
        assertEquals(
            colors.size,
            colors.map { it.second }.toSet().size,
            "each biome must map to its own colour: $colors")
    }

    @Test
    fun aquaticBiomesUseBlueMapColor() {
        val byId = BiomeRegistry.default().biomes.associateBy { it.id }
        for (id in listOf("sea", "lake")) {
            val hex = biomeMapColor(byId[id]!!)
            val r = hex.substring(1, 3).toInt(16)
            val g = hex.substring(3, 5).toInt(16)
            val b = hex.substring(5, 7).toInt(16)
            assertTrue(b > r && b > g, "$id map color must be blue-dominant: $hex")
        }
        assertTrue(biomeMapColor(byId["sea"]!!) != biomeMapColor(byId["lake"]!!))
    }

    @Test
    fun grassBiomeUsesGrassColorTint_notPlainGrassBlock() {
        val plains = BiomeRegistry.default().biomes.first { it.id == "plains" }
        val forest = BiomeRegistry.default().biomes.first { it.id == "forest" }
        assertTrue(biomeMapColor(plains).startsWith("#"))
        assertTrue(biomeMapColor(plains) != biomeMapColor(forest))
    }
}
