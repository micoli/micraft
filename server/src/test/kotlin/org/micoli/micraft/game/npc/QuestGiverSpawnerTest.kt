package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.behaviors.QuestGiverNpcBehavior
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.npc.roster.RegionPopulation
import org.micoli.micraft.game.quest.KillObjective
import org.micoli.micraft.game.quest.QuestDefinition
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.FlatArenaChunkGenerator
import org.micoli.micraft.player.Vec3

private fun questGiverDef(
    type: String = "hermit_man",
    biomes: List<String> = emptyList(),
    maxLevel: Int = Int.MAX_VALUE,
): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = QuestGiverNpcBehavior(),
        behaviorKey = "quest_giver",
        bbmodelFile = "npc",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 0f,
        wanderRadius = 0f,
        maxLevel = maxLevel,
        spawn = NpcSpawnConfig(spawnBiomes = biomes),
    )

private val deer =
    NpcDefinition(
        type = "deer",
        behavior = RandomMovableNpcBehavior(),
        bbmodelFile = "npc",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 2f,
        wanderRadius = 8f,
        spawn = NpcSpawnConfig(autoSpawn = true),
    )

private fun killQuest(id: String, level: Int, target: String) =
    QuestDefinition(
        id = id,
        title = id,
        description = "",
        type = QuestType.KILL,
        level = level,
        objectives = listOf(KillObjective(target, 1)))

/** A plains arena at Danger level 3: one Region whose Roster holds `deer`. */
private fun arena(): WorldState =
    WorldState(
            FlatArenaChunkGenerator(
                halfSize = 40, regionBudget = 10, zoneLevel = 3, vegetationDensity = 0.0))
        .also { world -> chunks.forEach { world.getOrGenerate(it) } }

private val chunks: List<ChunkPos> =
    (-1..1).flatMap { cx -> (-1..1).map { cz -> ChunkPos(cx, cz) } }

private fun manager(
    givers: List<NpcDefinition>,
    quests: List<QuestDefinition> = listOf(killQuest("deer_hunt", 2, "deer")),
): NpcManager {
    val questManager =
        QuestManager(getSessions = { emptyList() }, savePlayer = {}).also { qm ->
            qm.reloadDefinitions(quests.associateBy { it.id })
        }
    return NpcManager(broadcast = {}, getQuestManager = { questManager }).also { m ->
        m.loadDefinitions((givers + deer).associateBy { it.type })
    }
}

private fun giverSpawner(world: WorldState, m: NpcManager) =
    QuestGiverSpawner(RegionPopulation(world) { m.getDefinitions() })

private fun NpcManager.givers() = getAll().filter { it.definition.behaviorKey == "quest_giver" }

class QuestGiverSpawnerTest {
    @Test
    fun spawnsExactlyOnePerRegionWithTheSuitedQuests() = runBlocking {
        val world = arena()
        val m = manager(listOf(questGiverDef()))
        val spawner = giverSpawner(world, m)

        spawner.trySpawn(world, m, m.getDefinitions(), chunks)
        spawner.trySpawn(world, m, m.getDefinitions(), chunks)

        assertEquals(listOf("deer_hunt"), m.givers().single().offeredQuests)
    }

    @Test
    fun noGiverWhereNoQuestFitsTheRegion() = runBlocking {
        val world = arena()
        val m =
            manager(
                listOf(questGiverDef()),
                listOf(killQuest("eel_hunt", 2, "eel"), killQuest("deer_cull", 14, "deer")))

        giverSpawner(world, m).trySpawn(world, m, m.getDefinitions(), chunks)

        assertTrue(m.givers().isEmpty())
    }

    @Test
    fun extraGiversInARegionAreRemoved() = runBlocking {
        val world = arena()
        val m = manager(listOf(questGiverDef()))
        repeat(3) { m.spawnNpc("Hermit$it", "hermit_man", Vec3(it.toFloat(), 8f, 0f)) }

        giverSpawner(world, m).trySpawn(world, m, m.getDefinitions(), chunks)

        assertEquals(1, m.givers().size)
    }

    @Test
    fun prefersAGiverMadeForTheRegionBiome() = runBlocking {
        val world = arena()
        val m =
            manager(
                listOf(
                    questGiverDef("hermit_man", biomes = listOf("forest")),
                    questGiverDef("farmer", biomes = listOf("plains"))))

        giverSpawner(world, m).trySpawn(world, m, m.getDefinitions(), chunks)

        assertEquals("farmer", m.givers().single().state.type)
    }

    @Test
    fun fallsBackToAnyGiverOfTheRightLevel() = runBlocking {
        val world = arena()
        val m =
            manager(
                listOf(
                    questGiverDef("hermit_man", biomes = listOf("forest")),
                    questGiverDef("novice_guard", maxLevel = 2)))

        giverSpawner(world, m).trySpawn(world, m, m.getDefinitions(), chunks)

        assertEquals("hermit_man", m.givers().single().state.type)
    }

    @Test
    fun doesNothingWithoutQuestGiverTypes() = runBlocking {
        val world = arena()
        val m = manager(emptyList())
        giverSpawner(world, m).trySpawn(world, m, m.getDefinitions(), chunks)
        assertTrue(m.getAll().isEmpty())
    }
}
