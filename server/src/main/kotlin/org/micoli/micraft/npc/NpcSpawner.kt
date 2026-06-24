package org.micoli.micraft.npc

import kotlin.random.Random
import org.micoli.micraft.physics.AabbCollider
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldState
import org.micoli.micraft.world.isSolid
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(NpcSpawner::class.java)

class NpcSpawner {

    suspend fun trySpawn(
        world: WorldState,
        npcManager: NpcManager,
        definitions: Map<String, NpcDefinition>,
        loadedChunks: Collection<ChunkPos>,
    ) {
        if (loadedChunks.isEmpty()) return
        val chunkList = loadedChunks.toList().shuffled()

        for ((type, def) in definitions) {
            val spawn = def.spawn
            if (!spawn.autoSpawn) continue
            if (spawn.maxTotal > 0 && npcManager.countByType(type) >= spawn.maxTotal) continue

            var attempts = 0
            for (chunkPos in chunkList) {
                if (attempts >= NpcConstants.MAX_SPAWN_ATTEMPTS_PER_TICK) break
                if (npcManager.countByTypeInChunk(type, chunkPos) >= spawn.maxPerChunk) continue

                val wx =
                    chunkPos.cx * WorldConstants.CHUNK_SIZE +
                        Random.nextInt(WorldConstants.CHUNK_SIZE)
                val wz =
                    chunkPos.cz * WorldConstants.CHUNK_SIZE +
                        Random.nextInt(WorldConstants.CHUNK_SIZE)

                if (spawn.spawnBiomes.isNotEmpty()) {
                    val biome = world.biomeAt(wx, wz)
                    if (biome !in spawn.spawnBiomes) continue
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

                val name =
                    "${type.lowercase().replaceFirstChar { it.uppercase() }} #${java.util.UUID.randomUUID().toString().take(4)}"
                npcManager.spawnNpc(name, type, spawnPos)
                log.debug("Auto-spawned {} at ({},{},{})", type, wx, surfaceY, wz)
                attempts++
                if (spawn.maxTotal > 0 && npcManager.countByType(type) >= spawn.maxTotal) break
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
