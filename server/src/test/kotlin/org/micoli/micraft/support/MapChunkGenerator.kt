package org.micoli.micraft.support

import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator

class MapChunkGenerator(
    private val blocks: Map<Triple<Int, Int, Int>, BlockType> = emptyMap(),
    private val defaultBlock: BlockType = BlockType.AIR,
) : ChunkGenerator {
    override fun generate(pos: ChunkPos): Chunk =
        Chunk.build(pos) { lx, y, lz ->
            val wx = pos.cx * WorldConstants.CHUNK_SIZE + lx
            val wz = pos.cz * WorldConstants.CHUNK_SIZE + lz
            blocks[Triple(wx, y, wz)] ?: defaultBlock
        }
}
