package org.micoli.micraft.game.npc

import org.micoli.micraft.game.npc.roster.RegionPopulation
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.Vec3
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(NpcSpawner::class.java)

class NpcSpawner {
    @Volatile private var latestDefinitions: Map<String, NpcDefinition> = emptyMap()
    private var population: RegionPopulation? = null

    /**
     * Fills the Regions around [loadedChunks] from their Roster, each type up to its share of the
     * Region budget (ADR-0010).
     */
    suspend fun trySpawn(
        world: WorldState,
        npcManager: NpcManager,
        definitions: Map<String, NpcDefinition>,
        loadedChunks: Collection<ChunkPos>,
        ctx: NpcTickContext = NpcTickContext.live,
        canSpawn: () -> Boolean = { true },
    ) {
        if (loadedChunks.isEmpty()) return
        val population = populationFor(world, definitions)
        val census = population.census(npcManager.getAll())
        // Built once per pass, then kept up to date locally: rescanning every NPC for each attempt
        // is what these snapshots avoid.
        val counts = npcManager.countsByType().toMutableMap()
        val density = npcManager.spawnDensitySnapshot()
        val attempts = HashMap<Long, Int>()

        for (chunkPos in loadedChunks.toList().shuffled(ctx.random)) {
            if (!canSpawn()) return
            val wx =
                chunkPos.cx * WorldConstants.CHUNK_SIZE +
                    ctx.random.nextInt(WorldConstants.CHUNK_SIZE)
            val wz =
                chunkPos.cz * WorldConstants.CHUNK_SIZE +
                    ctx.random.nextInt(WorldConstants.CHUNK_SIZE)
            val region = world.regionAt(wx, wz) ?: continue
            if (census.isFull(region)) continue
            val roster = population.rosterOf(region)
            // Counted before the expensive checks below, which are what this cap bounds.
            val tried = attempts.merge(region.key, 1, Int::plus) ?: 0
            if (tried > ctx.tuning.maxSpawnAttemptsPerTick * roster.entries.size) continue

            val def =
                roster.entries
                    .filter { census.count(region, it.type) < it.share }
                    .randomOrNull(ctx.random)
                    ?.let { definitions[it.type] } ?: continue
            val type = def.type
            if (def.spawn.maxTotal > 0 && (counts[type] ?: 0) >= def.spawn.maxTotal) continue
            if (density.countByTypeInChunk(type, chunkPos) >= def.spawn.maxPerChunk) continue

            val spawnPos = spawnPosition(world, def, wx, wz, ctx) ?: continue
            val instanceLevel =
                (region.dangerLevel + ctx.random.nextInt(-3, 4)).coerceIn(
                    1, WorldConstants.RPG_LEVEL_MAX)
            // the animal record comes with the spawn now — see NpcManager.spawnNpc
            npcManager.spawnNpc(npcManager.generateUniqueName(type), type, spawnPos, instanceLevel)
            density.recordSpawn(chunkPos, type, npcManager.zoneKey(wx.toFloat(), wz.toFloat()))
            census.record(region, type)
            counts.merge(type, 1, Int::plus)
            log.debug("Auto-spawned {} in {} at ({},{},{})", type, region.name, wx, spawnPos.y, wz)
        }
    }

    private fun populationFor(
        world: WorldState,
        definitions: Map<String, NpcDefinition>,
    ): RegionPopulation {
        latestDefinitions = definitions
        return population ?: RegionPopulation(world) { latestDefinitions }.also { population = it }
    }

    private fun spawnPosition(
        world: WorldState,
        def: NpcDefinition,
        wx: Int,
        wz: Int,
        ctx: NpcTickContext,
    ): Vec3? {
        val biomeDef = world.biomeDefinitionAt(wx, wz)
        // A liquid biome is water top to bottom — only a swimmer can live there.
        if (biomeDef?.liquid == true && !def.canSwim) return null
        // A walker must land on the biome's actual terrain — never a tree canopy/trunk or a
        // player-built roof, both of which are `isSolid` too.
        val surfaceY =
            findSurfaceY(world, wx, wz, requireNaturalGround = !def.isAquatic) ?: return null
        val spawnY =
            if (def.isAquatic) {
                if (biomeDef == null || !biomeDef.liquid || biomeDef.waterLevel <= surfaceY)
                    return null
                val y = ctx.random.nextInt(surfaceY, biomeDef.waterLevel)
                if (!world.getBlockIfLoaded(wx, y, wz).isLiquid) return null
                y
            } else surfaceY
        val spawnPos = Vec3(wx + 0.5f, spawnY.toFloat(), wz + 0.5f)
        val solid = { bx: Int, by: Int, bz: Int -> world.getBlockIfLoaded(bx, by, bz).isSolid }
        val clearX =
            AabbCollider.resolveX(
                solid, spawnPos.x, spawnPos.y, spawnPos.z, def.width, def.height, 0f)
        val clearZ =
            AabbCollider.resolveZ(
                solid, spawnPos.x, spawnPos.y, spawnPos.z, def.width, def.height, 0f)
        if (clearX != 0f || clearZ != 0f) return null
        return spawnPos
    }

    private fun findSurfaceY(
        world: WorldState,
        wx: Int,
        wz: Int,
        requireNaturalGround: Boolean,
    ): Int? {
        val chunkX = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(wx, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(wz, WorldConstants.CHUNK_SIZE)
        // Only look at chunks that players have already discovered — never generate on the fly
        val chunk = world.getChunkIfDiscovered(ChunkPos(chunkX, chunkZ)) ?: return null
        val topY = chunk.topY()
        for (y in topY downTo WorldConstants.WORLD_MIN_Y) {
            if (!chunk.getBlock(localX, y, localZ).isSolid) continue
            if (requireNaturalGround && !world.isNaturalGround(wx, y, wz)) return null
            return y + 1
        }
        return null
    }
}
