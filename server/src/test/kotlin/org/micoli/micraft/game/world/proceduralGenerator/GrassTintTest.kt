package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.biome.BiomeConfig
import org.micoli.micraft.game.world.biome.BiomeDefinition
import org.micoli.micraft.game.world.biome.BiomeRegistry
import org.micoli.micraft.game.world.biome.BiomeZone

class GrassTintTest {
    private val green = listOf(0.2, 0.8, 0.2)
    private val yellow = listOf(0.8, 0.7, 0.2)

    private fun biome(id: String, zone: BiomeZone, grassColor: List<Double>) =
        BiomeDefinition(
            id = id,
            zones = listOf(zone),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT,
            grassColor = grassColor,
        )

    private val generator =
        ProceduralChunkGenerator(
            biomeRegistry =
                BiomeRegistry.from(
                    BiomeConfig(
                        biomes =
                            listOf(
                                biome("plains", BiomeZone(0.0, 0.5), green),
                                biome("dry_plains", BiomeZone(0.5, 1.0), yellow),
                            ),
                        voronoiCellSize = 32,
                        voronoiBlendRadius = 4,
                    )))

    private val s = WorldConstants.CHUNK_SIZE

    private fun tintAt(wx: Int, wz: Int): List<Int> {
        val pos = ChunkPos(Math.floorDiv(wx, s), Math.floorDiv(wz, s))
        val tints = generator.grassTintsAt(pos)
        val i = (Math.floorMod(wz, s) * s + Math.floorMod(wx, s)) * 3
        return (0..2).map { tints[i + it].toInt() and 0xFF }
    }

    private fun bytes(color: List<Double>) = color.map { (it * 255).roundToInt() }

    private fun columnDeepIn(biomeId: String): Pair<Int, Int>? {
        for (wx in 0 until 2048 step 3) for (wz in 0 until 256 step 3) {
            val sample = generator.voronoi.sample(wx, wz)
            if (sample.primary.id == biomeId && sample.blendFactor >= 1.0) return wx to wz
        }
        return null
    }

    @Test
    fun `one RGB triple per column`() {
        assertEquals(s * s * 3, generator.grassTintsAt(ChunkPos(3, -2)).size)
    }

    @Test
    fun `a column deep inside a biome gets that biome's grass color`() {
        val (px, pz) = assertNotNull(columnDeepIn("plains"))
        val (dx, dz) = assertNotNull(columnDeepIn("dry_plains"))

        assertEquals(bytes(green), tintAt(px, pz))
        assertEquals(bytes(yellow), tintAt(dx, dz))
    }

    @Test
    fun `neighbouring columns across a biome border get close colors`() {
        var borders = 0
        for (wx in 0 until 512) {
            if (generator.voronoi.sample(wx, 0).primary.id ==
                generator.voronoi.sample(wx + 1, 0).primary.id)
                continue
            borders++
            val a = tintAt(wx, 0)
            val b = tintAt(wx + 1, 0)
            for (c in 0..2) assertTrue(abs(a[c] - b[c]) <= 40, "jump at x=$wx: $a -> $b")
        }
        assertTrue(borders > 0)
    }
}
