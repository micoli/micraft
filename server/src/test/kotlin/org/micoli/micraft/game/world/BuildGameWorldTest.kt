package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator

private val shared by lazy { SharedGameServices.default() }

private fun gen() = EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1)

class BuildGameWorldTest {

    @Test
    fun simulationSections_tickTheNpcEcologyButNotWeatherOrLiquid() = runBlocking {
        val sim =
            buildGameWorld(
                "sim-test", gen(), shared, GameWorldOptions(tickSections = TickSection.SIMULATION))
        sim.tick()
        val phases = sim.getTickProfile().map { it.name }.toSet()

        assertTrue("npc" in phases, "the NPC pipeline must run")
        assertTrue("vegetation" in phases, "vegetation regrowth must run")
        assertFalse("weather" in phases, "weather must be skipped")
        assertFalse("liquid" in phases, "liquids must be skipped")
        assertFalse("siegeProjectiles" in phases, "siege must be skipped")
        assertFalse("statusEffects" in phases, "player status effects must be skipped")
    }

    @Test
    fun realtimeSections_tickWeatherAndLiquid() = runBlocking {
        val world =
            buildGameWorld(
                "rt-test", gen(), shared, GameWorldOptions(tickSections = TickSection.REALTIME))
        world.tick()
        val phases = world.getTickProfile().map { it.name }.toSet()

        assertTrue("weather" in phases)
        assertTrue("liquid" in phases)
        assertTrue("npc" in phases)
    }

    @Test
    fun eachWorld_getsItsOwnClaimAndRailRegistries() {
        val a = buildGameWorld("reg-a", gen(), shared)
        val b = buildGameWorld("reg-b", gen(), shared)
        assertFalse(a.claimRegistry === b.claimRegistry, "claim registries must be per-world")
        assertFalse(
            a.railNetworkRegistry === b.railNetworkRegistry, "rail registries must be per-world")
    }

    @Test
    fun questDefinitions_areLoadedIntoEachWorld() {
        val world = buildGameWorld("quest-test", gen(), shared)
        assertTrue(
            world.questManager!!.getDefinitions().isNotEmpty(),
            "a per-world QuestManager must have its definitions loaded (FETCH/KILL quests)")
    }

    @Test
    fun npcDefinitions_areLoadedIntoEachWorld() {
        val world = buildGameWorld("npc-test", gen(), shared)
        assertTrue(
            world.npcManager.getDefinitions().containsKey("seller"),
            "a per-world NpcManager must have its catalogue loaded (seller, animals, …)")
    }

    @Test
    fun initialGameTicks_overridesTheDefaultClockStart() {
        val fresh =
            buildGameWorld("clock-test", gen(), shared, GameWorldOptions(initialGameTicks = 0L))
        assert(fresh.gameTicks == 0L) { "expected clock at 0, got ${fresh.gameTicks}" }
    }
}
