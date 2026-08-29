package org.micoli.micraft.game.world.claim

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldPersistence

class ClaimRegistry(private val persistence: WorldPersistence?) {
    private val claims = ConcurrentHashMap<String, Claim>()

    // Reverse index: chunk column → claims covering it (almost always a single entry) — turns
    // claimAt() from an O(n_claims) scan into an O(1) lookup, same rationale as
    // InstanceRegistry.byChunk since this now runs once per block break/place/interact.
    private val byChunk = ConcurrentHashMap<ChunkPos, MutableList<Claim>>()

    init {
        persistence?.loadClaims()?.forEach { claim ->
            claims[claim.id] = claim
            index(claim)
        }
    }

    fun all(): List<Claim> = claims.values.sortedBy { it.createdAt }

    fun get(id: String): Claim? = claims[id]

    fun forOwnerOrTrusted(playerId: String): List<Claim> =
        all().filter { it.ownerId == playerId || playerId in it.trustedPlayerIds }

    fun claimAt(x: Int, y: Int, z: Int): Claim? {
        val chunkPos =
            ChunkPos(
                Math.floorDiv(x, WorldConstants.CHUNK_SIZE),
                Math.floorDiv(z, WorldConstants.CHUNK_SIZE))
        return byChunk[chunkPos]?.firstOrNull { y in it.yMin..it.yMax }
    }

    /**
     * True if [chunks]/[yMin]/[yMax] would share a chunk column and a Y range with any claim other
     * than [excludeId] — pass the claim's own id when checking a resize/move so it doesn't
     * spuriously overlap itself.
     */
    fun overlaps(chunks: Set<ChunkPos>, yMin: Int, yMax: Int, excludeId: String? = null): Boolean =
        chunks.any { chunk ->
            byChunk[chunk]?.any { claim ->
                claim.id != excludeId && yMin <= claim.yMax && yMax >= claim.yMin
            } ?: false
        }

    fun create(
        chunks: Set<ChunkPos>,
        yMin: Int,
        yMax: Int,
        ownerId: String,
        ownerName: String,
    ): Claim {
        val claim =
            Claim(
                id = UUID.randomUUID().toString(),
                chunks = chunks,
                yMin = yMin,
                yMax = yMax,
                ownerId = ownerId,
                ownerName = ownerName,
                createdAt = System.currentTimeMillis())
        claims[claim.id] = claim
        index(claim)
        persist()
        return claim
    }

    fun updateBounds(id: String, yMin: Int, yMax: Int): Claim? {
        val existing = claims[id] ?: return null
        val updated = existing.copy(yMin = yMin, yMax = yMax)
        claims[id] = updated
        reindex(existing, updated)
        persist()
        return updated
    }

    fun updateChunks(id: String, chunks: Set<ChunkPos>): Claim? {
        val existing = claims[id] ?: return null
        val updated = existing.copy(chunks = chunks)
        claims[id] = updated
        reindex(existing, updated)
        persist()
        return updated
    }

    fun reassignOwner(id: String, ownerId: String, ownerName: String): Claim? {
        val existing = claims[id] ?: return null
        val updated = existing.copy(ownerId = ownerId, ownerName = ownerName)
        claims[id] = updated
        persist()
        return updated
    }

    fun setTrusted(id: String, playerId: String, playerName: String, trusted: Boolean): Claim? {
        val existing = claims[id] ?: return null
        val updated =
            if (trusted)
                existing.copy(
                    trustedPlayerIds = existing.trustedPlayerIds + playerId,
                    trustedPlayerNames = existing.trustedPlayerNames + playerName)
            else
                existing.copy(
                    trustedPlayerIds = existing.trustedPlayerIds - playerId,
                    trustedPlayerNames = existing.trustedPlayerNames - playerName)
        claims[id] = updated
        persist()
        return updated
    }

    fun delete(id: String): Claim? {
        val removed = claims.remove(id) ?: return null
        unindex(removed)
        persist()
        return removed
    }

    private fun index(claim: Claim) {
        claim.chunks.forEach { byChunk.getOrPut(it) { mutableListOf() }.add(claim) }
    }

    private fun unindex(claim: Claim) {
        claim.chunks.forEach { byChunk[it]?.removeAll { c -> c.id == claim.id } }
    }

    private fun reindex(old: Claim, updated: Claim) {
        unindex(old)
        index(updated)
    }

    private fun persist() {
        persistence?.saveClaims(all())
    }
}
