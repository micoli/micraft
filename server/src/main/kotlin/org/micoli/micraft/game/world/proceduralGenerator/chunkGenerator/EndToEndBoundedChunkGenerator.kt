package org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator

import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.biome.BiomeDefinition
import org.micoli.micraft.game.world.biome.BiomeZone

/**
 * Deterministic bounded flat world for browser end-to-end tests: a single grass plane at [groundY]
 * spanning [halfChunksX] * [halfChunksZ] chunks around the origin, pure AIR outside. No noise, no
 * vegetation, no walls — two calls for the same [ChunkPos] return byte-identical blocks so specs
 * can assert exact coordinates.
 *
 * The biome's `maxNpcs` is 0 so [org.micoli.micraft.game.npc.NpcSpawner] never auto-spawns, keeping
 * the player set under test limited to the connected clients.
 */
class EndToEndBoundedChunkGenerator(
    private val halfChunksX: Int = 4,
    private val halfChunksZ: Int = 4,
    private val groundY: Int = 64,
    @Suppress("unused") private val seed: Long = 1234L,
    private val biomeId: String = "plains",
    private val zoneLevel: Int = 1,
) : ChunkGenerator {

    val minX: Int = -halfChunksX * WorldConstants.CHUNK_SIZE
    val maxX: Int = halfChunksX * WorldConstants.CHUNK_SIZE - 1
    val minZ: Int = -halfChunksZ * WorldConstants.CHUNK_SIZE
    val maxZ: Int = halfChunksZ * WorldConstants.CHUNK_SIZE - 1
    val floorY: Int = groundY

    private val biome =
        BiomeDefinition(
            id = biomeId,
            zones = listOf(BiomeZone(moistureMin = 0.0, moistureMax = 1.0)),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT,
            elevationMin = groundY,
            elevationMax = groundY,
            maxNpcs = 0,
        )

    private fun inBounds(wx: Int, wz: Int): Boolean = wx in minX..maxX && wz in minZ..maxZ

    override fun generate(pos: ChunkPos): Chunk =
        Chunk.build(pos) { lx, y, lz ->
            val wx = pos.cx * WorldConstants.CHUNK_SIZE + lx
            val wz = pos.cz * WorldConstants.CHUNK_SIZE + lz
            when {
                !inBounds(wx, wz) -> BlockType.AIR
                y == 0 -> BlockType.BEDROCK
                y < groundY - 3 -> BlockType.STONE
                y < groundY -> BlockType.DIRT
                y == groundY -> BlockType.GRASS
                else -> BlockType.AIR
            }
        }

    override fun biomeAt(wx: Int, wz: Int): String = if (inBounds(wx, wz)) biomeId else ""

    override fun biomeDefinitionAt(wx: Int, wz: Int): BiomeDefinition? =
        if (inBounds(wx, wz)) biome else null

    override fun zoneLevelAt(wx: Int, wz: Int): Int = if (inBounds(wx, wz)) zoneLevel else 0
}
