package org.micoli.micraft.game.npc

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The definitions the game actually ships, loaded through the real loader.
 *
 * Unit tests build their own definitions, so a tunable can be wired end to end in code and still be
 * absent — or silently misplaced under the wrong YAML block — in `resources/entities`. That is how
 * a mechanic ends up implemented, tested and inert. These assertions read the shipped files.
 */
class ShippedEntityConfigTest {

    private val resourcesPath = Path.of("resources/entities")

    private fun definitions(): Map<String, NpcDefinition>? {
        // the suite may run from a working directory without the resource tree
        if (!resourcesPath.exists()) return null
        return NpcRegistryLoader(
                resourcesEntityPath = resourcesPath,
                dataEntityPath = Path.of("data/resources/entities"),
            )
            .load()
    }

    /**
     * Guard against the whole class quietly passing on an empty registry: every other test here
     * returns early when the resource tree is missing, which would turn them all into no-ops.
     */
    @Test
    fun theShippedRegistryIsActuallyRead() {
        assertTrue(resourcesPath.exists(), "resources/entities must be reachable from the test cwd")
        val defs = assertNotNull(definitions())
        assertTrue(defs.size >= 5, "expected the shipped entity types, got ${defs.keys}")
        assertTrue(
            defs.values.any { it.animalConfig != null },
            "expected at least one animal among ${defs.keys}")
    }

    @Test
    fun everyBabyHasAGrowthTimer() {
        val defs = definitions() ?: return
        val babies = defs.filter { (_, def) -> def.animalConfig?.adultType != null }
        assertTrue(babies.isNotEmpty(), "expected at least one type with an adultType")
        for ((type, def) in babies) {
            val growth = def.animalConfig?.growthDays
            assertNotNull(growth, "$type has an adultType but no growthDays: it can never grow up")
            assertTrue(growth > 0.0, "$type has a non-positive growthDays")
            val lifespan = def.animalConfig?.lifespanDays
            if (lifespan != null) {
                assertTrue(
                    growth < lifespan,
                    "$type would die of old age ($lifespan d) before growing up ($growth d)")
            }
        }
    }

    @Test
    fun everyAnimalCanStarve() {
        val defs = definitions() ?: return
        val animals = defs.filter { (_, def) -> def.animalConfig != null }
        assertTrue(animals.isNotEmpty())
        for ((type, def) in animals) {
            val config = def.animalConfig ?: continue
            // 0 means the mechanic is off for this type; that is a decision, not a default to
            // inherit
            assertTrue(
                config.starvationDeathDays > 0.0, "$type cannot starve, so hunger costs it nothing")
            assertTrue(config.starvationSpeedMultiplier < 1f, "$type is not slowed while starving")
            assertTrue(
                config.starvationAttackMultiplier < 1f, "$type does not weaken while starving")
        }
    }

    /** The multipliers must stay in a range the movement and damage maths can use. */
    @Test
    fun conditionMultipliersAreSaneFractions() {
        val defs = definitions() ?: return
        for ((type, def) in defs) {
            val config = def.animalConfig ?: continue
            for ((label, value) in
                listOf(
                    "starvationSpeedMultiplier" to config.starvationSpeedMultiplier,
                    "starvationAttackMultiplier" to config.starvationAttackMultiplier,
                    "gestationSpeedMultiplier" to config.gestationSpeedMultiplier,
                    "gestationAttackMultiplier" to config.gestationAttackMultiplier,
                )) {
                assertTrue(value > 0f && value <= 1f, "$type.$label is out of range: $value")
            }
        }
    }

    /** Goal-seeking is what makes the rest work; an adult still on `random_movable` ignores it. */
    @Test
    fun everyAnimalUsesTheAnimalBehavior() {
        val defs = definitions() ?: return
        for ((type, def) in defs) {
            if (def.animalConfig == null) continue
            assertTrue(
                def.behaviorKey == "animal",
                "$type has an animal block but runs '${def.behaviorKey}', so it ignores prey, food and mates")
        }
    }

    @Test
    fun aMateIsWorthWalkingFurtherThanTheContactRange() {
        val defs = definitions() ?: return
        for ((type, def) in defs) {
            val config = def.animalConfig ?: continue
            if (!config.canReproduce) continue
            assertTrue(
                config.matingRange > config.matingContactRange,
                "$type would have to already touch its mate to consider walking to it")
        }
    }

    /**
     * A quota only regulates if it is set. Without one the spawner keeps filling whatever room the
     * chunks leave, which is what made spawning outweigh reproduction almost four to one.
     */
    @Test
    fun everyAutoSpawningTypeHasACeiling() {
        val defs = definitions() ?: return
        val autoSpawning = defs.filter { (_, def) -> def.spawn.autoSpawn }
        assertTrue(autoSpawning.isNotEmpty(), "expected at least one auto-spawning type")
        for ((type, def) in autoSpawning) {
            assertTrue(def.spawn.maxTotal > 0, "$type auto-spawns with no world-wide ceiling")
            assertTrue(
                def.spawn.minTotal in 1 until def.spawn.maxTotal,
                "$type needs a restocking floor below its ceiling (${def.spawn.minTotal}/${def.spawn.maxTotal})")
        }
    }

    @Test
    fun youngAreBornRatherThanSpawned() {
        val defs = definitions() ?: return
        val young = defs.filter { (_, def) -> def.animalConfig?.adultType != null }
        for ((type, def) in young) {
            assertTrue(
                !def.spawn.autoSpawn,
                "$type is a juvenile form: it should appear through reproduction, not spawning")
        }
    }
}
