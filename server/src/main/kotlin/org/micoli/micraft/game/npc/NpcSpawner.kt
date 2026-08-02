package org.micoli.micraft.game.npc

import kotlin.collections.iterator
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.Vec3
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(NpcSpawner::class.java)

class NpcSpawner {

    suspend fun trySpawn(
        world: WorldState,
        npcManager: NpcManager,
        definitions: Map<String, NpcDefinition>,
        loadedChunks: Collection<ChunkPos>,
        ctx: NpcTickContext = NpcTickContext.live,
        canSpawn: () -> Boolean = { true },
    ) {
        if (loadedChunks.isEmpty()) return
        val chunkList = loadedChunks.toList().shuffled(ctx.random)

        // One pass for every type, then kept up to date locally: the per-type quotas below would
        // otherwise rescan the whole NPC map for each type on each chunk attempt.
        val counts = npcManager.countsByType().toMutableMap()

        for ((type, def) in definitions) {
            val spawn = def.spawn
            if (!spawn.autoSpawn) continue

            val alive = counts[type] ?: 0
            if (spawn.maxTotal > 0 && alive >= spawn.maxTotal) continue
            // A floor turns the spawner into a restocker: above it, new life has to be born.
            // Without
            // one it keeps filling whatever room the chunks leave, which is how spawning came to
            // outweigh reproduction almost four to one.
            if (spawn.minTotal > 0 && alive >= spawn.minTotal) continue

            var attempts = 0
            for (chunkPos in chunkList) {
                if (attempts >= ctx.tuning.maxSpawnAttemptsPerTick) break
                if (!canSpawn()) return
                if (npcManager.countByTypeInChunk(type, chunkPos) >= spawn.maxPerChunk) continue

                val wx =
                    chunkPos.cx * WorldConstants.CHUNK_SIZE +
                        ctx.random.nextInt(WorldConstants.CHUNK_SIZE)
                val wz =
                    chunkPos.cz * WorldConstants.CHUNK_SIZE +
                        ctx.random.nextInt(WorldConstants.CHUNK_SIZE)

                val biomeDef = world.biomeDefinitionAt(wx, wz)
                if (spawn.spawnBiomes.isNotEmpty() && biomeDef?.id !in spawn.spawnBiomes) continue

                val maxNpcs = biomeDef?.maxNpcs ?: 0
                if (maxNpcs > 0) {
                    val zk = npcManager.zoneKey(wx.toFloat(), wz.toFloat())
                    if (npcManager.countInZone(zk) >= maxNpcs) continue
                }

                val surfaceY = findSurfaceY(world, wx, wz) ?: continue
                val spawnPos = Vec3(wx + 0.5f, surfaceY.toFloat(), wz + 0.5f)

                val solid = { bx: Int, by: Int, bz: Int ->
                    world.getBlockIfLoaded(bx, by, bz).isSolid
                }
                val clearX =
                    AabbCollider.resolveX(
                        solid, spawnPos.x, spawnPos.y, spawnPos.z, def.width, def.height, 0f)
                val clearZ =
                    AabbCollider.resolveZ(
                        solid, spawnPos.x, spawnPos.y, spawnPos.z, def.width, def.height, 0f)
                if (clearX != 0f || clearZ != 0f) continue

                val zoneLevel = world.zoneLevelAt(wx, wz)
                if (zoneLevel < def.minLevel || zoneLevel > def.maxLevel) continue
                val instanceLevel =
                    (zoneLevel + ctx.random.nextInt(-3, 4)).coerceIn(
                        1, WorldConstants.RPG_LEVEL_MAX)
                val name =
                    "${type.lowercase().replaceFirstChar { it.uppercase() }.replace('_',' ')} - ${FantasyNameGenerator.generate(type)}"
                // the animal record comes with the spawn now — see NpcManager.spawnNpc
                npcManager.spawnNpc(name, type, spawnPos, instanceLevel)
                val nowAlive = (counts[type] ?: 0) + 1
                counts[type] = nowAlive
                log.debug("Auto-spawned {} at ({},{},{})", type, wx, surfaceY, wz)
                attempts++
                // re-checked inside the loop: several chunks are tried per type per pass, and the
                // quota must hold across them, not only on entry
                if (spawn.maxTotal > 0 && nowAlive >= spawn.maxTotal) break
                if (spawn.minTotal > 0 && nowAlive >= spawn.minTotal) break
            }
        }
    }

    private fun findSurfaceY(world: WorldState, wx: Int, wz: Int): Int? {
        val chunkX = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(wx, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(wz, WorldConstants.CHUNK_SIZE)
        // Only look at chunks that players have already discovered — never generate on the fly
        val chunk = world.getChunkIfDiscovered(ChunkPos(chunkX, chunkZ)) ?: return null
        val topY = chunk.topY()
        for (y in topY downTo WorldConstants.WORLD_MIN_Y) {
            if (chunk.getBlock(localX, y, localZ).isSolid) return y + 1
        }
        return null
    }
}
