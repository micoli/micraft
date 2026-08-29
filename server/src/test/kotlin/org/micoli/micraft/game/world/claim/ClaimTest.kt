package org.micoli.micraft.game.world.claim

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.support.FakeWebSocketSession
import org.micoli.micraft.support.testPlayerState

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

private fun sessionWithPermissions(
    id: String,
    permissions: Set<String> = emptySet()
): PlayerSession =
    PlayerSession(
        id,
        id,
        FakeWebSocketSession(),
        testPlayerState(id = id),
        permissions = permissions,
    )

class ClaimTest {
    @Test
    fun canEdit_owner_returnsTrue() {
        val c = claim(ownerId = "owner-id")
        assertTrue(c.canEdit(sessionWithPermissions("owner-id")))
    }

    @Test
    fun canEdit_trustedPlayer_returnsTrue() {
        val c = claim(ownerId = "owner-id", trustedPlayerIds = setOf("friend-id"))
        assertTrue(c.canEdit(sessionWithPermissions("friend-id")))
    }

    @Test
    fun canEdit_stranger_returnsFalse() {
        val c = claim(ownerId = "owner-id")
        assertFalse(c.canEdit(sessionWithPermissions("stranger-id")))
    }

    @Test
    fun canEdit_admin_returnsTrue() {
        val c = claim(ownerId = "owner-id")
        assertTrue(c.canEdit(sessionWithPermissions("admin-id", permissions = setOf("*"))))
    }

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
