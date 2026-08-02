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
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testWorld

/**
 * Growing up.
 *
 * The original rule was "a baby matures once it reaches its mother's level", and the only way an
 * NPC gains levels is kill XP — which a passive baby with no attacks can never earn. So every birth
 * ended in an old-age death as a baby: 502 births and 0 maturations over 60 simulated days.
 * `growthDays` is what turns reproduction into actual population renewal.
 */
private fun babyDef(
    growthDays: Double?,
    lifespanDays: Double = 10.0,
    adultType: String? = "goat",
) =
    NpcDefinition(
        type = "goat_baby",
        behavior = AnimalNpcBehavior(),
        behaviorKey = "animal",
        bbmodelFile = "goat",
        width = 0.35f,
        height = 0.65f,
        wanderSpeed = 1.8f,
        wanderRadius = 8f,
        hp = 4,
        aggroMode = AggroMode.PASSIVE,
        aggroRange = 4f,
        animalConfig =
            AnimalYamlEntry(
                diet = NpcDiet.HERBIVORE,
                lifespanDays = lifespanDays,
                adultType = adultType,
                growthDays = growthDays,
                canReproduce = false,
                hpRegenPerSec = 0f,
                hungerRatePerDay = 0.0,
            ),
    )

private fun adultDef() =
    NpcDefinition(
        type = "goat",
        behavior = AnimalNpcBehavior(),
        behaviorKey = "animal",
        bbmodelFile = "goat",
        width = 0.5f,
        height = 0.9f,
        wanderSpeed = 2f,
        wanderRadius = 12f,
        hp = 10,
        aggroMode = AggroMode.PASSIVE,
        aggroRange = 5f,
        animalConfig =
            AnimalYamlEntry(
                diet = NpcDiet.HERBIVORE,
                lifespanDays = 20.0,
                canReproduce = true,
                hpRegenPerSec = 0f,
                hungerRatePerDay = 0.0,
            ),
    )

private fun processor(manager: NpcManager, onEvent: (AnimalEvent) -> Unit = {}) =
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
                VegetationManager(world, VegetationConfig(), Path.of("/tmp/test_veg_growth.yaml")),
            // one real second per game day: a tick is 0.05 game days
            gameTimeService = GameTimeService(1.0),
            broadcast = {},
            onEvent = onEvent,
        )
    }

private fun manager(vararg defs: NpcDefinition) =
    NpcManager(broadcast = {}, getSessions = { emptyList() }).apply {
        loadDefinitions(defs.associateBy { it.type })
    }

class AnimalGrowthTest {

    @Test
    fun aBabyOldEnough_becomesItsAdultType() = runBlocking {
        val m = manager(babyDef(growthDays = 0.10), adultDef())
        m.spawnNpc("Kid", "goat_baby", Vec3(0f, 5f, 0f))
        val events = mutableListOf<AnimalEvent>()
        val p = processor(m) { events.add(it) }

        repeat(5) { p.tick() }

        val alive = m.getAll().filter { !it.isDead }
        assertEquals(listOf("goat"), alive.map { it.state.type }, "the baby must have matured")
        assertEquals(1, events.count { it.type == AnimalEventType.EVOLVE })
    }

    @Test
    fun growthHappensOnce_notOnEveryTickAfterwards() = runBlocking {
        val m = manager(babyDef(growthDays = 0.05), adultDef())
        m.spawnNpc("Kid", "goat_baby", Vec3(0f, 5f, 0f))
        val events = mutableListOf<AnimalEvent>()
        val p = processor(m) { events.add(it) }

        repeat(20) { p.tick() }

        // the adult has no adultType of its own, so nothing may evolve a second time
        assertEquals(1, events.count { it.type == AnimalEventType.EVOLVE })
        assertEquals(1, m.getAll().count { !it.isDead })
    }

    /** `growthDays: null` must behave exactly as before, or every existing definition changes. */
    @Test
    fun withoutGrowthDays_aBabyNeverMaturesOnAgeAlone() = runBlocking {
        val m = manager(babyDef(growthDays = null), adultDef())
        m.spawnNpc("Kid", "goat_baby", Vec3(0f, 5f, 0f))
        val events = mutableListOf<AnimalEvent>()
        val p = processor(m) { events.add(it) }

        repeat(30) { p.tick() }

        assertTrue(events.none { it.type == AnimalEventType.EVOLVE })
        assertEquals(listOf("goat_baby"), m.getAll().filter { !it.isDead }.map { it.state.type })
    }

    @Test
    fun theMaturedAdultKeepsItsAge_soItDoesNotLiveTwice() = runBlocking {
        val m = manager(babyDef(growthDays = 0.10), adultDef())
        val baby = m.spawnNpc("Kid", "goat_baby", Vec3(0f, 5f, 0f))
        baby.animalData =
            AnimalInstanceData.initial(
                lifespanDays = 10.0,
                baseStats = BaseStats(),
                statsVariance = 0,
                initialAge = 0.0,
            )
        val p = processor(m)

        repeat(5) { p.tick() }

        val adult = m.getAll().single { !it.isDead }
        val age = adult.animalData?.ageGameDays
        assertTrue(age != null && age >= 0.10, "the adult must carry the age it grew up with: $age")
    }

    /** A baby whose `adultType` is missing from the registry must not vanish. */
    @Test
    fun anUnknownAdultType_leavesTheBabyAlone() = runBlocking {
        val m = manager(babyDef(growthDays = 0.05, adultType = "unicorn"))
        m.spawnNpc("Kid", "goat_baby", Vec3(0f, 5f, 0f))
        val p = processor(m)

        repeat(5) { p.tick() }

        assertEquals(listOf("goat_baby"), m.getAll().filter { !it.isDead }.map { it.state.type })
    }
}
