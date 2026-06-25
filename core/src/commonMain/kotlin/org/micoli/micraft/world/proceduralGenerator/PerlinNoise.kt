package org.micoli.micraft.world.proceduralGenerator

import kotlin.math.floor

class PerlinNoise(seed: Long = 0L) {
    private val perm = IntArray(512)

    init {
        val p = IntArray(256) { it }
        // LCG-based Fisher-Yates shuffle — deterministic from seed, no stdlib Random needed
        var rng = seed xor -7046029254386353131L // 0x9e3779b97f4a7c15 as signed Long
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

    private fun grad(hash: Int, x: Double, y: Double) =
        when (hash and 3) {
            0 -> x + y
            1 -> -x + y
            2 -> x - y
            else -> -x - y
        }

    fun noise(x: Double, y: Double): Double {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)
        val u = fade(xf)
        val v = fade(yf)
        val aa = perm[perm[xi] + yi]
        val ab = perm[perm[xi] + yi + 1]
        val ba = perm[perm[xi + 1] + yi]
        val bb = perm[perm[xi + 1] + yi + 1]
        return lerp(
            v,
            lerp(u, grad(aa, xf, yf), grad(ba, xf - 1.0, yf)),
            lerp(u, grad(ab, xf, yf - 1.0), grad(bb, xf - 1.0, yf - 1.0)))
    }

    fun octaveNoise(
        x: Double,
        y: Double,
        octaves: Int = 4,
        persistence: Double = 0.5,
        lacunarity: Double = 2.0,
    ): Double {
        var value = 0.0
        var amplitude = 1.0
        var frequency = 1.0
        var maxValue = 0.0
        repeat(octaves) {
            value += noise(x * frequency, y * frequency) * amplitude
            maxValue += amplitude
            amplitude *= persistence
            frequency *= lacunarity
        }
        return value / maxValue // in [-1, 1]
    }
}
