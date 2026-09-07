package org.micoli.micraft.game.world.biome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockType

class BiomeRegistryTest {

    @Test
    fun selectByMoisture_picksZoneContainingValue() {
        val registry = BiomeRegistry.default()
        assertEquals("sea", registry.selectByMoisture(0.05).id)
        assertEquals("plains", registry.selectByMoisture(0.5).id)
        assertEquals("pine_forest", registry.selectByMoisture(0.99).id)
    }

    @Test
    fun selectByMoisture_everyMoistureBiomeIsReachable() {
        val registry = BiomeRegistry.default()
        assertEquals("sea", registry.selectByMoisture(0.02).id)
        assertEquals("desert", registry.selectByMoisture(0.2).id)
        assertEquals("dry_plains", registry.selectByMoisture(0.4).id)
        assertEquals("plains", registry.selectByMoisture(0.5).id)
        assertEquals("forest", registry.selectByMoisture(0.6).id)
        assertEquals("lake", registry.selectByMoisture(0.7).id)
        assertEquals("pine_forest", registry.selectByMoisture(0.8).id)
    }

    @Test
    fun treeless_isTrueOnlyForBiomesWithoutTrees() {
        val byId = BiomeRegistry.default().biomes.associateBy { it.id }
        assertEquals(true, byId["sea"]!!.treeless)
        assertEquals(true, byId["lake"]!!.treeless)
        assertEquals(true, byId["desert"]!!.treeless)
        assertEquals(true, byId["dry_plains"]!!.treeless)
        assertEquals(false, byId["plains"]!!.treeless)
        assertEquals(false, byId["forest"]!!.treeless)
        assertEquals(false, byId["pine_forest"]!!.treeless)
    }

    @Test
    fun selectByMoisture_boundaryIsExclusiveOnUpperEnd() {
        val registry = BiomeRegistry.default()
        // sea zone is [0.0, 0.06), desert starts at 0.06
        assertEquals("desert", registry.selectByMoisture(0.06).id)
        // dry_plains zone is [0.35, 0.46), forest at 0.56, lake at 0.68
        assertEquals("dry_plains", registry.selectByMoisture(0.35).id)
        assertEquals("lake", registry.selectByMoisture(0.68).id)
        assertEquals("pine_forest", registry.selectByMoisture(0.74).id)
    }

    @Test
    fun aquaticBiomes_areLiquidWithFlatBandAndTint() {
        val byId = BiomeRegistry.default().biomes.associateBy { it.id }
        for (id in listOf("sea", "lake")) {
            val b = byId[id]!!
            assertTrue(b.liquid, "$id liquid")
            assertTrue(b.isAquatic, "$id isAquatic")
            assertTrue(b.waterLevel > 0)
            assertEquals(b.waterLevel, b.elevationMin, "$id flat band min")
            assertEquals(b.waterLevel, b.elevationMax, "$id flat band max")
            assertEquals(8, b.waterMaxDepth)
            assertEquals(3, b.tintColor!!.size)
            assertTrue(b.vegetation.isEmpty())
            assertNull(b.caverns)
        }
    }

    @Test
    fun allLandBiomes_shareElevationFloor() {
        val biomes = BiomeRegistry.default().biomes
        val maxWater = biomes.filter { it.liquid }.maxOf { it.waterLevel }
        biomes
            .filter { !it.liquid && it.zones.none { z -> z.altitudeConstrained } }
            .forEach {
                assertTrue(it.elevationMin >= 72, "${it.id} elevationMin >= 72")
                assertTrue(it.elevationMin >= maxWater, "${it.id} elevationMin >= maxWater")
            }
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
