package org.micoli.micraft.game.npc.roster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.MovementMode
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcSpawnConfig
import org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Region
import org.micoli.micraft.game.world.biome.BiomeDefinition

class RosterBuilderTest {
    private val forest =
        BiomeDefinition(
            id = "forest",
            zones = emptyList(),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT,
            regionBudget = 25)
    private val sea = forest.copy(id = "sea", liquid = true, regionBudget = 12)

    private fun region(biome: BiomeDefinition = forest, level: Int = 13, seedX: Int = 100) =
        Region(seedX, 200, biome, "Region$seedX", level)

    private fun npc(
        type: String,
        hostile: Boolean = false,
        biomes: List<String> = emptyList(),
        minLevel: Int = 0,
        maxLevel: Int = Int.MAX_VALUE,
        aquatic: Boolean = false,
    ) =
        NpcDefinition(
            type = type,
            behavior = StaticNpcBehavior(),
            bbmodelFile = "npc",
            width = 0.6f,
            height = 1.8f,
            wanderSpeed = 0f,
            wanderRadius = 0f,
            minLevel = minLevel,
            maxLevel = maxLevel,
            aggroMode = if (hostile) AggroMode.AGGRESSIVE else AggroMode.PASSIVE,
            movementMode =
                if (aquatic) listOf(MovementMode.SWIMMING) else listOf(MovementMode.WALKING),
            spawn = NpcSpawnConfig(autoSpawn = true, spawnBiomes = biomes))

    private fun NpcDefinition.withSpawn(change: NpcSpawnConfig.() -> NpcSpawnConfig) =
        copy(spawn = spawn.change())

    private val registry: Map<String, NpcDefinition> =
        listOf(
                npc("deer"),
                npc("rabbit"),
                npc("fox"),
                npc("duck"),
                npc("owl", biomes = listOf("forest")),
                npc("camel", biomes = listOf("desert")),
                npc("baby").withSpawn { copy(autoSpawn = false) },
                npc("wolf", hostile = true),
                npc("spider", hostile = true, biomes = listOf("forest")),
                npc("zombie", hostile = true, minLevel = 11, maxLevel = 15),
                npc(
                    "bandit",
                    hostile = true,
                    minLevel = 11,
                    maxLevel = 15,
                    biomes = listOf("desert")),
                npc("frost_troll", hostile = true, minLevel = 16, maxLevel = 20),
                npc("treant", hostile = true).withSpawn { copy(maxTotal = 5) },
                npc("dolphin", aquatic = true),
                npc("shark", hostile = true, aquatic = true),
            )
            .associateBy { it.type }

    @Test
    fun sameInputsGiveTheSameRoster() {
        val a = RosterBuilder.build(42L, region(), registry)
        val b = RosterBuilder.build(42L, region(), registry)

        assertEquals(a, b)
    }

    @Test
    fun rosterOnlyHoldsTypesTheRegionAllows() {
        repeat(200) { i ->
            val roster = RosterBuilder.build(i.toLong(), region(seedX = i * 300), registry)
            val allowed =
                setOf("deer", "rabbit", "fox", "duck", "owl", "wolf", "spider", "zombie", "treant")

            assertTrue(allowed.containsAll(roster.types), "unexpected types in ${roster.types}")
        }
    }

    @Test
    fun rosterHoldsTwoToFourPassivesAndOneToThreeHostiles() {
        repeat(200) { i ->
            val roster = RosterBuilder.build(i.toLong(), region(seedX = i * 300), registry)
            val commons = roster.entries.filterNot { it.rare }
            val hostiles =
                commons.count { registry.getValue(it.type).aggroMode == AggroMode.AGGRESSIVE }

            assertTrue(commons.size - hostiles in 2..4, "passives in $roster")
            assertTrue(hostiles in 1..3, "hostiles in $roster")
        }
    }

    @Test
    fun seaRegionsOnlyDrawAquaticTypes() {
        val roster = RosterBuilder.build(7L, region(biome = sea), registry)

        assertEquals(setOf("dolphin", "shark"), roster.types)
    }

    @Test
    fun rareEntersAboutOneRegionInFiveAndIsCapped() {
        val rosters =
            (0 until 1000).map { RosterBuilder.build(9L, region(seedX = it * 300), registry) }
        val withRare = rosters.filter { roster -> roster.entries.any { it.rare } }

        assertTrue(withRare.size in 120..280, "rare in ${withRare.size} of 1000 Regions")
        withRare.forEach { roster -> assertTrue(roster.entries.single { it.rare }.share in 1..2) }
    }

    @Test
    fun budgetIsSharedByWeight() {
        val heavy = registry + ("deer" to npc("deer").withSpawn { copy(weight = 3) })
        val roster =
            (0 until 200)
                .map { RosterBuilder.build(3L, region(seedX = it * 300), heavy) }
                .first { r ->
                    "deer" in r.types && r.entries.none { it.rare } && r.entries.size == 3
                }

        assertEquals(25, roster.entries.sumOf { it.share })
        assertTrue(roster.entries.single { it.type == "deer" }.share >= 14, "deer share in $roster")
    }

    @Test
    fun noBudgetMeansNoShare() {
        val roster =
            RosterBuilder.build(1L, region(biome = forest.copy(regionBudget = 0)), registry)

        assertTrue(roster.entries.all { it.share == 0 })
    }

    @Test
    fun eachRareKeepsItsOwnChanceWhenSeveralAreEligible() {
        val twoRares =
            registry + ("hydra" to npc("hydra", hostile = true).withSpawn { copy(maxTotal = 4) })
        val rosters =
            (0 until 1000).map { RosterBuilder.build(5L, region(seedX = it * 300), twoRares) }

        for (rare in listOf("treant", "hydra")) {
            val count = rosters.count { rare in it.types }
            assertTrue(count in 110..260, "$rare in $count of 1000 Regions")
        }
        assertTrue(rosters.all { roster -> roster.entries.count { it.rare } <= 1 })
    }

    @Test
    fun nonPositiveWeightGetsNoShare() {
        val weightless = registry + ("deer" to npc("deer").withSpawn { copy(weight = -3) })
        val roster =
            (0 until 200)
                .map { RosterBuilder.build(4L, region(seedX = it * 300), weightless) }
                .first { "deer" in it.types }

        assertEquals(0, roster.entries.single { it.type == "deer" }.share)
        assertEquals(25, roster.entries.sumOf { it.share })
    }
}
