package org.micoli.micraft.game.world.claim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class ClaimManagerTest {
    private fun manager(
        sessions: List<PlayerSession>,
        registry: ClaimRegistry = ClaimRegistry(null),
        config: ClaimConfig = ClaimConfig(),
    ) =
        ClaimManager(
            registry = registry,
            config = config,
            getSessions = { sessions },
            i18n = testI18n(),
            savePlayer = {},
        )

    @Test
    fun createClaim_sendsClaimSyncContainingTheNewClaim() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        alice.state = alice.state.copy(wallet = 1000L)
        val mgr = manager(listOf(alice))

        mgr.createClaim(alice, BlockPos(0, 0, 0), BlockPos(1, 0, 1))

        val sync = alice.sent.filterIsInstance<ServerMessage.ClaimSync>().lastOrNull()
        assertTrue(sync != null, "expected a ClaimSync to be sent")
        assertEquals(1, sync!!.claims.size)
        assertEquals("alice-id", sync.claims[0].ownerId)
        assertTrue(alice.sent.none { it is ServerMessage.ClaimDenied })
    }
}
