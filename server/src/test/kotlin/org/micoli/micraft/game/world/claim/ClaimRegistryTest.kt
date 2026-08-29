package org.micoli.micraft.game.world.claim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.ChunkPos

class ClaimRegistryTest {
    private fun registry() = ClaimRegistry(null)

    @Test
    fun create_thenClaimAtInsideChunkAndYRange_findsClaim() {
        val r = registry()
        val claim =
            r.create(
                chunks = setOf(ChunkPos(0, 0)),
                yMin = 0,
                yMax = 10,
                ownerId = "owner-id",
                ownerName = "Alice",
            )
        assertEquals(claim, r.claimAt(5, 5, 5))
    }

    @Test
    fun claimAt_yOutsideRange_returnsNull() {
        val r = registry()
        r.create(
            chunks = setOf(ChunkPos(0, 0)), yMin = 0, yMax = 10, ownerId = "o", ownerName = "Alice")
        assertNull(r.claimAt(5, 11, 5))
        assertNull(r.claimAt(5, -1, 5))
    }

    @Test
    fun claimAt_outsideSelectedChunk_returnsNull() {
        val r = registry()
        r.create(
            chunks = setOf(ChunkPos(0, 0)), yMin = 0, yMax = 10, ownerId = "o", ownerName = "Alice")
        // chunk (1,0) covers x in 16..31 — not part of the claim
        assertNull(r.claimAt(20, 5, 5))
    }

    @Test
    fun claimAt_nonContiguousChunkSet_bothChunksMatch() {
        val r = registry()
        val claim =
            r.create(
                chunks = setOf(ChunkPos(0, 0), ChunkPos(5, 5)),
                yMin = 0,
                yMax = 10,
                ownerId = "o",
                ownerName = "Alice",
            )
        assertEquals(claim, r.claimAt(5, 5, 5))
        assertEquals(claim, r.claimAt(5 * 16 + 3, 5, 5 * 16 + 3))
    }

    @Test
    fun overlaps_sameChunkOverlappingYRange_returnsTrue() {
        val r = registry()
        r.create(
            chunks = setOf(ChunkPos(0, 0)), yMin = 0, yMax = 10, ownerId = "o", ownerName = "Alice")
        assertTrue(r.overlaps(setOf(ChunkPos(0, 0)), 5, 15))
    }

    @Test
    fun overlaps_sameChunkDisjointYRange_returnsFalse() {
        val r = registry()
        r.create(
            chunks = setOf(ChunkPos(0, 0)), yMin = 0, yMax = 10, ownerId = "o", ownerName = "Alice")
        assertTrue(!r.overlaps(setOf(ChunkPos(0, 0)), 11, 20))
    }

    @Test
    fun overlaps_differentChunk_returnsFalse() {
        val r = registry()
        r.create(
            chunks = setOf(ChunkPos(0, 0)), yMin = 0, yMax = 10, ownerId = "o", ownerName = "Alice")
        assertTrue(!r.overlaps(setOf(ChunkPos(1, 0)), 0, 10))
    }

    @Test
    fun setTrusted_true_addsPlayerToClaim() {
        val r = registry()
        val claim =
            r.create(
                chunks = setOf(ChunkPos(0, 0)),
                yMin = 0,
                yMax = 10,
                ownerId = "o",
                ownerName = "Alice")
        val updated = r.setTrusted(claim.id, "friend-id", "Bob", trusted = true)
        assertEquals(setOf("friend-id"), updated?.trustedPlayerIds)
        assertEquals(setOf("Bob"), updated?.trustedPlayerNames)
    }

    @Test
    fun setTrusted_false_removesPlayerFromClaim() {
        val r = registry()
        val claim =
            r.create(
                chunks = setOf(ChunkPos(0, 0)),
                yMin = 0,
                yMax = 10,
                ownerId = "o",
                ownerName = "Alice")
        r.setTrusted(claim.id, "friend-id", "Bob", trusted = true)
        val updated = r.setTrusted(claim.id, "friend-id", "Bob", trusted = false)
        assertEquals(emptySet(), updated?.trustedPlayerIds)
    }

    @Test
    fun setTrusted_unknownId_returnsNull() {
        val r = registry()
        assertNull(r.setTrusted("nope", "friend-id", "Bob", trusted = true))
    }

    @Test
    fun delete_removesClaim_andClearsIndex() {
        val r = registry()
        val claim =
            r.create(
                chunks = setOf(ChunkPos(0, 0)),
                yMin = 0,
                yMax = 10,
                ownerId = "o",
                ownerName = "Alice")
        assertEquals(claim, r.delete(claim.id))
        assertNull(r.get(claim.id))
        assertNull(r.claimAt(5, 5, 5))
    }

    @Test
    fun delete_unknownId_returnsNull() {
        val r = registry()
        assertNull(r.delete("nope"))
    }

    @Test
    fun forOwnerOrTrusted_returnsOwnedAndTrustedClaimsOnly() {
        val r = registry()
        val ownedClaim =
            r.create(
                chunks = setOf(ChunkPos(0, 0)),
                yMin = 0,
                yMax = 10,
                ownerId = "o",
                ownerName = "Alice")
        val trustedClaim =
            r.create(
                chunks = setOf(ChunkPos(1, 1)),
                yMin = 0,
                yMax = 10,
                ownerId = "other",
                ownerName = "Bob")
        r.setTrusted(trustedClaim.id, "o", "Alice", trusted = true)
        r.create(
            chunks = setOf(ChunkPos(2, 2)),
            yMin = 0,
            yMax = 10,
            ownerId = "stranger",
            ownerName = "Carl")

        val result = r.forOwnerOrTrusted("o")
        assertEquals(setOf(ownedClaim.id, trustedClaim.id), result.map { it.id }.toSet())
    }
}
