package org.micoli.micraft.game.world.claim

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.ChunkPos

private fun claim(ownerId: String = "owner-id", trustedPlayerIds: Set<String> = emptySet()) =
    Claim(
        id = "claim-1",
        chunks = setOf(ChunkPos(0, 0)),
        yMin = 0,
        yMax = 10,
        ownerId = ownerId,
        ownerName = "Alice",
        createdAt = 0L,
        trustedPlayerIds = trustedPlayerIds,
    )

class ClaimTest {
    @Test
    fun contains_insideChunkAndYRange_returnsTrue() {
        val c = claim()
        assertTrue(c.contains(5, 5, 5))
    }

    @Test
    fun contains_outsideYRange_returnsFalse() {
        val c = claim()
        assertFalse(c.contains(5, 20, 5))
    }
}
