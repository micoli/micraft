package org.micoli.micraft.game.npc.roster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcSpawnConfig
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.FlatArenaChunkGenerator
import org.micoli.micraft.player.Vec3

class RegionPopulationTest {
    private val world = WorldState(FlatArenaChunkGenerator(regionBudget = 2, zoneLevel = 3))
    private val region = world.regionAt(0, 0)!!

    private fun def(type: String, autoSpawn: Boolean = true) =
        NpcDefinition(
            type = type,
            behavior = StaticNpcBehavior(),
            bbmodelFile = "npc",
            width = 0.6f,
            height = 1.8f,
            wanderSpeed = 0f,
            wanderRadius = 0f,
            spawn = NpcSpawnConfig(autoSpawn = autoSpawn))

    private val defs = mapOf("deer" to def("deer"), "hermit" to def("hermit", autoSpawn = false))

    private fun manager() = NpcManager(broadcast = {}).also { it.loadDefinitions(defs) }

    @Test
    fun censusCountsWildNpcsOnly() = runBlocking {
        val m = manager()
        m.spawnNpc("Wild", "deer", Vec3(1f, 8f, 1f))
        m.spawnNpc("Pet", "deer", Vec3(2f, 8f, 2f)).ownerId = "someone"
        m.spawnNpc("Giver", "hermit", Vec3(3f, 8f, 3f))
        m.spawnNpc("Corpse", "deer", Vec3(4f, 8f, 4f)).isDead = true

        val census = RegionPopulation(world) { defs }.census(m.getAll())

        assertEquals(1, census.total(region))
        assertEquals(1, census.count(region, "deer"))
    }

    @Test
    fun regionIsFullAtItsBudget() = runBlocking {
        val m = manager()
        val population = RegionPopulation(world) { defs }
        m.spawnNpc("A", "deer", Vec3(1f, 8f, 1f))
        assertFalse(population.census(m.getAll()).isFull(region))

        m.spawnNpc("B", "deer", Vec3(2f, 8f, 2f))
        assertTrue(population.census(m.getAll()).isFull(region))
    }

    @Test
    fun rosterIsRebuiltWhenDefinitionsChange() {
        var current = defs
        val population = RegionPopulation(world) { current }
        val before = population.rosterOf(region)

        current = defs + ("boar" to def("boar"))

        assertNotSame(before, population.rosterOf(region))
    }

    @Test
    fun zeroBudgetNeverReportsFull() {
        val openWorld = WorldState(FlatArenaChunkGenerator(regionBudget = 0))
        val census = RegionPopulation(openWorld) { defs }.census(emptyList())

        assertFalse(census.isFull(openWorld.regionAt(0, 0)!!))
    }
}
