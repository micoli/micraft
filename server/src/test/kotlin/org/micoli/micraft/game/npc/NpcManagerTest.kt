package org.micoli.micraft.game.npc

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.behaviors.InteractionableNpcBehavior
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

private fun testNpcManager(
    defs: Map<String, NpcDefinition> = emptyMap(),
    nearbySession: FakePlayerSession? = null,
): Pair<NpcManager, MutableList<ServerMessage>> {
    val broadcasts = mutableListOf<ServerMessage>()
    val sessions = if (nearbySession != null) listOf(nearbySession) else emptyList()
    val m = NpcManager(broadcast = { broadcasts.add(it) }, getSessions = { sessions })
    m.loadDefinitions(defs)
    return m to broadcasts
}

private fun staticDef(type: String = "SELLER"): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = StaticNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
    )

private fun wanderDef(type: String = "GOAT"): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = RandomMovableNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 3f,
        wanderRadius = 12f,
    )

private fun interactDef(type: String = "SELLER"): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = InteractionableNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
    )

class NpcManagerTest {
    @Test
    fun spawnNpc_sendsNpcSpawnedToNearbySession() = runBlocking {
        val session = testSession(pos = Vec3(8f, 5f, 8f))
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()), nearbySession = session)
        m.spawnNpc("Bob", "SELLER", Vec3(8f, 5f, 8f))
        assertTrue(session.sent.any { it is ServerMessage.NpcSpawned })
    }

    @Test
    fun spawnNpc_unknownType_throws() = runBlocking {
        val (m, _) = testNpcManager()
        assertFails { m.spawnNpc("X", "UNKNOWN", Vec3(0f, 0f, 0f)) }
        Unit
    }

    @Test
    fun spawnNpc_idIsUnique() = runBlocking {
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()))
        val a = m.spawnNpc("A", "SELLER", Vec3(0f, 0f, 0f))
        val b = m.spawnNpc("B", "SELLER", Vec3(0f, 0f, 0f))
        assertTrue(a.state.id != b.state.id)
    }

    @Test
    fun despawnNpc_sendsNpcDespawnedToKnownSession() = runBlocking {
        val session = testSession(pos = Vec3(0f, 5f, 0f))
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()), nearbySession = session)
        val instance = m.spawnNpc("Bob", "SELLER", Vec3(0f, 5f, 0f))
        session.sent.clear()
        m.despawnNpc(instance.state.id)
        assertTrue(session.sent.any { it is ServerMessage.NpcDespawned })
    }

    @Test
    fun despawnNpc_unknownId_noop() = runBlocking {
        val (m, broadcasts) = testNpcManager()
        m.despawnNpc("nonexistent")
        assertTrue(broadcasts.isEmpty())
    }

    @Test
    fun tick_staticNpc_grounded_doesNotSendUpdate() = runBlocking {
        val floorY = 4
        val world = testWorld(Triple(8, floorY, 8))
        val nearby = testSession(pos = Vec3(8.5f, 5f, 8.5f))
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()), nearbySession = nearby)
        val instance = m.spawnNpc("Bob", "SELLER", Vec3(8.5f, (floorY + 1).toFloat(), 8.5f))
        instance.vy = 0f
        nearby.sent.clear()
        m.tick(world)
        assertFalse(nearby.sent.any { it is ServerMessage.NpcUpdate })
    }

    @Test
    fun tick_gravityApplied_npcFalls() = runBlocking {
        val world = testWorld(Triple(8, 0, 8)) // pre-generates chunk (0,0) so NPC physics tick runs
        val nearby = testSession(pos = Vec3(8.5f, 50f, 8.5f))
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()), nearbySession = nearby)
        val instance = m.spawnNpc("Bob", "SELLER", Vec3(8.5f, 50f, 8.5f))
        val startY = instance.state.pos.y
        nearby.sent.clear()
        m.tick(world)
        assertTrue(instance.state.pos.y < startY, "Expected NPC to fall but y stayed at $startY")
        assertTrue(nearby.sent.any { it is ServerMessage.NpcUpdate })
    }

    @Test
    fun tick_npcLandsOnGround_stopsGravity() = runBlocking {
        val floorY = 4
        val world =
            testWorld(
                Triple(8, floorY, 8),
                Triple(9, floorY, 8),
                Triple(8, floorY, 9),
                Triple(9, floorY, 9),
            )
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()))
        val instance = m.spawnNpc("Bob", "SELLER", Vec3(8.5f, 10f, 8.5f))
        repeat(100) { m.tick(world) }
        assertTrue(instance.vy == 0f, "Expected vy=0 after landing, got ${instance.vy}")
        assertTrue(instance.state.pos.y <= (floorY + 2).toFloat())
    }

    @Test
    fun tick_wanderNpc_movesOverTime() = runBlocking {
        val floorY = 4
        val chunkSize = WorldConstants.CHUNK_SIZE
        val blocks = buildList {
            for (x in 0 until chunkSize * 3) for (z in 0 until chunkSize * 3) add(
                Triple(x, floorY, z))
        }
        val world = testWorld(*blocks.toTypedArray())
        val nearby = testSession(pos = Vec3(8.5f, 5f, 8.5f))
        val (m, _) = testNpcManager(mapOf("GOAT" to wanderDef()), nearbySession = nearby)
        val instance = m.spawnNpc("Billy", "GOAT", Vec3(8.5f, (floorY + 1).toFloat(), 8.5f))
        instance.vy = 0f
        instance.wanderStepTicks = 30
        instance.wanderTargetX = 20f
        instance.wanderTargetZ = 20f
        val startX = instance.state.pos.x
        val startZ = instance.state.pos.z
        repeat(40) { m.tick(world) }
        val moved =
            abs(instance.state.pos.x - startX) > 0.01f || abs(instance.state.pos.z - startZ) > 0.01f
        assertTrue(moved, "Expected wandering NPC to move from start position")
    }

    @Test
    fun tick_wanderNpc_jumpsOver1BlockObstacle() = runBlocking {
        val floorY = 4
        val chunkSize = WorldConstants.CHUNK_SIZE
        // Floor across 3 chunks
        val floor = buildList {
            for (x in 0 until chunkSize * 3) for (z in 0 until chunkSize * 3) add(
                Triple(x, floorY, z))
        }
        // 1-block wall at z=12 for a strip of x values
        val wall = buildList { for (x in 0 until chunkSize * 3) add(Triple(x, floorY + 1, 12)) }
        val world = testWorld(*(floor + wall).toTypedArray())
        val (m, _) = testNpcManager(mapOf("GOAT" to wanderDef()))
        val instance = m.spawnNpc("Billy", "GOAT", Vec3(8.5f, (floorY + 1).toFloat(), 8.5f))
        instance.vy = 0f
        instance.wanderStepTicks = 60
        instance.wanderTargetX = 8.5f
        instance.wanderTargetZ = 20f
        val startY = instance.state.pos.y
        var ticks = 0
        while (ticks < 200 && instance.vy <= 0f && instance.state.pos.y <= startY) {
            m.tick(world)
            ticks++
        }
        // NPC should have initiated a jump (vy set positive at some point), so y should have risen
        // above start
        assertTrue(
            instance.state.pos.y > startY || instance.vy > 0f,
            "Expected NPC to jump over 1-block wall")
    }

    @Test
    fun tick_wanderNpc_doesNotJumpOver2BlockObstacle() = runBlocking {
        val floorY = 4
        val chunkSize = WorldConstants.CHUNK_SIZE
        val floor = buildList {
            for (x in 0 until chunkSize * 3) for (z in 0 until chunkSize * 3) add(
                Triple(x, floorY, z))
        }
        // 2-block wall at z=12 — too high to jump
        val wall = buildList {
            for (x in 0 until chunkSize * 3) {
                add(Triple(x, floorY + 1, 12))
                add(Triple(x, floorY + 2, 12))
            }
        }
        val world = testWorld(*(floor + wall).toTypedArray())
        val (m, _) = testNpcManager(mapOf("GOAT" to wanderDef()))
        val instance = m.spawnNpc("Billy", "GOAT", Vec3(8.5f, (floorY + 1).toFloat(), 8.5f))
        instance.vy = 0f
        instance.wanderStepTicks = 60
        instance.wanderTargetX = 8.5f
        instance.wanderTargetZ = 20f
        repeat(5) { m.tick(world) }
        // NPC should pick a new target rather than jumping — vy stays 0
        assertTrue(instance.vy == 0f, "NPC should not jump over a 2-block wall")
    }

    @Test
    fun tick_npcUpdate_positionRoundedTo2Decimals() = runBlocking {
        val world = testWorld()
        val nearby = testSession(pos = Vec3(8.5f, 50f, 8.5f))
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()), nearbySession = nearby)
        m.spawnNpc("Bob", "SELLER", Vec3(8.5f, 50f, 8.5f))
        nearby.sent.clear()
        m.tick(world)
        val update = nearby.sent.filterIsInstance<ServerMessage.NpcUpdate>().firstOrNull()
        if (update != null) {
            val pos = update.npc.pos
            assertEquals(Math.round(pos.x * 100) / 100f, pos.x)
            assertEquals(Math.round(pos.y * 100) / 100f, pos.y)
            assertEquals(Math.round(pos.z * 100) / 100f, pos.z)
        }
    }

    @Test
    fun tick_npcUpdate_notSentWhenRoundedPositionUnchanged() = runBlocking {
        val world = testWorld()
        val nearby = testSession(pos = Vec3(8.5f, 50f, 8.5f))
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()), nearbySession = nearby)
        val instance = m.spawnNpc("Bob", "SELLER", Vec3(8.5f, 50f, 8.5f))
        nearby.sent.clear()
        m.tick(world)
        val countAfterFirstTick = nearby.sent.filterIsInstance<ServerMessage.NpcUpdate>().size
        nearby.sent.clear()
        // Force a tiny sub-centimeter change that should not produce a new update
        instance.state =
            instance.state.copy(pos = instance.state.pos.copy(y = instance.state.pos.y + 0.001f))
        m.tick(world)
        val countAfterSecondTick = nearby.sent.filterIsInstance<ServerMessage.NpcUpdate>().size
        assertTrue(
            countAfterSecondTick <= countAfterFirstTick,
            "Sub-centimeter change should not produce extra NpcUpdate")
    }

    @Test
    fun tick_npcUpdate_notSentToDistantSession() = runBlocking {
        val world = testWorld()
        val far = testSession(pos = Vec3(500f, 50f, 500f))
        val m = NpcManager(broadcast = {}, getSessions = { listOf(far) })
        m.loadDefinitions(mapOf("SELLER" to staticDef()))
        m.spawnNpc("Bob", "SELLER", Vec3(8.5f, 50f, 8.5f))
        far.sent.clear()
        m.tick(world)
        assertFalse(far.sent.any { it is ServerMessage.NpcUpdate })
    }

    @Test
    fun handleInteract_interactionable_inRange_sendsResult() = runBlocking {
        val m = NpcManager(broadcast = {})
        m.loadDefinitions(mapOf("SELLER" to interactDef()))
        val instance = m.spawnNpc("Bob", "SELLER", Vec3(8f, 5f, 8f))
        val session = testSession(pos = Vec3(8f, 5f, 9f))
        m.handleInteract(session, instance.state.id)
        assertTrue(session.sent.any { it is ServerMessage.NpcInteractResult })
    }

    @Test
    fun handleInteract_outOfRange_noResult() = runBlocking {
        val (m, _) = testNpcManager(mapOf("SELLER" to interactDef()))
        val instance = m.spawnNpc("Bob", "SELLER", Vec3(8f, 5f, 8f))
        val session = testSession(pos = Vec3(8f, 5f, 20f))
        m.handleInteract(session, instance.state.id)
        assertFalse(session.sent.any { it is ServerMessage.NpcInteractResult })
    }

    @Test
    fun handleInteract_staticBehavior_noResult() = runBlocking {
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()))
        val instance = m.spawnNpc("Bob", "SELLER", Vec3(8f, 5f, 8f))
        val session = testSession(pos = Vec3(8f, 5f, 9f))
        m.handleInteract(session, instance.state.id)
        assertFalse(session.sent.any { it is ServerMessage.NpcInteractResult })
    }

    @Test
    fun sendAllTo_sendsAllSpawnedNpcs() = runBlocking {
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef(), "GOAT" to wanderDef()))
        m.spawnNpc("A", "SELLER", Vec3(0f, 0f, 0f))
        m.spawnNpc("B", "GOAT", Vec3(0f, 0f, 0f))
        m.spawnNpc("C", "SELLER", Vec3(0f, 0f, 0f))
        val session = testSession()
        m.sendAllTo(session)
        assertEquals(3, session.sent.filterIsInstance<ServerMessage.NpcSpawned>().size)
    }

    @Test
    fun countByType_returnsCorrectCount() = runBlocking {
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef(), "GOAT" to wanderDef()))
        m.spawnNpc("A", "SELLER", Vec3(0f, 0f, 0f))
        m.spawnNpc("B", "GOAT", Vec3(0f, 0f, 0f))
        m.spawnNpc("C", "SELLER", Vec3(0f, 0f, 0f))
        assertEquals(2, m.countByType("SELLER"))
        assertEquals(1, m.countByType("GOAT"))
        assertEquals(0, m.countByType("DUCK"))
    }

    @Test
    fun countByTypeInChunk_returnsCorrectCount() = runBlocking {
        val (m, _) = testNpcManager(mapOf("SELLER" to staticDef()))
        m.spawnNpc("A", "SELLER", Vec3(4f, 5f, 4f))
        m.spawnNpc("B", "SELLER", Vec3(4f, 5f, 4f))
        m.spawnNpc("C", "SELLER", Vec3(20f, 5f, 20f))
        assertEquals(2, m.countByTypeInChunk("SELLER", ChunkPos(0, 0)))
        assertEquals(1, m.countByTypeInChunk("SELLER", ChunkPos(1, 1)))
    }
}
