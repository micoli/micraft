package org.micoli.micraft.game.world.claim

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testPlayerState
import org.micoli.micraft.support.testSession

class ClaimManagerTest {
    private fun manager(
        sessions: List<PlayerSession>,
        registry: ClaimRegistry = ClaimRegistry(null),
        config: ClaimConfig = ClaimConfig(),
        persistence: WorldPersistence? = null,
    ) =
        ClaimManager(
            registry = registry,
            config = config,
            getSessions = { sessions },
            i18n = testI18n(),
            savePlayer = {},
            persistence = persistence,
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

    @Test
    fun setTrusted_onlinePlayer_addsToClaimAndSyncsBothSessions() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        val bob = testSession(id = "bob-id", name = "Bob")
        val registry = ClaimRegistry(null)
        val claim = registry.create(setOf(), 0, 10, "alice-id", "Alice")
        val mgr = manager(listOf(alice, bob), registry = registry)

        mgr.setTrusted(alice, claim.id, "Bob", trusted = true)

        assertEquals(setOf("bob-id"), registry.get(claim.id)?.trustedPlayerIds)
        assertTrue(bob.sent.any { it is ServerMessage.ClaimSync })
        assertTrue(alice.sent.none { it is ServerMessage.ClaimDenied })
    }

    @Test
    fun setTrusted_offlinePlayer_resolvesViaPersistedPlayerState() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        val persistence = WorldPersistence(Files.createTempDirectory("claim-manager-test"))
        persistence.savePlayerState("Bob", testPlayerState(id = "bob-id", name = "Bob"))
        val registry = ClaimRegistry(null)
        val claim = registry.create(setOf(), 0, 10, "alice-id", "Alice")
        val mgr = manager(listOf(alice), registry = registry, persistence = persistence)

        mgr.setTrusted(alice, claim.id, "Bob", trusted = true)

        assertEquals(setOf("bob-id"), registry.get(claim.id)?.trustedPlayerIds)
        assertTrue(alice.sent.none { it is ServerMessage.ClaimDenied })
    }

    @Test
    fun setTrusted_unknownPlayer_sendsClaimDenied() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        val registry = ClaimRegistry(null)
        val claim = registry.create(setOf(), 0, 10, "alice-id", "Alice")
        val mgr = manager(listOf(alice), registry = registry)

        mgr.setTrusted(alice, claim.id, "Ghost", trusted = true)

        assertTrue(alice.sent.any { it is ServerMessage.ClaimDenied })
        assertEquals(emptySet(), registry.get(claim.id)?.trustedPlayerIds)
    }
}
