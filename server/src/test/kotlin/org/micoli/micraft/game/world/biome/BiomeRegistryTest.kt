package org.micoli.micraft.game.world.biome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.micoli.micraft.game.world.BlockType

class BiomeRegistryTest {

    @Test
    fun selectByMoisture_picksZoneContainingValue() {
        val registry = BiomeRegistry.default()
        assertEquals("desert", registry.selectByMoisture(0.05).id)
        assertEquals("plains", registry.selectByMoisture(0.5).id)
        assertEquals("pine_forest", registry.selectByMoisture(0.99).id)
    }

    @Test
    fun selectByMoisture_boundaryIsExclusiveOnUpperEnd() {
        val registry = BiomeRegistry.default()
        // desert zone is [0.0, 0.12), dry_plains starts at 0.12
        assertEquals("dry_plains", registry.selectByMoisture(0.12).id)
    }

    @Test
    fun selectByMoisture_noMatch_fallsBackToFirstBiome() {
        val registry =
            BiomeRegistry(
                biomes =
                    listOf(
                        BiomeDefinition(
                            id = "only",
                            zones = listOf(BiomeZone(0.0, 0.5)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT)))
        assertEquals("only", registry.selectByMoisture(0.9).id)
    }

    @Test
    fun altitudeOverride_returnsNull_whenNoAltitudeConstrainedZoneMatches() {
        val registry = BiomeRegistry.default()
        assertNull(registry.altitudeOverride(surfaceY = 80, moisture = 0.5))
    }

    @Test
    fun altitudeOverride_matchesHighAltitudeSnowPeaks() {
        val registry = BiomeRegistry.default()
        val biome = registry.altitudeOverride(surfaceY = 200, moisture = 0.5)
        assertNotNull(biome)
        assertEquals("snow_peaks", biome.id)
    }

    @Test
    fun altitudeOverride_ignoresZoneOutsideAltitudeRange() {
        val registry = BiomeRegistry.default()
        assertNull(registry.altitudeOverride(surfaceY = 100, moisture = 0.5))
    }
}
