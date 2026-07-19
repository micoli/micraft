package org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator

import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.biome.BiomeDefinition

interface ChunkGenerator {
    fun generate(pos: ChunkPos): Chunk

    fun biomeAt(wx: Int, wz: Int): String = ""

    fun biomeDefinitionAt(wx: Int, wz: Int): BiomeDefinition? = null

    fun zoneLevelAt(wx: Int, wz: Int): Int = 0
}
