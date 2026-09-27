package org.micoli.micraft.game.npc

import org.micoli.micraft.game.npc.roster.RegionPopulation
import org.micoli.micraft.game.npc.roster.RegionQuests
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.Region
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.player.Vec3
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(QuestGiverSpawner::class.java)

/**
 * Keeps exactly one Quest giver in every Region around the players that has a Quest suited to it,
 * placed near the Region's Voronoi seed, and keeps its offer list in step with the Region's Roster
 * (ADR-0010).
 */
class QuestGiverSpawner(private val population: RegionPopulation) {

    suspend fun trySpawn(
        world: WorldState,
        npcManager: NpcManager,
        definitions: Map<String, NpcDefinition>,
        loadedChunks: Collection<ChunkPos>,
    ) {
        if (loadedChunks.isEmpty()) return
        val giverTypes = definitions.values.filter { it.isQuestGiver }
        if (giverTypes.isEmpty()) return
        val quests = npcManager.questDefinitions()
        val liveGivers =
            npcManager
                .getAll()
                .filter { !it.isDead && it.definition.isQuestGiver }
                .groupBy { world.regionAt(it.state.pos) }
        val chunksByRegion = chunksByRegion(world, loadedChunks)

        for (region in (chunksByRegion.keys + liveGivers.keys).filterNotNull()) {
            val offers = RegionQuests.suitedTo(population.rosterOf(region), quests)
            val existing = liveGivers[region].orEmpty()
            if (offers.isEmpty()) {
                existing.forEach { npcManager.despawnNpc(it.state.id) }
                continue
            }
            existing.drop(1).forEach { npcManager.despawnNpc(it.state.id) }
            val giver =
                existing.firstOrNull()
                    ?: spawnGiver(
                        world, npcManager, giverTypes, region, chunksByRegion[region].orEmpty())
            giver?.offeredQuests = offers
        }
    }

    private suspend fun spawnGiver(
        world: WorldState,
        npcManager: NpcManager,
        giverTypes: List<NpcDefinition>,
        region: Region,
        chunks: List<ChunkPos>,
    ): NpcInstance? {
        val def = giverTypeFor(region, giverTypes) ?: return null
        val pos = groundNearSeed(world, region, chunks) ?: return null
        val name = npcManager.generateUniqueName(def.type)
        val giver = npcManager.spawnNpc(name, def.type, pos, region.dangerTier.npcLevelRange.first)
        log.info(
            "Quest giver '{}' ({}) spawned in {} (tier {})",
            name,
            def.type,
            region.name,
            region.dangerTier.tier)
        return giver
    }

    /** A giver whose Biomes include the Region's first, else any giver of the right level. */
    private fun giverTypeFor(region: Region, giverTypes: List<NpcDefinition>): NpcDefinition? {
        val ofLevel =
            giverTypes
                .filter { region.dangerLevel in it.minLevel..it.maxLevel }
                .sortedBy { it.type }
        return ofLevel.firstOrNull { region.biome.id in it.spawn.spawnBiomes }
            ?: ofLevel.firstOrNull()
    }

    /** Natural ground at the loaded chunk of the Region closest to its Voronoi seed. */
    private fun groundNearSeed(world: WorldState, region: Region, chunks: List<ChunkPos>): Vec3? {
        val byDistance =
            chunks.sortedBy { chunk ->
                val dx = centerX(chunk) - region.seedX
                val dz = centerZ(chunk) - region.seedZ
                dx.toLong() * dx + dz.toLong() * dz
            }
        return byDistance.firstNotNullOfOrNull { naturalGroundAtCenter(world, it) }
    }

    private fun naturalGroundAtCenter(world: WorldState, chunkPos: ChunkPos): Vec3? {
        val chunk = world.getChunkIfDiscovered(chunkPos) ?: return null
        val half = WorldConstants.CHUNK_SIZE / 2
        val wx = centerX(chunkPos)
        val wz = centerZ(chunkPos)
        val y =
            (chunk.topY() downTo WorldConstants.WORLD_MIN_Y).firstOrNull {
                chunk.getBlock(half, it, half).isSolid
            } ?: return null
        if (!world.isNaturalGround(wx, y, wz)) return null
        if (world.getBlockIfLoaded(wx, y + 1, wz).isLiquid) return null
        return Vec3(wx + 0.5f, (y + 1).toFloat(), wz + 0.5f)
    }

    private fun chunksByRegion(
        world: WorldState,
        chunks: Collection<ChunkPos>
    ): Map<Region, List<ChunkPos>> =
        chunks
            .mapNotNull { chunk ->
                world.regionAt(centerX(chunk), centerZ(chunk))?.let { it to chunk }
            }
            .groupBy({ it.first }, { it.second })

    private fun centerX(chunk: ChunkPos) =
        chunk.cx * WorldConstants.CHUNK_SIZE + WorldConstants.CHUNK_SIZE / 2

    private fun centerZ(chunk: ChunkPos) =
        chunk.cz * WorldConstants.CHUNK_SIZE + WorldConstants.CHUNK_SIZE / 2
}
