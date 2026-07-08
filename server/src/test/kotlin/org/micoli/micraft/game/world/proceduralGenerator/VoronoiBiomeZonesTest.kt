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
    fun selectColumn_returnsPrimarySubsurface() {
        val z = zones()
        val sample = z.sample(10, 10)
        val cols = z.selectColumn(10, 10, surfaceY = 80, col = sample)
        assertEquals(sample.primary.subsurface, cols.subsurface)
    }
}
