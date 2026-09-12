package org.micoli.micraft.game.npc

import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.ZoneTier
import org.micoli.micraft.player.Vec3
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(QuestGiverSpawner::class.java)

/**
 * Keeps exactly one quest-giver NPC alive per zone cell (same grid as [NpcTickPipeline.zoneOf]) —
 * no manual placement. Content only needs to flag one roster NPC per tier with `behavior:
 * quest_giver` and a `minLevel`/`maxLevel` range; this picks the right one for each cell's
 * [ZoneTier] and auto-spawns/despawns to keep the count at exactly one.
 */
class QuestGiverSpawner {
    suspend fun trySpawn(
        world: WorldState,
        npcManager: NpcManager,
        definitions: Map<String, NpcDefinition>,
        loadedChunks: Collection<ChunkPos>,
        ctx: NpcTickContext = NpcTickContext.live,
    ) {
        if (loadedChunks.isEmpty()) return
        val zoneSize = ctx.tuning.npcZoneSize
        val questGiverDefs = definitions.filterValues { it.behaviorKey == "quest_giver" }
        if (questGiverDefs.isEmpty()) return

        fun cellOf(cp: ChunkPos) =
            Pair(
                Math.floorDiv(cp.cx * WorldConstants.CHUNK_SIZE, zoneSize),
                Math.floorDiv(cp.cz * WorldConstants.CHUNK_SIZE, zoneSize))

        // One representative loaded chunk per cell — the cell's geometric center can fall well
        // outside any chunk a player has actually discovered when npcZoneSize exceeds the view
        // distance, so spawn near a chunk that is actually loaded instead of the cell's center.
        val chunkByCell = loadedChunks.groupBy(::cellOf).mapValues { it.value.first() }

        val liveByCell = mutableMapOf<Pair<Int, Int>, MutableList<NpcInstance>>()
        for (instance in npcManager.getAll()) {
            if (instance.isDead || instance.definition.behaviorKey != "quest_giver") continue
            val cell =
                Pair(
                    Math.floorDiv(instance.state.pos.x.toInt(), zoneSize),
                    Math.floorDiv(instance.state.pos.z.toInt(), zoneSize))
            liveByCell.getOrPut(cell) { mutableListOf() }.add(instance)
        }

        for ((cell, chunkPos) in chunkByCell) {
            val existing = liveByCell[cell] ?: emptyList()
            if (existing.size > 1) {
                // Edge case: a tier shift or a killed-and-respawned giver left more than one.
                existing.drop(1).forEach { npcManager.despawnNpc(it.state.id) }
            }
            if (existing.isNotEmpty()) continue

            val chunk = world.getChunkIfDiscovered(chunkPos) ?: continue
            val wx = chunkPos.cx * WorldConstants.CHUNK_SIZE + WorldConstants.CHUNK_SIZE / 2
            val wz = chunkPos.cz * WorldConstants.CHUNK_SIZE + WorldConstants.CHUNK_SIZE / 2
            val zoneLevel = world.zoneLevelAt(wx, wz)
            val tier = ZoneTier.fromZoneLevel(zoneLevel)
            val candidate =
                questGiverDefs.entries.firstOrNull { (_, def) ->
                    zoneLevel in def.minLevel..def.maxLevel
                } ?: continue

            val localX = Math.floorMod(wx, WorldConstants.CHUNK_SIZE)
            val localZ = Math.floorMod(wz, WorldConstants.CHUNK_SIZE)
            var surfaceY: Int? = null
            for (y in chunk.topY() downTo WorldConstants.WORLD_MIN_Y) {
                if (chunk.getBlock(localX, y, localZ).isSolid) {
                    surfaceY = y + 1
                    break
                }
            }
            val y = surfaceY ?: continue
            val spawnPos = Vec3(wx + 0.5f, y.toFloat(), wz + 0.5f)
            npcManager.spawnNpc(candidate.key, candidate.key, spawnPos, tier.npcLevelRange.first)
            log.info(
                "Quest giver '{}' auto-spawned in zone cell {} (tier {})",
                candidate.key,
                cell,
                tier.tier)
        }
    }
}
