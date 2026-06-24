package org.micoli.micraft.npc

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldState

private fun wanderDef(
    type: String = "GOAT",
    autoSpawn: Boolean = true,
    maxTotal: Int = 10,
    maxPerChunk: Int = 2,
    spawnBiomes: List<String> = emptyList(),
): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = RandomMovableNpcBehavior(),
        bbmodelFile = "npc.bbmodel",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 2f,
        wanderRadius = 8f,
        spawn =
            NpcSpawnConfig(
                autoSpawn = autoSpawn,
                maxTotal = maxTotal,
                maxPerChunk = maxPerChunk,
                spawnBiomes = spawnBiomes),
    )

private fun staticDef(
    type: String = "SELLER",
    autoSpawn: Boolean = false,
): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = StaticNpcBehavior(),
        bbmodelFile = "npc.bbmodel",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
        spawn = NpcSpawnConfig(autoSpawn = autoSpawn),
    )

private fun testManager(
    defs: Map<String, NpcDefinition>
): Pair<NpcManager, MutableList<ServerMessage>> {
    val broadcasts = mutableListOf<ServerMessage>()
    val m = NpcManager(broadcast = { broadcasts.add(it) })
    m.loadDefinitions(defs)
    return m to broadcasts
}

private fun solidFloorWorld(): WorldState {
    val chunkSize = WorldConstants.CHUNK_SIZE
    val blocks = buildList {
        for (x in 0 until chunkSize * 5) for (z in 0 until chunkSize * 5) add(Triple(x, 3, z))
    }
    return testWorld(*blocks.toTypedArray())
}

class NpcSpawnerTest {
    @Test
    fun trySpawn_autoSpawnTrue_spawnsNpc() = runBlocking {
        val world = solidFloorWorld()
        val (m, broadcasts) = testManager(mapOf("GOAT" to wanderDef()))
        NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks())
        assertTrue(broadcasts.any { it is ServerMessage.NpcSpawned })
    }

    @Test
    fun trySpawn_autoSpawnFalse_neverSpawns() = runBlocking {
        val world = solidFloorWorld()
        val (m, broadcasts) = testManager(mapOf("SELLER" to staticDef(autoSpawn = false)))
        repeat(5) { NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }
        assertTrue(broadcasts.none { it is ServerMessage.NpcSpawned })
    }

    @Test
    fun trySpawn_respectsMaxTotal() = runBlocking {
        val world = solidFloorWorld()
        val (m, _) = testManager(mapOf("GOAT" to wanderDef(maxTotal = 2, maxPerChunk = 10)))
        repeat(20) { NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }
        assertTrue(m.countByType("GOAT") <= 2, "Expected ≤2 GOATs, got ${m.countByType("GOAT")}")
    }

    @Test
    fun trySpawn_respectsMaxPerChunk() = runBlocking {
        val world = solidFloorWorld()
        val (m, _) = testManager(mapOf("GOAT" to wanderDef(maxTotal = 100, maxPerChunk = 1)))
        val chunk = ChunkPos(0, 0)
        repeat(10) { NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }
        assertTrue(
            m.countByTypeInChunk("GOAT", chunk) <= 1,
            "Expected ≤1 GOAT in chunk, got ${m.countByTypeInChunk("GOAT", chunk)}",
        )
    }

    @Test
    fun trySpawn_emptyBiomeFilter_spawnsAnywhere() = runBlocking {
        val world = solidFloorWorld()
        val (m, broadcasts) = testManager(mapOf("GOAT" to wanderDef(spawnBiomes = emptyList())))
        NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks())
        assertTrue(broadcasts.any { it is ServerMessage.NpcSpawned })
    }

    @Test
    fun trySpawn_biomeFilterNoMatch_doesNotSpawn() = runBlocking {
        val world = solidFloorWorld()
        val (m, broadcasts) =
            testManager(mapOf("GOAT" to wanderDef(spawnBiomes = listOf("nonexistent_biome"))))
        repeat(5) { NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }
        assertTrue(broadcasts.none { it is ServerMessage.NpcSpawned })
    }

    @Test
    fun trySpawn_noDiscoveredChunks_doesNothing() = runBlocking {
        // Empty testWorld — no blocks pre-generated, so no discovered chunks
        val world = testWorld()
        val (m, broadcasts) = testManager(mapOf("GOAT" to wanderDef()))
        NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks())
        assertTrue(broadcasts.none { it is ServerMessage.NpcSpawned })
    }
}
