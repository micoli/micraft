package org.micoli.micraft.world.proceduralGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerlinNoiseTest {

    @Test
    fun sameSeed_producesDeterministicNoise() {
        val a = PerlinNoise(42L)
        val b = PerlinNoise(42L)
        assertEquals(a.noise(1.3, 4.7), b.noise(1.3, 4.7))
    }

    @Test
    fun differentSeeds_produceDifferentNoise() {
        val a = PerlinNoise(1L)
        val b = PerlinNoise(2L)
        assertTrue(a.noise(1.3, 4.7) != b.noise(1.3, 4.7))
    }

    @Test
    fun noise_isBoundedRoughlyInUnitRange() {
        val n = PerlinNoise(7L)
        for (x in 0 until 50) {
            for (y in 0 until 50) {
                val v = n.noise(x / 3.0, y / 3.0)
                assertTrue(v in -1.1..1.1, "noise out of range: $v")
            }
        }
    }

    @Test
    fun noise_atIntegerLattice_isZero() {
        val n = PerlinNoise(7L)
        assertEquals(0.0, n.noise(3.0, 5.0))
    }

    @Test
    fun octaveNoise_isBoundedInUnitRange() {
        val n = PerlinNoise(9L)
        for (x in 0 until 20) {
            for (y in 0 until 20) {
                val v = n.octaveNoise(x / 5.0, y / 5.0)
                assertTrue(v in -1.0..1.0, "octaveNoise out of range: $v")
            }
        }
    }

    @Test
    fun octaveNoise_singleOctave_matchesPlainNoise() {
        val n = PerlinNoise(3L)
        assertEquals(n.noise(2.25, 1.75), n.octaveNoise(2.25, 1.75, octaves = 1))
    }
}
