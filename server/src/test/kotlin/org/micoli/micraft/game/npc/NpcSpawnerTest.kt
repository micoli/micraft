package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.support.testWorld

private fun wanderDef(
    type: String = "GOAT",
    autoSpawn: Boolean = true,
    maxPerChunk: Int = 2,
    spawnBiomes: List<String> = emptyList(),
    maxTotal: Int = 0,
    minTotal: Int = 0,
): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = RandomMovableNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 2f,
        wanderRadius = 8f,
        spawn =
            NpcSpawnConfig(
                autoSpawn = autoSpawn,
                maxPerChunk = maxPerChunk,
                spawnBiomes = spawnBiomes,
                maxTotal = maxTotal,
                minTotal = minTotal,
            ),
    )

private fun staticDef(
    type: String = "SELLER",
    autoSpawn: Boolean = false,
): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = StaticNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
        spawn = NpcSpawnConfig(autoSpawn = autoSpawn),
    )

private fun testManager(defs: Map<String, NpcDefinition>): NpcManager {
    val m = NpcManager(broadcast = {})
    m.loadDefinitions(defs)
    return m
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
        val m = testManager(mapOf("GOAT" to wanderDef()))
        NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks())
        assertTrue(m.getAll().isNotEmpty())
    }

    @Test
    fun trySpawn_autoSpawnFalse_neverSpawns() = runBlocking {
        val world = solidFloorWorld()
        val m = testManager(mapOf("SELLER" to staticDef(autoSpawn = false)))
        repeat(5) { NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }
        assertTrue(m.getAll().isEmpty())
    }

    @Test
    fun trySpawn_respectsMaxPerChunk() = runBlocking {
        val world = solidFloorWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(maxPerChunk = 1)))
        val chunk = ChunkPos(0, 0)
        repeat(10) { NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }
        assertTrue(
            m.countByTypeInChunk("GOAT", chunk) <= 1,
            "Expected ≤1 GOAT in chunk, got ${m.countByTypeInChunk("GOAT", chunk)}",
        )
    }

    @Test
    fun trySpawn_respectsBiomeMaxNpcs() = runBlocking {
        val chunkSize = WorldConstants.CHUNK_SIZE
        val blocks = buildList {
            for (x in 0 until chunkSize * 5) for (z in 0 until chunkSize * 5) add(Triple(x, 3, z))
        }
        val testBiome =
            org.micoli.micraft.game.world.biome.BiomeDefinition(
                id = "plains",
                zones =
                    listOf(
                        org.micoli.micraft.game.world.biome.BiomeZone(
                            moistureMin = 0.0, moistureMax = 1.0)),
                surface = org.micoli.micraft.game.world.BlockType.GRASS,
                subsurface = org.micoli.micraft.game.world.BlockType.DIRT,
                maxNpcs = 2,
            )
        val baseWorld = org.micoli.micraft.support.testWorld(*blocks.toTypedArray())
        val worldWithBiome =
            org.micoli.micraft.game.world.WorldState(
                object :
                    org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator {
                    override fun generate(
                        pos: org.micoli.micraft.game.world.ChunkPos
                    ): org.micoli.micraft.game.world.Chunk = baseWorld.getOrGenerate(pos)

                    override fun biomeDefinitionAt(
                        wx: Int,
                        wz: Int,
                    ) = testBiome
                })
        blocks
            .map { (x, _, z) ->
                ChunkPos(
                    Math.floorDiv(x, WorldConstants.CHUNK_SIZE),
                    Math.floorDiv(z, WorldConstants.CHUNK_SIZE))
            }
            .toSet()
            .forEach { worldWithBiome.getOrGenerate(it) }

        val m = testManager(mapOf("GOAT" to wanderDef(maxPerChunk = 10)))
        repeat(20) {
            NpcSpawner()
                .trySpawn(worldWithBiome, m, m.getDefinitions(), worldWithBiome.discoveredChunks())
        }
        val zoneKey = m.zoneKey(chunkSize / 2f, chunkSize / 2f)
        assertTrue(
            m.countInZone(zoneKey) <= 2,
            "Expected ≤2 NPCs in zone, got ${m.countInZone(zoneKey)}",
        )
    }

    @Test
    fun trySpawn_emptyBiomeFilter_spawnsAnywhere() = runBlocking {
        val world = solidFloorWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(spawnBiomes = emptyList())))
        NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks())
        assertTrue(m.getAll().isNotEmpty())
    }

    @Test
    fun trySpawn_biomeFilterNoMatch_doesNotSpawn() = runBlocking {
        val world = solidFloorWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(spawnBiomes = listOf("nonexistent_biome"))))
        repeat(5) { NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }
        assertTrue(m.getAll().isEmpty())
    }

    @Test
    fun trySpawn_noDiscoveredChunks_doesNothing() = runBlocking {
        // Empty testWorld — no blocks pre-generated, so no discovered chunks
        val world = testWorld()
        val m = testManager(mapOf("GOAT" to wanderDef()))
        NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks())
        assertTrue(m.getAll().isEmpty())
    }

    // ── per-type quotas ───────────────────────────────────────────────────────

    @Test
    fun trySpawn_respectsMaxTotalAcrossChunks() = runBlocking {
        val world = solidFloorWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(maxTotal = 3, maxPerChunk = 10)))
        val spawner = NpcSpawner()

        // several passes: the ceiling has to hold over time, not only within one call
        repeat(10) { spawner.trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }

        assertTrue(m.countByType("GOAT") <= 3, "was ${m.countByType("GOAT")}")
    }

    @Test
    fun trySpawn_stopsAtMinTotalEvenWithChunkRoomLeft() = runBlocking {
        val world = solidFloorWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(minTotal = 2, maxPerChunk = 10)))
        val spawner = NpcSpawner()

        repeat(10) { spawner.trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }

        // the floor is a restocking target, not a budget to spend: above it, births take over
        assertTrue(m.countByType("GOAT") == 2, "was ${m.countByType("GOAT")}")
    }

    @Test
    fun trySpawn_restocksBackUpToTheFloor() = runBlocking {
        val world = solidFloorWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(minTotal = 3, maxPerChunk = 10)))
        val spawner = NpcSpawner()
        repeat(10) { spawner.trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }
        m.getAll().first().let { m.despawnNpc(it.state.id) }
        assertTrue(m.countByType("GOAT") == 2)

        repeat(10) { spawner.trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }

        assertTrue(m.countByType("GOAT") == 3, "the net must refill: ${m.countByType("GOAT")}")
    }

    @Test
    fun trySpawn_zeroQuotas_keepTheOldBehaviour() = runBlocking {
        val world = solidFloorWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(maxTotal = 0, minTotal = 0, maxPerChunk = 2)))
        val spawner = NpcSpawner()

        repeat(10) { spawner.trySpawn(world, m, m.getDefinitions(), world.discoveredChunks()) }

        // unquotaed, the spawner still fills up to the per-chunk cap as it always did
        assertTrue(m.countByType("GOAT") > 3, "was ${m.countByType("GOAT")}")
    }

    @Test
    fun countsByType_matchesCountByType_andIgnoresTheDead() = runBlocking {
        val world = solidFloorWorld()
        val m = testManager(mapOf("GOAT" to wanderDef(), "SELLER" to staticDef()))
        NpcSpawner().trySpawn(world, m, m.getDefinitions(), world.discoveredChunks())
        m.getAll().first().isDead = true

        val counts = m.countsByType()

        val living = m.getAll().count { it.state.type == "GOAT" && !it.isDead }
        assertTrue(counts["GOAT"] == living, "${counts["GOAT"]} vs $living")
        assertTrue(counts["GOAT"]!! < m.countByType("GOAT"), "the dead one must be excluded")
    }
}
