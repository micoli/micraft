package org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator

import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.Region
import org.micoli.micraft.game.world.biome.BiomeDefinition

interface ChunkGenerator {
    fun generate(pos: ChunkPos): Chunk

    fun biomeAt(wx: Int, wz: Int): String = ""

    fun biomeDefinitionAt(wx: Int, wz: Int): BiomeDefinition? = null

    /**
     * Grass color of each column, RGB bytes indexed by `(lz * CHUNK_SIZE + lx) * 3`; empty if none.
     */
    fun grassTintsAt(pos: ChunkPos): ByteArray = ByteArray(0)

    fun zoneLevelAt(wx: Int, wz: Int): Int = 0

    val worldSeed: Long
        get() = 0L

    fun regionAt(wx: Int, wz: Int): Region? = null

    fun regionsNear(wx: Int, wz: Int, radiusBlocks: Int): List<Region> = emptyList()

    /** [region] and the Regions bordering it. */
    fun regionsAround(region: Region): List<Region> = listOf(region)

    fun distinctLowLevelSpawns(
        count: Int,
        ringRadius: Double,
        maxLevel: Int = 5
    ): List<Pair<Int, Int>> = emptyList()
}
