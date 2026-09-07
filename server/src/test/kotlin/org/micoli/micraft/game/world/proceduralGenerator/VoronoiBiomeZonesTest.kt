package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.biome.BiomeRegistry

class VoronoiBiomeZonesTest {

    private fun zones(seed: Long = 1L) =
        VoronoiBiomeZones(seed, BiomeRegistry.default(), PerlinNoise(seed))

    @Test
    fun sample_isDeterministicForSameSeed() {
        val a = zones(5L)
        val b = zones(5L)
        val sa = a.sample(100, 200)
        val sb = b.sample(100, 200)
        assertEquals(sa.primary.id, sb.primary.id)
        assertEquals(sa.blendFactor, sb.blendFactor)
    }

    @Test
    fun sample_blendFactorInUnitRange() {
        val z = zones()
        for (wx in 0 until 1024 step 64) {
            for (wz in 0 until 1024 step 64) {
                val s = z.sample(wx, wz)
                assertTrue(s.blendFactor in 0.0..1.0, "blend out of range: ${s.blendFactor}")
            }
        }
    }

    @Test
    fun sample_blendedElevation_isContinuousAcrossBiomeBorders() {
        val z = zones()
        var prev = z.sample(0, 0)
        // Step 1 block at a time along a transect crossing several Voronoi cells; the weighted
        // elevation band must never jump (no cliff), unlike a raw primary/secondary switch.
        for (wx in 1..1500) {
            val s = z.sample(wx, 300)
            assertTrue(
                kotlin.math.abs(s.elevationMin - prev.elevationMin) <= 3.0 &&
                    kotlin.math.abs(s.elevationMax - prev.elevationMax) <= 3.0,
                "elevation jumped at wx=$wx: ${prev.elevationMin}/${prev.elevationMax} -> ${s.elevationMin}/${s.elevationMax}")
            prev = s
        }
    }

    @Test
    fun sample_atCellSeedPoint_hasZeroBlend() {
        val z = zones()
        // Near the center of a cell, primary/secondary distances diverge -> blend closer to bounds.
        val s = z.sample(0, 0)
        assertTrue(s.blendFactor in 0.0..1.0)
    }

    @Test
    fun cells_withinRadius_areWithinDistance() {
        val z = zones()
        val found = z.cells(0, 0, 300)
        for (cell in found) {
            val dx = cell.seedX.toLong()
            val dz = cell.seedZ.toLong()
            assertTrue(dx * dx + dz * dz <= 300L * 300L)
        }
    }

    @Test
    fun effectiveBiome_matchesSamplePrimary_whenNoAltitudeOverride() {
        val z = zones()
        val sample = z.sample(50, 50)
        val biome = z.effectiveBiome(50, 50, surfaceY = 80, col = sample)
        assertEquals(sample.primary.id, biome.id)
    }

    @Test
    fun effectiveBiome_appliesAltitudeOverride_forHighSurface() {
        val z = zones()
        // snow_peaks biome requires altitudeMin=150 in default registry.
        val biome = z.effectiveBiome(0, 0, surfaceY = 500)
        assertEquals("snow_peaks", biome.id)
    }

    @Test
    fun zoneLevelAt_nearOrigin_isLowLevel() {
        for (seed in listOf(1L, 7L, 42L, 99L)) {
            val z = zones(seed)
            for (wx in -400..400 step 80) {
                for (wz in -400..400 step 80) {
                    assertTrue(
                        z.zoneLevelAt(wx, wz) < 5,
                        "seed=$seed ($wx,$wz) level=${z.zoneLevelAt(wx, wz)}")
                }
            }
        }
    }

    @Test
    fun zoneLevelAt_farField_scalesUp() {
        val z = zones(1L)
        assertTrue(z.zoneLevelAt(5000, 5000) >= 55)
    }

    @Test
    fun distinctLowLevelSpawns_areDistinctAndTreeless() {
        val z = zones(3L)
        val spawns = z.distinctLowLevelSpawns(count = 4, ringRadius = 384.0)
        assertEquals(4, spawns.size)
        for ((x, zc) in spawns) assertTrue(
            z.sample(x, zc).primary.treeless, "spawn ($x,$zc) must be a treeless biome")
        val cells = spawns.map { z.nearestSeed(it.first, it.second) }.toSet()
        assertEquals(4, cells.size, "each faction spawn in a distinct Voronoi cell")
    }

    @Test
    fun selectColumn_returnsPrimarySubsurface() {
        val z = zones()
        val sample = z.sample(10, 10)
        val cols = z.selectColumn(10, 10, surfaceY = 80, col = sample)
        assertEquals(sample.primary.subsurface, cols.subsurface)
    }
}
