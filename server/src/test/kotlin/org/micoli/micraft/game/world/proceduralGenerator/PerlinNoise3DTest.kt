package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerlinNoise3DTest {

    @Test
    fun sameSeed_producesDeterministicNoise() {
        val a = PerlinNoise3D(42L)
        val b = PerlinNoise3D(42L)
        assertEquals(a.noise(1.3, 4.7, 2.1), b.noise(1.3, 4.7, 2.1))
    }

    @Test
    fun differentSeeds_produceDifferentNoise() {
        val a = PerlinNoise3D(1L)
        val b = PerlinNoise3D(2L)
        assertTrue(a.noise(1.3, 4.7, 2.1) != b.noise(1.3, 4.7, 2.1))
    }

    @Test
    fun noise_isBoundedRoughlyInUnitRange() {
        val n = PerlinNoise3D(7L)
        for (x in 0 until 20) {
            for (y in 0 until 20) {
                for (z in 0 until 20) {
                    val v = n.noise(x / 3.0, y / 3.0, z / 3.0)
                    assertTrue(v in -1.1..1.1, "noise out of range: $v at ($x,$y,$z)")
                }
            }
        }
    }

    @Test
    fun noise_atIntegerLattice_isZero() {
        val n = PerlinNoise3D(7L)
        assertEquals(0.0, n.noise(3.0, 5.0, 2.0))
    }

    @Test
    fun octaveNoise_isBoundedInUnitRange() {
        val n = PerlinNoise3D(9L)
        for (x in 0 until 10) {
            for (y in 0 until 10) {
                for (z in 0 until 10) {
                    val v = n.octaveNoise(x / 5.0, y / 5.0, z / 5.0)
                    assertTrue(v in -1.0..1.0, "octaveNoise out of range: $v at ($x,$y,$z)")
                }
            }
        }
    }

    @Test
    fun octaveNoise_singleOctave_matchesPlainNoise() {
        val n = PerlinNoise3D(3L)
        assertEquals(n.noise(2.25, 1.75, 0.5), n.octaveNoise(2.25, 1.75, 0.5, octaves = 1))
    }

    @Test
    fun differentAxes_produceDifferentValues() {
        val n = PerlinNoise3D(5L)
        val xy = n.noise(1.5, 2.5, 0.0)
        val xz = n.noise(1.5, 0.0, 2.5)
        val yz = n.noise(0.0, 1.5, 2.5)
        assertTrue(xy != xz || xz != yz, "all three axis permutations should differ")
    }
}
