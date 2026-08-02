package org.micoli.micraft.game.npc

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.npc.animal.AnimalYamlEntry
import org.micoli.micraft.game.npc.animal.AnimalYamlOverride
import org.micoli.micraft.game.npc.animal.NpcDiet
import org.micoli.micraft.game.npc.behaviors.InteractionableNpcBehavior
import org.micoli.micraft.game.npc.behaviors.RandomMovableNpcBehavior
import org.micoli.micraft.game.npc.pack.PackConfig
import org.micoli.micraft.game.npc.pack.PackConfigOverride

class NpcRegistryLoaderTest {

    private data class LoaderContext(
        val loader: NpcRegistryLoader,
        val dataDir: Path,
    )

    private fun loaderWithNpcs(
        npcs: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val resourcesDir = createTempDirectory("resources_entity")
        val dataDir = createTempDirectory("data_entity")
        npcs.forEach { (name, yaml) ->
            val npcDir = resourcesDir.resolve(name)
            npcDir.toFile().mkdir()
            npcDir.resolve("$name.yaml").writeText(yaml)
        }
        overrides.forEach { (name, yaml) ->
            val overrideDir = dataDir.resolve(name)
            overrideDir.toFile().mkdir()
            overrideDir.resolve("$name.yaml").writeText(yaml)
        }
        return LoaderContext(NpcRegistryLoader(resourcesDir, dataDir), dataDir)
    }

    @Test
    fun validYaml_loadsAllNpcs() {
        val (loader) =
            loaderWithNpcs(
                mapOf(
                    "npc_seller" to
                        """
                        behavior: interactionable
                        width: 0.6
                        height: 1.8
                        wanderSpeed: 0.0
                        wanderRadius: 0.0
                        spawn:
                          autoSpawn: false
                          maxPerChunk: 1
                          spawnBiomes: []
                        """
                            .trimIndent(),
                    "npc_goat" to
                        """
                        behavior: random_movable
                        width: 0.5
                        height: 0.9
                        wanderSpeed: 2.0
                        wanderRadius: 12.0
                        spawn:
                          autoSpawn: true
                          maxPerChunk: 3
                          spawnBiomes: [plains]
                        """
                            .trimIndent(),
                ))
        val defs = loader.load()
        assertEquals(2, defs.size)
        val seller = defs["npc_seller"]
        assertNotNull(seller)
        assertEquals("npc_seller", seller.bbmodelFile)
        assertEquals(0.6f, seller.width)
        assertEquals(1.8f, seller.height)
        assertTrue(seller.behavior is InteractionableNpcBehavior)
        assertFalse(seller.spawn.autoSpawn)
        val goat = defs["npc_goat"]
        assertNotNull(goat)
        assertTrue(goat.behavior is RandomMovableNpcBehavior)
        assertTrue(goat.spawn.autoSpawn)
        assertEquals(listOf("plains"), goat.spawn.spawnBiomes)
    }

    @Test
    fun bbmodelFile_equalsDirectoryName() {
        val (loader) =
            loaderWithNpcs(
                mapOf(
                    "npc_cat" to
                        """
                        behavior: random_movable
                        width: 0.5
                        height: 0.9
                        wanderSpeed: 2.0
                        wanderRadius: 12.0
                        """
                            .trimIndent()))
        val defs = loader.load()
        assertEquals("npc_cat", defs["npc_cat"]?.bbmodelFile)
    }

    @Test
    fun dirWithoutYaml_skipped() {
        val (loader) =
            loaderWithNpcs(
                mapOf(
                    "npc_seller" to
                        """
                        behavior: interactionable
                        width: 0.6
                        height: 1.8
                        wanderSpeed: 0.0
                        wanderRadius: 0.0
                        """
                            .trimIndent()))
        // player dir has no yaml — verify it doesn't appear
        val resourcesDir = createTempDirectory("resources_entity2")
        resourcesDir.resolve("player").toFile().mkdir()
        val loader2 = NpcRegistryLoader(resourcesDir, createTempDirectory("data_entity2"))
        assertTrue(loader2.load().isEmpty())
        assertEquals(1, loader.load().size)
    }

    @Test
    fun missingSpawnSection_usesDefaults() {
        val (loader) =
            loaderWithNpcs(
                mapOf(
                    "npc_duck" to
                        """
                        behavior: static
                        width: 0.3
                        height: 0.5
                        wanderSpeed: 0.0
                        wanderRadius: 0.0
                        """
                            .trimIndent()))
        val defs = loader.load()
        val duck = defs["npc_duck"]
        assertNotNull(duck)
        assertFalse(duck.spawn.autoSpawn)
        assertEquals(1, duck.spawn.maxPerChunk)
        assertTrue(duck.spawn.spawnBiomes.isEmpty())
    }

    @Test
    fun unknownBehaviorKey_skipsEntry() {
        val (loader) =
            loaderWithNpcs(
                mapOf(
                    "npc_seller" to
                        """
                        behavior: interactionable
                        width: 0.6
                        height: 1.8
                        wanderSpeed: 0.0
                        wanderRadius: 0.0
                        """
                            .trimIndent(),
                    "npc_unknown" to
                        """
                        behavior: totally_fake_behavior
                        width: 0.6
                        height: 1.8
                        wanderSpeed: 0.0
                        wanderRadius: 0.0
                        """
                            .trimIndent(),
                ))
        val defs = loader.load()
        assertTrue(defs.containsKey("npc_seller"))
        assertFalse(defs.containsKey("npc_unknown"))
    }

    @Test
    fun invalidYaml_skipsNpc() {
        val (loader) =
            loaderWithNpcs(
                mapOf(
                    "npc_seller" to
                        """
                        behavior: interactionable
                        width: 0.6
                        height: 1.8
                        wanderSpeed: 0.0
                        wanderRadius: 0.0
                        """
                            .trimIndent(),
                    "npc_goat" to "this is not: [valid yaml: }",
                ))
        val defs = loader.load()
        assertEquals(1, defs.size)
        assertTrue(defs.containsKey("npc_seller"))
        assertFalse(defs.containsKey("npc_goat"))
    }

    @Test
    fun dataOverride_emptyFile_writesBackDefaults() {
        val (loader, dataDir) =
            loaderWithNpcs(
                npcs =
                    mapOf(
                        "npc_goat" to
                            """
                            behavior: random_movable
                            width: 0.5
                            height: 0.9
                            wanderSpeed: 2.0
                            wanderRadius: 12.0
                            spawn:
                              autoSpawn: true
                              maxPerChunk: 3
                              spawnBiomes: []
                            """
                                .trimIndent()),
                overrides = mapOf("npc_goat" to ""),
            )
        val defs = loader.load()
        val goat = defs["npc_goat"]
        assertNotNull(goat)
        assertEquals(0.5f, goat.width)
        assertTrue(goat.spawn.autoSpawn)
        val writtenBack = dataDir.resolve("npc_goat/npc_goat.yaml").readText()
        assertTrue(writtenBack.contains("wanderSpeed"), "Write-back must contain all keys")
        assertTrue(writtenBack.contains("behavior"))
    }

    @Test
    fun dataOverride_mergesAndWritesBack() {
        val (loader, dataDir) =
            loaderWithNpcs(
                npcs =
                    mapOf(
                        "npc_goat" to
                            """
                            behavior: random_movable
                            width: 0.5
                            height: 0.9
                            wanderSpeed: 2.0
                            wanderRadius: 12.0
                            spawn:
                              autoSpawn: true
                              maxPerChunk: 3
                              spawnBiomes: []
                            """
                                .trimIndent()),
                overrides = mapOf("npc_goat" to "wanderSpeed: 9.9\n"),
            )
        val defs = loader.load()
        val goat = defs["npc_goat"]
        assertNotNull(goat)
        assertEquals(9.9f, goat.wanderSpeed)
        assertEquals(0.5f, goat.width)
        val writtenBack = dataDir.resolve("npc_goat/npc_goat.yaml").readText()
        assertTrue(writtenBack.contains("9.9"), "Override value preserved in write-back")
        assertTrue(writtenBack.contains("behavior"), "Missing keys added in write-back")
    }

    @Test
    fun dataOverride_reload_isIdempotent_doesNotDuplicateComments() {
        val (loader, dataDir) =
            loaderWithNpcs(
                npcs =
                    mapOf(
                        "npc_goat" to
                            """
                            behavior: random_movable
                            width: 0.5
                            height: 0.9
                            wanderSpeed: 2.0
                            wanderRadius: 12.0
                            """
                                .trimIndent()),
                overrides = mapOf("npc_goat" to "wanderSpeed: 9.9\n"),
            )
        loader.reload()
        val afterFirstReload = dataDir.resolve("npc_goat/npc_goat.yaml").readText()
        loader.reload()
        val afterSecondReload = dataDir.resolve("npc_goat/npc_goat.yaml").readText()
        assertEquals(
            afterFirstReload, afterSecondReload, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("behavior").findAll(afterSecondReload).count())
    }

    @Test
    fun dataOverride_invalidYaml_leftUntouched() {
        val (loader, dataDir) =
            loaderWithNpcs(
                npcs =
                    mapOf(
                        "npc_goat" to
                            """
                            behavior: random_movable
                            width: 0.5
                            height: 0.9
                            wanderSpeed: 2.0
                            wanderRadius: 12.0
                            """
                                .trimIndent()),
                overrides = mapOf("npc_goat" to "this is not: [valid yaml: }"),
            )
        loader.reload()
        assertEquals(
            "this is not: [valid yaml: }", dataDir.resolve("npc_goat/npc_goat.yaml").readText())
    }

    @Test
    fun dataOverride_noFile_notCreated() {
        val (_, dataDir) =
            loaderWithNpcs(
                npcs =
                    mapOf(
                        "npc_goat" to
                            """
                            behavior: random_movable
                            width: 0.5
                            height: 0.9
                            wanderSpeed: 2.0
                            wanderRadius: 12.0
                            """
                                .trimIndent()))
        assertFalse(dataDir.resolve("npc_goat/npc_goat.yaml").toFile().exists())
    }

    /**
     * The `animal:` block used to be swapped wholesale, so an override touching one field reset
     * every other one to its code default — an override without `lifespanDays` made the species
     * immortal, which quietly invalidated every balancing experiment.
     */
    @Test
    fun animalOverride_mergesFieldByField() {
        val (loader) =
            loaderWithNpcs(
                npcs =
                    mapOf(
                        "wolf" to
                            """
                            behavior: animal
                            width: 0.5
                            height: 0.9
                            wanderSpeed: 2.0
                            wanderRadius: 55.0
                            hp: 18
                            animal:
                              diet: CARNIVORE
                              lifespanDays: 30.0
                              preyTypes: [goat, duck]
                              canReproduce: true
                              hungerRatePerDay: 0.10
                              hungerThresholdToHunt: 0.4
                            """
                                .trimIndent()),
                overrides =
                    mapOf(
                        "wolf" to
                            """
                            animal:
                              lifespanDays: 12.0
                            """
                                .trimIndent()),
            )

        val animal = assertNotNull(loader.load()["wolf"]?.animalConfig)
        assertEquals(12.0, animal.lifespanDays, "the overridden field wins")
        assertEquals(listOf("goat", "duck"), animal.preyTypes, "untouched fields survive")
        assertEquals(0.10, animal.hungerRatePerDay)
        assertEquals(0.4, animal.hungerThresholdToHunt)
        assertTrue(animal.canReproduce)
    }

    @Test
    fun packOverride_mergesFieldByField() {
        val (loader) =
            loaderWithNpcs(
                npcs =
                    mapOf(
                        "wolf" to
                            """
                            behavior: animal
                            width: 0.5
                            height: 0.9
                            wanderSpeed: 2.0
                            wanderRadius: 55.0
                            pack:
                              extendPackType: [wolf_veteran]
                              minSizeToEngage: 3
                              hostileTypes: [polar_bear]
                            """
                                .trimIndent()),
                overrides =
                    mapOf(
                        "wolf" to
                            """
                            pack:
                              minSizeToEngage: 5
                            """
                                .trimIndent()),
            )

        val pack = assertNotNull(loader.load()["wolf"]?.packConfig)
        assertEquals(5, pack.minSizeToEngage)
        // wiping this would silently stop the species pack-hunting at all
        assertEquals(listOf("polar_bear"), pack.hostileTypes)
        assertEquals(listOf("wolf_veteran"), pack.extendPackType)
    }

    /** A type with no `animal:` block can be given one from an override alone. */
    @Test
    fun animalOverride_onATypeWithoutAnAnimalBlock_fallsBackToTheEntryDefaults() {
        val (loader) =
            loaderWithNpcs(
                npcs =
                    mapOf(
                        "polar_bear" to
                            """
                            behavior: random_movable
                            width: 0.5
                            height: 0.9
                            wanderSpeed: 2.0
                            wanderRadius: 32.0
                            hp: 40
                            """
                                .trimIndent()),
                overrides =
                    mapOf(
                        "polar_bear" to
                            """
                            animal:
                              diet: CARNIVORE
                              lifespanDays: 40.0
                            """
                                .trimIndent()),
            )

        val animal = assertNotNull(loader.load()["polar_bear"]?.animalConfig)
        assertEquals(40.0, animal.lifespanDays)
        assertEquals(0.08, animal.hungerRatePerDay, "unset fields take the entry default")
    }

    /**
     * The other override path: the world simulator layers rules on an already-built definition
     * rather than on raw YAML. Both must merge the same way, or a tuning experiment behaves
     * differently depending on whether it came from a file or from the admin UI.
     */
    @Test
    fun definitionOverride_mergesTheAnimalBlockFieldByField() {
        val base =
            NpcDefinition(
                type = "wolf",
                behavior = RandomMovableNpcBehavior(),
                bbmodelFile = "wolf",
                width = 0.5f,
                height = 0.9f,
                wanderSpeed = 2f,
                wanderRadius = 55f,
                hp = 18,
                animalConfig =
                    AnimalYamlEntry(
                        diet = NpcDiet.CARNIVORE,
                        lifespanDays = 30.0,
                        preyTypes = listOf("goat", "duck"),
                        canReproduce = true,
                        hungerRatePerDay = 0.10,
                    ),
                packConfig = PackConfig(minSizeToEngage = 3, hostileTypes = listOf("polar_bear")),
            )

        val merged =
            base.applyOverride(
                NpcYamlOverride(
                    animal = AnimalYamlOverride(lifespanDays = 12.0),
                    pack = PackConfigOverride(minSizeToEngage = 5),
                ))

        val animal = assertNotNull(merged.animalConfig)
        assertEquals(12.0, animal.lifespanDays)
        assertEquals(listOf("goat", "duck"), animal.preyTypes)
        assertEquals(0.10, animal.hungerRatePerDay)
        assertEquals(5, assertNotNull(merged.packConfig).minSizeToEngage)
        assertEquals(listOf("polar_bear"), merged.packConfig?.hostileTypes)
    }
}

private fun assertFalse(value: Boolean) = assertFalse(value)
