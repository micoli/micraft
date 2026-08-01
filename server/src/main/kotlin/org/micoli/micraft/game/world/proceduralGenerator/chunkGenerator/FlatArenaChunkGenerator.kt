package org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator

import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.biome.BiomeDefinition
import org.micoli.micraft.game.world.biome.BiomeZone

/**
 * Flat walled arena used by the admin world simulator: grass floor at [groundY], unbreakable wall
 * ring [wallHeight] blocks tall on the `|x| == halfSize || |z| == halfSize` perimeter so NPCs
 * cannot wander out of view. Ground is generated outside the perimeter too, so nothing can fall
 * into the void when spawned on the edge.
 *
 * [zoneLevel] and [maxNpcs] feed the level window and per-biome cap that
 * [ org.micoli.micraft.game.npc.NpcSpawner] applies, so auto-spawn behaves as it does in the real
 * world.
 */
class FlatArenaChunkGenerator(
    private val halfSize: Int = 100,
    private val groundY: Int = 7,
    private val wallHeight: Int = 4,
    private val biomeId: String = "plains",
    private val maxNpcs: Int = 0,
    private val zoneLevel: Int = 5,
) : ChunkGenerator {

    private val biome =
        BiomeDefinition(
            id = biomeId,
            zones = listOf(BiomeZone(moistureMin = 0.0, moistureMax = 1.0)),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT,
            elevationMin = groundY,
            elevationMax = groundY,
            maxNpcs = maxNpcs,
        )

    /** Inclusive world-space bounds of the walkable area. */
    val minCoord: Int
        get() = -halfSize + 1

    val maxCoord: Int
        get() = halfSize - 1

    val floorY: Int
        get() = groundY

    override fun generate(pos: ChunkPos): Chunk =
        Chunk.build(pos) { lx, y, lz ->
            // Chunk.build hands out chunk-local coordinates
            val wx = pos.cx * WorldConstants.CHUNK_SIZE + lx
            val wz = pos.cz * WorldConstants.CHUNK_SIZE + lz
            val onWall = wx == -halfSize || wx == halfSize || wz == -halfSize || wz == halfSize
            when {
                y == 0 -> BlockType.BEDROCK
                y < groundY - 2 -> BlockType.STONE
                y < groundY -> BlockType.DIRT
                y == groundY -> BlockType.GRASS
                onWall && y <= groundY + wallHeight -> BlockType.BEDROCK
                else -> BlockType.AIR
            }
        }

    override fun biomeAt(wx: Int, wz: Int): String = biomeId

    override fun biomeDefinitionAt(wx: Int, wz: Int): BiomeDefinition = biome

    override fun zoneLevelAt(wx: Int, wz: Int): Int = zoneLevel
}
