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

/**
 * What being starving or pregnant costs.
 *
 * Hunger used to clamp at 1.0 and do nothing else: no damage, no slowdown, no death. With no cost
 * to being hungry there was no density-dependent feedback at all, so the population was regulated
 * only by the hard ceiling and by old age — which is exactly what the 60-day run showed.
 */
private val CONDITION_CONFIG =
    AnimalYamlEntry(
        diet = NpcDiet.HERBIVORE,
        lifespanDays = 100.0,
        canReproduce = true,
        gestationDays = 5.0,
        offspringType = "grazer",
        matingRange = 8f,
        // no drift during a test: hunger is set by hand
        hungerRatePerDay = 0.0,
        hpRegenPerSec = 2f,
        hungerThresholdToHunt = 0.4,
        hungerThresholdToMate = 0.5,
        starvationThreshold = 1.0,
        starvationAttackMultiplier = 0.5f,
        starvationSpeedMultiplier = 0.2f,
        starvationSterile = true,
        starvationDeathDays = 0.0,
        gestationAttackMultiplier = 0.6f,
        gestationSpeedMultiplier = 0.7f,
    )

private fun grazerDef(config: AnimalYamlEntry = CONDITION_CONFIG) =
    NpcDefinition(
        type = "grazer",
        behavior = AnimalNpcBehavior(),
        behaviorKey = "animal",
        bbmodelFile = "grazer",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 2f,
        wanderRadius = 10f,
        hp = 20,
        aggroMode = AggroMode.PASSIVE,
        aggroRange = 5f,
        animalConfig = config,
    )

private fun manager(def: NpcDefinition = grazerDef()) =
    NpcManager(broadcast = {}, getSessions = { emptyList() }).apply {
        loadDefinitions(mapOf(def.type to def))
    }

private fun managerReporting(
    def: NpcDefinition,
    causes: MutableList<NpcDeathCause>,
) =
    NpcManager(
            broadcast = {},
            getSessions = { emptyList() },
            onNpcKilled = { _, cause -> causes.add(cause) },
        )
        .apply { loadDefinitions(mapOf(def.type to def)) }

private fun processor(manager: NpcManager, gameDaySeconds: Double = 1.0) =
    testWorld().let { world ->
        AnimalInteractionProcessor(
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
                VegetationManager(
                    world, VegetationConfig(), Path.of("/tmp/test_veg_condition.yaml")),
            gameTimeService = GameTimeService(gameDaySeconds),
            broadcast = {},
        )
    }

private fun NpcInstance.withHunger(hunger: Double, gestation: Double? = null) {
    animalData =
        AnimalInstanceData.initial(
                lifespanDays = 100.0,
                baseStats = BaseStats(),
                statsVariance = 0,
                initialAge = 0.0,
                initialHunger = hunger,
            )
            .also {
                it.gender = NpcGender.FEMALE
                it.gestationRemainingDays = gestation
            }
}

class AnimalConditionTest {

    @Test
    fun aHealthyAnimal_hasNoPenalty() = runBlocking {
        val m = manager()
        val goat = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        goat.withHunger(0.2)

        processor(m).tick()

        assertEquals(1f, goat.speedMultiplier)
        assertEquals(1f, goat.damageMultiplier)
    }

    @Test
    fun starving_slowsMovementAndWeakensAttacks() = runBlocking {
        val m = manager()
        val goat = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        goat.withHunger(1.0)

        processor(m).tick()

        assertEquals(0.2f, goat.speedMultiplier)
        assertEquals(0.5f, goat.damageMultiplier)
    }

    @Test
    fun pregnant_slowsMovementAndWeakensAttacks() = runBlocking {
        val m = manager()
        val goat = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        goat.withHunger(0.2, gestation = 3.0)

        processor(m).tick()

        assertEquals(0.7f, goat.speedMultiplier)
        assertEquals(0.6f, goat.damageMultiplier)
    }

    @Test
    fun starvingAndPregnant_penaltiesMultiply() = runBlocking {
        val m = manager()
        val goat = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        goat.withHunger(1.0, gestation = 3.0)

        processor(m).tick()

        // 0.2 × 0.7 and 0.5 × 0.6: worse than either alone, which is what makes her catchable
        assertEquals(0.2f * 0.7f, goat.speedMultiplier)
        assertEquals(0.5f * 0.6f, goat.damageMultiplier)
    }

    @Test
    fun eating_clearsThePenaltyCompletely() = runBlocking {
        val m = manager()
        val goat = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        goat.withHunger(1.0)
        val p = processor(m)
        p.tick()
        assertEquals(0.2f, goat.speedMultiplier)

        goat.animalData?.hunger = 0.1
        p.tick()

        assertEquals(1f, goat.speedMultiplier)
        assertEquals(0.0, goat.animalData?.starvingDays, "the starvation timer must reset")
    }

    /**
     * The gate that makes starvation mean anything: regeneration is fed by food. A hungry animal
     * healing at full rate could never actually weaken.
     */
    @Test
    fun aHungryAnimal_doesNotRegenerate() = runBlocking {
        val m = manager()
        val goat = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        goat.withHunger(0.9)
        goat.currentHp = 5
        goat.state = goat.state.copy(currentHp = 5)

        repeat(40) { processor(m).tick() }

        assertEquals(5, goat.currentHp, "a hungry animal must not heal")
    }

    @Test
    fun aFedAnimal_doesRegenerate() = runBlocking {
        val m = manager()
        val goat = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        goat.withHunger(0.1)
        goat.currentHp = 5
        goat.state = goat.state.copy(currentHp = 5)
        val p = processor(m)

        repeat(40) { p.tick() }

        assertTrue(goat.currentHp > 5, "a fed animal heals, was ${goat.currentHp}")
    }

    @Test
    fun starvingLongEnough_killsWithTheStarvationCause() = runBlocking {
        val causes = mutableListOf<NpcDeathCause>()
        val def = grazerDef(CONDITION_CONFIG.copy(starvationDeathDays = 0.10))
        val m = managerReporting(def, causes)
        val goat = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        goat.withHunger(1.0)

        repeat(5) { processor(m).tick() }

        assertEquals(listOf(NpcDeathCause.STARVATION), causes)
        assertTrue(goat.isDead)
    }

    @Test
    fun starvationDeathDaysZero_neverKills() = runBlocking {
        val causes = mutableListOf<NpcDeathCause>()
        val m = managerReporting(grazerDef(), causes)
        val goat = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        goat.withHunger(1.0)
        val p = processor(m)

        repeat(60) { p.tick() }

        // 0 disables the mechanic, so every existing definition keeps its current behaviour
        assertTrue(causes.isEmpty())
        assertTrue(!goat.isDead)
    }

    @Test
    fun aStarvingAnimal_refusesToConceive() = runBlocking {
        val m = manager()
        val female = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        val male = m.spawnNpc("Bruno", "grazer", Vec3(1f, 5f, 0f))
        female.withHunger(1.0)
        male.withHunger(1.0)
        male.animalData?.gender = NpcGender.MALE
        val p = processor(m)

        repeat(40) { p.tick() }

        assertTrue(female.animalData?.gestationRemainingDays == null)
        assertTrue(female.animalData?.mateTargetId == null, "no courtship while starving")
    }

    @Test
    fun aCrowdedAnimal_stopsLookingForAMate() = runBlocking {
        val def = grazerDef(CONDITION_CONFIG.copy(maxLocalDensity = 2, densityRadius = 20f))
        val m = manager(def)
        val female = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        val male = m.spawnNpc("Bruno", "grazer", Vec3(2f, 5f, 0f))
        val crowd = m.spawnNpc("Carla", "grazer", Vec3(3f, 5f, 0f))
        listOf(female, male, crowd).forEach { it.withHunger(0.1) }
        male.animalData?.gender = NpcGender.MALE
        val p = processor(m)

        repeat(40) { p.tick() }

        assertTrue(female.animalData?.mateTargetId == null, "a crowded herd must stop breeding")
        assertTrue(female.animalData?.gestationRemainingDays == null)
    }

    @Test
    fun roomToSpare_stillAllowsCourtship() = runBlocking {
        // the mirror case: without it, the test above would pass on a mechanic that simply blocks
        // every pairing
        val def = grazerDef(CONDITION_CONFIG.copy(maxLocalDensity = 5, densityRadius = 20f))
        val m = manager(def)
        val female = m.spawnNpc("Bella", "grazer", Vec3(0f, 5f, 0f))
        val male = m.spawnNpc("Bruno", "grazer", Vec3(2f, 5f, 0f))
        listOf(female, male).forEach { it.withHunger(0.1) }
        male.animalData?.gender = NpcGender.MALE
        val p = processor(m)

        repeat(40) { p.tick() }

        assertTrue(
            female.animalData?.mateTargetId != null ||
                female.animalData?.gestationRemainingDays != null,
            "an uncrowded pair must be able to court")
    }
}
