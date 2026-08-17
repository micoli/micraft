package org.micoli.micraft.game.world.instance

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldPersistence

class InstanceRegistry(private val persistence: WorldPersistence?) {
    private val zones = ConcurrentHashMap<String, InstanceZone>()

    // Reverse index: chunk column → zones covering it (almost always a single entry) — turns
    // zoneAt() from an O(n_zones) scan into an O(1) lookup, which matters once there are many
    // zones since it now runs once per admin session per tick.
    private val byChunk = ConcurrentHashMap<ChunkPos, MutableList<InstanceZone>>()

    init {
        persistence?.loadInstances()?.forEach { zone ->
            zones[zone.id] = zone
            index(zone)
        }
    }

    fun all(): List<InstanceZone> = zones.values.sortedBy { it.createdAt }

    fun get(id: String): InstanceZone? = zones[id]

    // A disabled zone is fully inactive: BlockPlacer/BlockBreaker stop protecting its footprint,
    // letting normal players build there again, same as if the zone didn't exist.
    fun zoneAt(x: Int, y: Int, z: Int): InstanceZone? {
        val chunkPos =
            ChunkPos(
                Math.floorDiv(x, WorldConstants.CHUNK_SIZE),
                Math.floorDiv(z, WorldConstants.CHUNK_SIZE))
        return byChunk[chunkPos]?.firstOrNull { it.enabled && y in it.yMin..it.yMax }
    }

    // True if [chunks]/[yMin]/[yMax] would share a chunk column and a Y range with any existing
    // zone other than [excludeId] — used to reject overlapping create/updateBounds/updateChunks
    // requests before they hit the registry.
    fun overlaps(chunks: Set<ChunkPos>, yMin: Int, yMax: Int, excludeId: String? = null): Boolean =
        chunks.any { chunk ->
            byChunk[chunk]?.any { zone ->
                zone.id != excludeId && yMin <= zone.yMax && yMax >= zone.yMin
            } ?: false
        }

    fun create(
        name: String,
        yMin: Int,
        yMax: Int,
        chunks: Set<ChunkPos>,
        ownerName: String
    ): InstanceZone {
        val zone =
            InstanceZone(
                id = UUID.randomUUID().toString(),
                name = name,
                yMin = yMin,
                yMax = yMax,
                chunks = chunks,
                ownerName = ownerName,
                createdAt = System.currentTimeMillis())
        zones[zone.id] = zone
        index(zone)
        persist()
        return zone
    }

    fun rename(id: String, name: String): InstanceZone? {
        val existing = zones[id] ?: return null
        val updated = existing.copy(name = name)
        zones[id] = updated
        reindex(existing, updated)
        persist()
        return updated
    }

    fun updateBounds(id: String, yMin: Int, yMax: Int): InstanceZone? {
        val existing = zones[id] ?: return null
        val updated = existing.copy(yMin = yMin, yMax = yMax)
        zones[id] = updated
        reindex(existing, updated)
        persist()
        return updated
    }

    fun setEnabled(id: String, enabled: Boolean): InstanceZone? {
        val existing = zones[id] ?: return null
        val updated = existing.copy(enabled = enabled)
        zones[id] = updated
        reindex(existing, updated)
        persist()
        return updated
    }

    fun updateChunks(id: String, chunks: Set<ChunkPos>): InstanceZone? {
        val existing = zones[id] ?: return null
        val updated = existing.copy(chunks = chunks)
        zones[id] = updated
        reindex(existing, updated)
        persist()
        return updated
    }

    fun updateLayout(
        id: String,
        clipPlanes: InstanceClipPlanes,
        shortcutBarPages: List<List<String?>>
    ): InstanceZone? {
        val existing = zones[id] ?: return null
        val updated = existing.copy(clipPlanes = clipPlanes, shortcutBarPages = shortcutBarPages)
        zones[id] = updated
        // Footprint is unchanged, but byChunk still holds the old object reference — refresh it so
        // zoneAt() returns the updated zone, same as rename()/setEnabled() do.
        reindex(existing, updated)
        persist()
        return updated
    }

    fun delete(id: String): Boolean {
        val removed = zones.remove(id) ?: return false
        unindex(removed)
        persist()
        return true
    }

    private fun index(zone: InstanceZone) {
        zone.chunks.forEach { byChunk.getOrPut(it) { mutableListOf() }.add(zone) }
    }

    private fun unindex(zone: InstanceZone) {
        zone.chunks.forEach { byChunk[it]?.removeAll { z -> z.id == zone.id } }
    }

    private fun reindex(old: InstanceZone, updated: InstanceZone) {
        unindex(old)
        index(updated)
    }

    private fun persist() {
        persistence?.saveInstances(all())
    }
}
