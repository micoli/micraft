package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.math.floor

class PerlinNoise3D(seed: Long = 0L) {
    private val perm = IntArray(512)

    init {
        val p = IntArray(256) { it }
        var rng = seed xor -7046029254386353131L
        for (i in 255 downTo 1) {
            rng = rng * 6364136223846793005L + 1442695040888963407L
            val j = ((rng ushr 33).toInt() and 0x7fffffff) % (i + 1)
            val tmp = p[i]
            p[i] = p[j]
            p[j] = tmp
        }
        repeat(512) { perm[it] = p[it and 255] }
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6.0 - 15.0) + 10.0)

    private fun lerp(t: Double, a: Double, b: Double) = a + t * (b - a)

    private fun grad(hash: Int, x: Double, y: Double, z: Double): Double =
        when (hash and 0xF) {
            0x0 -> x + y
            0x1 -> -x + y
            0x2 -> x - y
            0x3 -> -x - y
            0x4 -> x + z
            0x5 -> -x + z
            0x6 -> x - z
            0x7 -> -x - z
            0x8 -> y + z
            0x9 -> -y + z
            0xA -> y - z
            0xB -> -y - z
            0xC -> y + x
            0xD -> -y + z
            0xE -> y - x
            else -> -y - z
        }

    fun noise(x: Double, y: Double, z: Double): Double {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val zi = floor(z).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)
        val zf = z - floor(z)
        val u = fade(xf)
        val v = fade(yf)
        val w = fade(zf)
        val aaa = perm[perm[perm[xi] + yi] + zi]
        val aab = perm[perm[perm[xi] + yi] + zi + 1]
        val aba = perm[perm[perm[xi] + yi + 1] + zi]
        val abb = perm[perm[perm[xi] + yi + 1] + zi + 1]
        val baa = perm[perm[perm[xi + 1] + yi] + zi]
        val bab = perm[perm[perm[xi + 1] + yi] + zi + 1]
        val bba = perm[perm[perm[xi + 1] + yi + 1] + zi]
        val bbb = perm[perm[perm[xi + 1] + yi + 1] + zi + 1]
        val x1 = lerp(u, grad(aaa, xf, yf, zf), grad(baa, xf - 1.0, yf, zf))
        val x2 = lerp(u, grad(aba, xf, yf - 1.0, zf), grad(bba, xf - 1.0, yf - 1.0, zf))
        val y1 = lerp(v, x1, x2)
        val x3 = lerp(u, grad(aab, xf, yf, zf - 1.0), grad(bab, xf - 1.0, yf, zf - 1.0))
        val x4 = lerp(u, grad(abb, xf, yf - 1.0, zf - 1.0), grad(bbb, xf - 1.0, yf - 1.0, zf - 1.0))
        val y2 = lerp(v, x3, x4)
        return lerp(w, y1, y2)
    }

    fun octaveNoise(
        x: Double,
        y: Double,
        z: Double,
        octaves: Int = 3,
        persistence: Double = 0.5,
        lacunarity: Double = 2.0,
    ): Double {
        var value = 0.0
        var amplitude = 1.0
        var frequency = 1.0
        var maxValue = 0.0
        repeat(octaves) {
            value += noise(x * frequency, y * frequency, z * frequency) * amplitude
            maxValue += amplitude
            amplitude *= persistence
            frequency *= lacunarity
        }
        return value / maxValue
    }
}
