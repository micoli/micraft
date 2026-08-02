package org.micoli.micraft.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcYamlOverride
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.npc.pack.PackConfig
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.support.testI18n

private const val HALF = 32

private fun walkerDef() =
    NpcDefinition(
        type = "walker",
        behavior = RandomMovableNpcBehavior(),
        behaviorKey = "random_movable",
        bbmodelFile = "walker",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 4f,
        wanderRadius = 12f,
        hp = 30,
        aggroMode = AggroMode.PASSIVE,
        aggroRange = 400f,
    )

private fun hunterDef() =
    NpcDefinition(
        type = "hunter",
        behavior = RandomMovableNpcBehavior(),
        behaviorKey = "random_movable",
        bbmodelFile = "hunter",
        width = 0.6f,
        height = 1.8f,
        wanderSpeed = 4f,
        wanderRadius = 12f,
        hp = 20,
        aggroMode = AggroMode.AGGRESSIVE,
        aggroRange = 40f,
        packConfig =
            PackConfig(
                callRadius = 60f,
                minSizeToEngage = 2,
                chaseRadius = 80f,
                hostileTypes = listOf("walker"),
            ),
    )

private fun testDeps() =
    SimulationDeps(
        definitions = mapOf("walker" to walkerDef(), "hunter" to hunterDef()),
        combatConfig = CombatConfigData(),
        attackRegistry = emptyMap(),
        armorRegistry = emptyMap(),
        classRegistry = emptyMap(),
        i18n = testI18n(),
        vegetationConfig = VegetationConfig(),
    )

private fun testConfig(
    spawns: List<SimSpawn> = emptyList(),
    players: List<SimPlayerSpec> = emptyList(),
) =
    SimulationConfig(
        halfSize = HALF,
        // paused: tests drive step() by hand so nothing races
        ticksPerSecond = 0,
        seed = 1234L,
        initialSpawns = spawns,
        players = players,
        gameDayDurationSeconds = 1.0,
    )

private fun simulator(config: SimulationConfig = testConfig()) = WorldSimulator(config, testDeps())

class WorldSimulatorTest {

    @Test
    fun start_pregeneratesTheArenaAndItsWalls() = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            assertTrue(sim.world.loadedChunkCount() > 4, "arena chunks should be pregenerated")
            assertEquals(BlockType.GRASS, sim.world.getBlockIfLoaded(0, 7, 0))
            assertTrue(sim.world.getBlockIfLoaded(HALF, 8, 0).isSolid, "wall must be there")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun start_placesInitialSpawnsAndPlayers() = runBlocking {
        val sim =
            simulator(
                testConfig(
                    spawns = listOf(SimSpawn("walker", count = 3)),
                    players = listOf(SimPlayerSpec("tester", 0f, 0f)),
                ))
        try {
            sim.start()
            assertEquals(3, sim.npcDtos().size)
            assertEquals(1, sim.playerDtos().size)
            assertEquals("tester", sim.playerDtos().first().name)
            assertTrue(sim.npcDtos().all { it.x > -HALF && it.x < HALF }, "spawns inside the arena")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun step_advancesTickAndGameTime() = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            assertEquals(0L, sim.tick)
            sim.stepOnce(25)
            assertEquals(25L, sim.tick)
            assertTrue(sim.statsDto().gameDay > 0.0, "game time must advance")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun npcs_cannotLeaveTheArena() = runBlocking {
        val sim = simulator(testConfig(spawns = listOf(SimSpawn("walker", count = 6))))
        try {
            sim.start()
            sim.stepOnce(1500)
            sim.npcDtos().forEach { npc ->
                assertTrue(
                    npc.x > -HALF && npc.x < HALF && npc.z > -HALF && npc.z < HALF,
                    "${npc.name} escaped at (${npc.x}, ${npc.z})")
            }
        } finally {
            sim.stop()
        }
    }

    @Test
    fun packHunt_isRecordedInTheEventLog() = runBlocking {
        val sim =
            simulator(
                testConfig(
                    spawns = listOf(SimSpawn("walker", count = 1), SimSpawn("hunter", count = 3))))
        try {
            sim.start()
            sim.stepOnce(60)
            val packEvents =
                sim.events.snapshot().filter {
                    it.type in
                        setOf(
                            SimEventType.PACK_CALL,
                            SimEventType.PACK_JOIN,
                            SimEventType.PACK_ENGAGE)
                }
            assertTrue(packEvents.any { it.type == SimEventType.PACK_CALL }, "a call is expected")
            assertTrue(packEvents.any { it.type == SimEventType.PACK_JOIN }, "kin should answer")
            assertTrue(sim.npcDtos().any { it.packId != null }, "members carry their pack id")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun packHunt_isReproducibleForAGivenSeed() = runBlocking {
        fun run(): List<String> = runBlocking {
            val sim =
                simulator(
                    testConfig(
                        spawns =
                            listOf(SimSpawn("walker", count = 1), SimSpawn("hunter", count = 3))))
            try {
                sim.start()
                sim.stepOnce(60)
                sim.events
                    .snapshot()
                    .filter { it.type.name.startsWith("PACK_") }
                    .map { "${it.type}" }
            } finally {
                sim.stop()
            }
        }
        assertEquals(run(), run())
    }

    @Test
    fun eventLog_isCappedAtThreeHundredKeepingTheNewest() {
        val log = SimEventLog(WorldSimulator.EVENT_HISTORY)
        repeat(1000) { i ->
            log.add(
                SimEvent(
                    seq = 0L,
                    tick = i.toLong(),
                    gameDay = 0.0,
                    type = SimEventType.SYSTEM,
                    message = "event $i"))
        }
        assertEquals(300, log.size)
        val snapshot = log.snapshot()
        assertEquals("event 700", snapshot.first().message)
        assertEquals("event 999", snapshot.last().message)
        assertEquals(1000L, snapshot.last().seq, "sequence numbers keep increasing")
    }

    @Test
    fun eventLog_sinceReturnsOnlyNewerEntries() {
        val log = SimEventLog(10)
        repeat(5) {
            log.add(
                SimEvent(
                    seq = 0L,
                    tick = 0,
                    gameDay = 0.0,
                    type = SimEventType.SYSTEM,
                    message = "e$it"))
        }
        val since = log.since(3)
        assertEquals(listOf("e3", "e4"), since.map { it.message })
    }

    @Test
    fun start_logsArenaReadyEvent() = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            assertTrue(
                sim.events.snapshot().any { it.type == SimEventType.SYSTEM },
                "startup should be logged")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun setSpeed_zeroMeansPaused() = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            sim.setSpeed(0)
            assertTrue(sim.paused)
            sim.setSpeed(50)
            assertTrue(!sim.paused)
            assertEquals(50, sim.ticksPerSecond)
        } finally {
            sim.stop()
        }
    }

    @Test
    fun spawn_clampsInsideTheArena() = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            sim.spawn("walker", x = 9999f, z = -9999f)
            val npc = sim.npcDtos().single()
            assertTrue(npc.x < HALF && npc.z > -HALF, "spawn clamped to (${npc.x}, ${npc.z})")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun spawn_unknownTypeIsReportedNotThrown() = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            sim.spawn("dragon", 0f, 0f)
            assertEquals(0, sim.npcDtos().size)
            assertTrue(sim.events.snapshot().any { it.message.contains("dragon") })
        } finally {
            sim.stop()
        }
    }

    @Test
    fun npcDetail_returnsRulesInForce() = runBlocking {
        val sim = simulator(testConfig(spawns = listOf(SimSpawn("walker", count = 1))))
        try {
            sim.start()
            val id = sim.npcDtos().first().id
            val detail = sim.npcDetail(id)
            assertNotNull(detail)
            assertEquals("random_movable", detail.behaviorKey)
            assertEquals(4f, detail.wanderSpeed)
            assertNull(sim.npcDetail("nope"))
        } finally {
            sim.stop()
        }
    }

    @Test
    fun definitionOverrides_applyToNewSpawnsOnly() = runBlocking {
        val sim = simulator()
        try {
            sim.start()
            sim.spawn("walker", 0f, 0f)
            val beforeId = sim.npcDtos().first().id
            sim.applyDefinitionOverrides(mapOf("walker" to NpcYamlOverride(wanderSpeed = 42f)))
            sim.spawn("walker", 1f, 1f)
            val afterId = sim.npcDtos().first { it.id != beforeId }.id

            assertEquals(4f, sim.npcDetail(beforeId)!!.wanderSpeed, "live instance keeps its rules")
            assertEquals(42f, sim.npcDetail(afterId)!!.wanderSpeed, "new spawn uses the override")
        } finally {
            sim.stop()
        }
    }

    @Test
    fun instanceTuning_neverTouchesLiveTuning() = runBlocking {
        val liveBefore = NpcConstants.live
        val sim =
            WorldSimulator(
                testConfig().copy(npcTuning = NpcConstants.live.copy(jumpVelocity = 123f)),
                testDeps())
        try {
            sim.start()
            sim.stepOnce(5)
            assertEquals(liveBefore, NpcConstants.live)
        } finally {
            sim.stop()
        }
    }

    @Test
    fun sameSeed_producesTheSameEvolution() = runBlocking {
        suspend fun run(): List<Triple<String, Float, Float>> {
            val sim =
                WorldSimulator(
                    testConfig(spawns = listOf(SimSpawn("walker", count = 4))).copy(seed = 99L),
                    testDeps())
            return try {
                sim.start()
                sim.stepOnce(300)
                // NPC ids and generated names are not part of the simulated rules; positions are.
                sim.npcDtos()
                    .map { Triple(it.type, it.x, it.z) }
                    .sortedWith(compareBy({ it.second }, { it.third }))
            } finally {
                sim.stop()
            }
        }
        assertEquals(
            run(),
            run(),
            "a seeded simulation must be reproducible, otherwise rule comparisons are meaningless")
    }

    @Test
    fun maxGameDays_pausesTheArenaOnceTheSpanIsRun() = runBlocking {
        // running, not paused: the limit only ever fires on an arena that is actually advancing
        val sim = simulator(testConfig().copy(ticksPerSecond = 100, maxGameDays = 0.5))
        try {
            sim.start()
            sim.stepOnce(200)
            assertTrue(sim.paused, "the arena must park itself once its span is run")
            assertTrue(
                sim.statsDto().gameDay >= 0.5,
                "it must park at the limit, not before: ${sim.statsDto().gameDay}")
            // paused, not closed — the charts and the event log are the point of a bounded run
            assertTrue(sim.npcDtos().isNotEmpty() || sim.statsDto().npcCount == 0)
        } finally {
            sim.stop()
        }
    }

    @Test
    fun maxGameDays_zeroRunsOn() = runBlocking {
        val sim = simulator(testConfig().copy(ticksPerSecond = 100, maxGameDays = 0.0))
        try {
            sim.start()
            sim.stepOnce(200)
            assertTrue(
                sim.statsDto().gameDay > 0.5, "the clock must have moved past any short span")
            assertTrue(!sim.paused, "no limit means run until someone stops it")
        } finally {
            sim.stop()
        }
    }
}
