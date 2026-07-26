package org.micoli.micraft.game.npc.animal

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.npc.NpcGender
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testWorld

private val TEST_ANIMAL_CONFIG =
    AnimalYamlEntry(
        diet = NpcDiet.CARNIVORE,
        lifespanDays = 10.0,
        canReproduce = true,
        gestationDays = 2.0,
        offspringType = "wolf_baby",
        offspringMinCount = 1,
        offspringMaxCount = 1,
        reproductionCooldownDays = 5.0,
        matingRange = 5.0f,
        scale = 1.0f,
        adultType = null,
        baseStats = BaseStats(str = 10, dex = 10, con = 10),
        statsVariance = 0,
        hpRegenPerSec = 5.0f,
        manaRegenPerSec = 3.0f,
        foodSearchRadius = 10.0f,
        hungerRatePerDay = 0.1,
        feedHungerReduction = 0.3,
        combatExitDelaySec = 2.0f,
        hungerThresholdToHunt = 0.5,
        hungerThresholdToMate = 0.4,
    )

private val BABY_CONFIG =
    AnimalYamlEntry(
        diet = NpcDiet.CARNIVORE,
        canReproduce = false,
        scale = 0.75f,
        adultType = "wolf",
        hpRegenPerSec = 2.0f,
        hungerRatePerDay = 0.05,
        hungerThresholdToHunt = 0.99,
        hungerThresholdToMate = 0.99,
    )

private fun testAnimalDef(
    type: String = "wolf",
    config: AnimalYamlEntry = TEST_ANIMAL_CONFIG,
): NpcDefinition =
    NpcDefinition(
        type = type,
        behavior = AnimalNpcBehavior(),
        bbmodelFile = type,
        width = 0.6f,
        height = 1.5f,
        wanderSpeed = 3f,
        wanderRadius = 20f,
        hp = 40,
        aggroMode = AggroMode.AGGRESSIVE,
        aggroRange = 8f,
        animalConfig = config,
    )

private fun testNpcManager(
    defs: Map<String, NpcDefinition>
): Pair<NpcManager, MutableList<ServerMessage>> {
    val broadcasts = mutableListOf<ServerMessage>()
    val m = NpcManager(broadcast = { broadcasts.add(it) }, getSessions = { emptyList() })
    m.loadDefinitions(defs)
    return m to broadcasts
}

private fun fakeCombatProcessor(npcManager: NpcManager) =
    CombatProcessor(
        config = CombatConfigData(),
        attackRegistry = emptyMap(),
        armorRegistry = emptyMap(),
        classRegistry = emptyMap(),
        npcManager = npcManager,
        getSessions = { emptyList() },
        broadcastCombatLog = {},
        subscribeToChannel = { _, _ -> },
        i18n = testI18n(),
        savePlayer = {},
    )

private fun testProcessor(
    npcManager: NpcManager,
    gameTimeService: GameTimeService = GameTimeService(1200.0),
): AnimalInteractionProcessor {
    val world = testWorld()
    val vegConfig = VegetationConfig()
    val vegManager =
        VegetationManager(world, vegConfig, Path.of("/tmp/test_veg_animal_processor.yaml"))
    return AnimalInteractionProcessor(
        npcManager = npcManager,
        combatProcessor = fakeCombatProcessor(npcManager),
        world = world,
        vegetationManager = vegManager,
        gameTimeService = gameTimeService,
        broadcast = {},
    )
}

class AnimalInteractionProcessorTest {

    @Test
    fun tick_incrementsAgeAndHunger() = runBlocking {
        val (m, _) = testNpcManager(mapOf("wolf" to testAnimalDef()))
        val instance = m.spawnNpc("Rex", "wolf", Vec3(0f, 5f, 0f))
        instance.animalData =
            AnimalInstanceData.initial(
                lifespanDays = 10.0,
                baseStats = BaseStats(),
                statsVariance = 0,
                initialHunger = 0.3,
                initialAge = 0.0,
            )
        val proc = testProcessor(m)

        val ageBefore = instance.animalData!!.ageGameDays
        val hungerBefore = instance.animalData!!.hunger
        proc.tick()

        assertTrue(instance.animalData!!.ageGameDays > ageBefore, "age should increase after tick")
        assertTrue(instance.animalData!!.hunger > hungerBefore, "hunger should increase after tick")
    }

    @Test
    fun tick_naturalDeath_whenAgeExceedsLifespan() = runBlocking {
        val (m, _) = testNpcManager(mapOf("wolf" to testAnimalDef()))
        val instance = m.spawnNpc("OldRex", "wolf", Vec3(0f, 5f, 0f))
        instance.animalData =
            AnimalInstanceData.initial(
                lifespanDays = null,
                baseStats = BaseStats(),
                statsVariance = 0,
                initialAge = 9.9999,
            )
        val proc = testProcessor(m)

        repeat(100) { proc.tick() }

        assertTrue(
            instance.isDead,
            "NPC should die of old age when ageGameDays >= lifespanDays (${TEST_ANIMAL_CONFIG.lifespanDays})")
    }

    @Test
    fun tick_noNaturalDeath_whenLifespanNull() = runBlocking {
        val config = TEST_ANIMAL_CONFIG.copy(lifespanDays = null)
        val (m, _) = testNpcManager(mapOf("wolf" to testAnimalDef(config = config)))
        val instance = m.spawnNpc("Immortal", "wolf", Vec3(0f, 5f, 0f))
        instance.animalData =
            AnimalInstanceData.initial(
                lifespanDays = null,
                baseStats = BaseStats(),
                statsVariance = 0,
                initialAge = 9999.0,
            )
        val proc = testProcessor(m)

        proc.tick()

        assertFalse(instance.isDead, "NPC with null lifespanDays should not die of old age")
    }

    @Test
    fun tick_hpRegen_whenOutOfCombat() = runBlocking {
        val (m, _) = testNpcManager(mapOf("wolf" to testAnimalDef()))
        val instance = m.spawnNpc("Rex", "wolf", Vec3(0f, 5f, 0f))
        instance.animalData =
            AnimalInstanceData.initial(
                lifespanDays = 10.0, baseStats = BaseStats(), statsVariance = 0)
        instance.lastDamagedAtMs = 0L
        val maxHp = instance.state.maxHp
        instance.currentHp = maxHp / 2
        instance.state = instance.state.copy(currentHp = maxHp / 2)

        val proc = testProcessor(m)
        repeat(400) { proc.tick() }

        assertTrue(
            instance.currentHp > maxHp / 2,
            "HP should regen when out of combat (got ${instance.currentHp}, expected > ${maxHp / 2})")
    }

    @Test
    fun tick_noHpRegen_whenInCombat() = runBlocking {
        val (m, _) = testNpcManager(mapOf("wolf" to testAnimalDef()))
        val instance = m.spawnNpc("Rex", "wolf", Vec3(0f, 5f, 0f))
        instance.animalData =
            AnimalInstanceData.initial(
                lifespanDays = 10.0, baseStats = BaseStats(), statsVariance = 0)
        instance.lastDamagedAtMs = System.currentTimeMillis()
        val maxHp = instance.state.maxHp
        instance.currentHp = maxHp / 2

        val proc = testProcessor(m)
        repeat(20) { proc.tick() }

        assertEquals(maxHp / 2, instance.currentHp, "HP should not regen during combat window")
    }

    @Test
    fun tick_manaRegen_whenOutOfCombat() = runBlocking {
        val (m, _) = testNpcManager(mapOf("wolf" to testAnimalDef()))
        val instance = m.spawnNpc("Rex", "wolf", Vec3(0f, 5f, 0f))
        instance.animalData =
            AnimalInstanceData.initial(
                lifespanDays = 10.0, baseStats = BaseStats(), statsVariance = 0)
        instance.lastDamagedAtMs = 0L
        instance.currentMana = 0

        val proc = testProcessor(m)
        repeat(400) { proc.tick() }

        assertTrue(
            instance.currentMana > 0,
            "Mana should regen when out of combat (maxMana=${instance.maxMana})")
    }

    @Test
    fun tick_gestationCountdown_spawnsOffspring() = runBlocking {
        val babyDef = testAnimalDef(type = "wolf_baby", config = BABY_CONFIG)
        val wolfDef = testAnimalDef()
        val (m, _) = testNpcManager(mapOf("wolf" to wolfDef, "wolf_baby" to babyDef))
        val mother = m.spawnNpc("Mother", "wolf", Vec3(0f, 5f, 0f))
        val gameTime = GameTimeService(1200.0)

        val tinyGestation = 0.001
        val data =
            AnimalInstanceData(
                gender = NpcGender.FEMALE,
                ageGameDays = 1.0,
                hunger = 0.2,
                gestationRemainingDays = tinyGestation,
                lastReproductionDay = null,
                parentIds = mutableSetOf(),
                stats = BaseStats(),
                motherLevel = 0,
            )
        mother.animalData = data

        gameTime.tick(1.0)
        val proc = testProcessor(m, gameTime)
        repeat(50) { proc.tick() }

        assertNull(
            mother.animalData!!.gestationRemainingDays,
            "gestationRemainingDays should be null after delivery")
        assertTrue(
            m.getAll().any { it.state.type == "wolf_baby" }, "A wolf_baby should have been spawned")
    }

    @Test
    fun tick_babyEvolution_whenLevelReachesMotherLevel() = runBlocking {
        val wolfDef = testAnimalDef()
        val babyDef = testAnimalDef(type = "wolf_baby", config = BABY_CONFIG)
        val (m, _) = testNpcManager(mapOf("wolf" to wolfDef, "wolf_baby" to babyDef))
        val baby = m.spawnNpc("Pup", "wolf_baby", Vec3(0f, 5f, 0f))
        val motherLevel = 3
        baby.instanceLevel = motherLevel
        baby.animalData =
            AnimalInstanceData(
                gender = NpcGender.MALE,
                ageGameDays = 1.0,
                hunger = 0.2,
                gestationRemainingDays = null,
                lastReproductionDay = null,
                parentIds = mutableSetOf(),
                stats = BaseStats(),
                motherLevel = motherLevel,
            )

        val proc = testProcessor(m)
        proc.tick()

        assertFalse(
            m.getAll().any { it.state.id == baby.state.id },
            "Baby should be removed from NpcManager after evolution")
        assertTrue(
            m.getAll().any { it.state.type == "wolf" },
            "An adult wolf should have been spawned after baby evolution")
    }

    @Test
    fun animalInstanceData_initial_randomGenderAssigned() {
        val data =
            AnimalInstanceData.initial(
                lifespanDays = 10.0, baseStats = BaseStats(), statsVariance = 0)
        assertNotNull(data.gender)
    }

    @Test
    fun animalInstanceData_offspring_parentIdsSet() {
        val parentA =
            AnimalInstanceData.initial(
                lifespanDays = 10.0, baseStats = BaseStats(), statsVariance = 0)
        val parentB =
            AnimalInstanceData.initial(
                lifespanDays = 10.0, baseStats = BaseStats(), statsVariance = 0)
        val child =
            AnimalInstanceData.offspring(
                parentA = parentA,
                parentB = parentB,
                statsVariance = 0,
                parentAId = "parent-a",
                parentBId = "parent-b",
                motherLevel = 5,
            )
        assertEquals(setOf("parent-a", "parent-b"), child.parentIds)
        assertEquals(5, child.motherLevel)
        assertEquals(0.0, child.ageGameDays)
    }

    @Test
    fun animalInstanceData_toState_fromState_roundTrip() {
        val original =
            AnimalInstanceData(
                gender = NpcGender.FEMALE,
                ageGameDays = 3.14,
                hunger = 0.42,
                gestationRemainingDays = 1.5,
                lastReproductionDay = 7.0,
                parentIds = mutableSetOf("id1", "id2"),
                stats = BaseStats(str = 12, dex = 8, con = 15),
                motherLevel = 4,
            )
        val restored = AnimalInstanceData.fromState(original.toState())

        assertEquals(original.gender, restored.gender)
        assertEquals(original.ageGameDays, restored.ageGameDays)
        assertEquals(original.hunger, restored.hunger)
        assertEquals(original.gestationRemainingDays, restored.gestationRemainingDays)
        assertEquals(original.lastReproductionDay, restored.lastReproductionDay)
        assertEquals(original.parentIds, restored.parentIds)
        assertEquals(original.stats, restored.stats)
        assertEquals(original.motherLevel, restored.motherLevel)
    }
}
