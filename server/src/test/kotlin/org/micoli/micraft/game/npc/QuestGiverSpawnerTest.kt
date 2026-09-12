package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.behaviors.QuestGiverNpcBehavior
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.support.testWorld

private fun questGiverDef(type: String = "hermit_man"): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = QuestGiverNpcBehavior(),
        behaviorKey = "quest_giver",
        bbmodelFile = "npc",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
        minLevel = 0,
        maxLevel = 10,
    )

private fun solidFloorWorld(): WorldState {
    val chunkSize = WorldConstants.CHUNK_SIZE
    val blocks = buildList {
        for (x in 0 until chunkSize * 3) for (z in 0 until chunkSize * 3) add(Triple(x, 3, z))
    }
    return testWorld(*blocks.toTypedArray())
}

/** Chunks 0..2 in both axes — matches the solid floor laid down in [solidFloorWorld]. */
private fun chunksAround(max: Int): List<ChunkPos> =
    (0..max).flatMap { cx -> (0..max).map { cz -> ChunkPos(cx, cz) } }

class QuestGiverSpawnerTest {
    private fun testManager(defs: Map<String, NpcDefinition>): NpcManager {
        val m = NpcManager(broadcast = {})
        m.loadDefinitions(defs)
        return m
    }

    @Test
    fun spawnsExactlyOnePerCell() = runBlocking {
        val world = solidFloorWorld()
        val manager = testManager(mapOf("hermit_man" to questGiverDef()))
        val spawner = QuestGiverSpawner()
        val chunks = chunksAround(2)

        spawner.trySpawn(world, manager, manager.getDefinitions(), chunks)
        spawner.trySpawn(world, manager, manager.getDefinitions(), chunks)

        assertEquals(1, manager.getAll().count { it.definition.behaviorKey == "quest_giver" })
    }

    @Test
    fun ignoresNpcsWithoutQuestGiverBehavior() = runBlocking {
        val world = solidFloorWorld()
        val manager = testManager(emptyMap())
        val spawner = QuestGiverSpawner()
        spawner.trySpawn(world, manager, manager.getDefinitions(), chunksAround(1))
        assertEquals(0, manager.getAll().size)
    }
}
