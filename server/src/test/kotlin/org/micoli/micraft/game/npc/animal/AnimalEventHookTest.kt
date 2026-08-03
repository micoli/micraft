package org.micoli.micraft.game.npc.animal

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.npc.NpcDeathCause
import org.micoli.micraft.npc.NpcGender
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testWorld

private val HOOK_CONFIG =
    AnimalYamlEntry(
        diet = NpcDiet.HERBIVORE,
        lifespanDays = 10.0,
        canReproduce = true,
        gestationDays = 1.0,
        offspringType = "goat_baby",
        offspringMinCount = 1,
        offspringMaxCount = 1,
        reproductionCooldownDays = 0.0,
        matingRange = 5f,
        baseStats = BaseStats(str = 10, dex = 10, con = 10),
        statsVariance = 0,
        hpRegenPerSec = 0f,
        hungerRatePerDay = 1.0,
        feedHungerReduction = 0.3,
        hungerThresholdToHunt = 0.5,
        hungerThresholdToMate = 0.9,
    )

private fun goatDef(type: String = "goat", config: AnimalYamlEntry = HOOK_CONFIG) =
    NpcDefinition(
        type = type,
        behavior = AnimalNpcBehavior(),
        bbmodelFile = type,
        width = 0.6f,
        height = 1.2f,
        wanderSpeed = 2f,
        wanderRadius = 10f,
        hp = 30,
        aggroMode = AggroMode.PASSIVE,
        aggroRange = 6f,
        animalConfig = config,
    )

private class Recorder {
    val events = mutableListOf<AnimalEvent>()

    fun sink(): (AnimalEvent) -> Unit = { events.add(it) }

    fun types() = events.map { it.type }
}

private fun processor(
    manager: NpcManager,
    onEvent: (AnimalEvent) -> Unit = {},
    gameTimeService: GameTimeService = GameTimeService(1.0),
): AnimalInteractionProcessor {
    val world = testWorld()
    return AnimalInteractionProcessor(
        npcManager = manager,
        combatProcessor =
            CombatProcessor(
                config = CombatConfigData(),
                attackRegistry = emptyMap(),
                armorRegistry = emptyMap(),
                classRegistry = emptyMap(),
                npcManager = manager,
                getSessions = { emptyList() },
                broadcastCombatLog = {},
                subscribeToChannel = { _, _ -> },
                i18n = testI18n(),
                savePlayer = {},
            ),
        world = world,
        vegetationManager =
            VegetationManager(world, VegetationConfig(), Path.of("/tmp/test_veg_hook.yaml")),
        gameTimeService = gameTimeService,
        broadcast = {},
        onEvent = onEvent,
    )
}

private fun manager(
    defs: Map<String, NpcDefinition>,
    onNpcKilled: suspend (NpcInstance, NpcDeathCause, NpcInstance?) -> Unit = { _, _, _ -> },
): NpcManager =
    NpcManager(broadcast = {}, getSessions = { emptyList() }, onNpcKilled = onNpcKilled).apply {
        loadDefinitions(defs)
    }

class AnimalEventHookTest {

    @Test
    fun hungerThreshold_emitsHungryOnceWhenCrossed() = runBlocking {
        val m = manager(mapOf("goat" to goatDef()))
        val goat = m.spawnNpc("Bella", "goat", Vec3(0f, 5f, 0f))
        goat.animalData =
            AnimalInstanceData.initial(
                lifespanDays = 10.0,
                baseStats = BaseStats(),
                statsVariance = 0,
                initialHunger = 0.49,
                initialAge = 0.0,
            )
        val rec = Recorder()
        val p = processor(m, rec.sink())

        repeat(40) { p.tick() }

        assertEquals(
            1,
            rec.events.count { it.type == AnimalEventType.HUNGRY },
            "crossing the hunt threshold must be reported exactly once")
        val hungry = rec.events.first { it.type == AnimalEventType.HUNGRY }
        assertEquals(goat.state.id, hungry.npcId)
        assertEquals("Bella", hungry.npcName)
    }

    @Test
    fun gestation_emitsBirthWhenItCompletes() = runBlocking {
        val m = manager(mapOf("goat" to goatDef(), "goat_baby" to goatDef("goat_baby")))
        val mother = m.spawnNpc("Bella", "goat", Vec3(0f, 5f, 0f))
        mother.animalData =
            AnimalInstanceData.initial(
                    lifespanDays = 10.0,
                    baseStats = BaseStats(),
                    statsVariance = 0,
                    initialAge = 0.0,
                )
                .also {
                    it.gender = NpcGender.FEMALE
                    it.gestationRemainingDays = 0.001
                }
        val rec = Recorder()
        val p = processor(m, rec.sink())

        repeat(5) { p.tick() }

        assertTrue(rec.types().contains(AnimalEventType.BIRTH), "birth should be reported")
        val birth = rec.events.first { it.type == AnimalEventType.BIRTH }
        assertEquals(mother.state.id, birth.otherId, "birth must point back to the mother")
        assertEquals("goat_baby", birth.npcType)
    }

    /**
     * Old age is reported *once*, by the kill hook, with its cause — not as an animal event as
     * well. Emitting from both places is what made the simulator count every natural death twice.
     */
    @Test
    fun oldAge_isReportedOnceByTheKillHook() = runBlocking {
        val causes = mutableListOf<NpcDeathCause>()
        // lifespan comes from the definition, not from the instance
        val m =
            manager(mapOf("goat" to goatDef(config = HOOK_CONFIG.copy(lifespanDays = 1.0)))) { _, c, _ ->
                causes.add(c)
            }
        val goat = m.spawnNpc("Ancient", "goat", Vec3(0f, 5f, 0f))
        goat.animalData =
            AnimalInstanceData.initial(
                lifespanDays = 1.0,
                baseStats = BaseStats(),
                statsVariance = 0,
                initialAge = 0.999,
            )
        val rec = Recorder()
        val p = processor(m, rec.sink())

        repeat(10) { p.tick() }

        assertEquals(listOf(NpcDeathCause.OLD_AGE), causes, "exactly one death, cause OLD_AGE")
        assertTrue(goat.isDead)
        // the animal hook reports lifecycle only (hunger here); a death reported from both sides is
        // what used to double-count natural deaths
        assertTrue(
            rec.types().none { it == AnimalEventType.BIRTH || it == AnimalEventType.EVOLVE },
            "unexpected lifecycle events: ${rec.types()}")
    }

    @Test
    fun defaultSink_isNoOp_soTheLiveServerIsUnchanged() = runBlocking {
        val m = manager(mapOf("goat" to goatDef()))
        val goat = m.spawnNpc("Bella", "goat", Vec3(0f, 5f, 0f))
        goat.animalData =
            AnimalInstanceData.initial(
                lifespanDays = 10.0,
                baseStats = BaseStats(),
                statsVariance = 0,
                initialHunger = 0.49,
                initialAge = 0.0,
            )
        // no onEvent argument: the live wiring
        val p = processor(m)

        repeat(40) { p.tick() }

        assertTrue(goat.animalData!!.hunger > 0.49, "hunger still progresses without a sink")
    }
}
