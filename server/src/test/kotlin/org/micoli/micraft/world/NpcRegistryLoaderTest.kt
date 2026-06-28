package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.micoli.micraft.npc.behaviors.InteractionableNpcBehavior
import org.micoli.micraft.npc.behaviors.RandomMovableNpcBehavior

class NpcRegistryLoaderTest {

    private data class LoaderContext(
        val loader: NpcRegistryLoader,
        val dataDir: java.nio.file.Path,
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
                          maxTotal: 5
                          maxPerChunk: 1
                          spawnBiomes: []
                        """.trimIndent(),
                    "npc_goat" to
                        """
                        behavior: random_movable
                        width: 0.5
                        height: 0.9
                        wanderSpeed: 2.0
                        wanderRadius: 12.0
                        spawn:
                          autoSpawn: true
                          maxTotal: 30
                          maxPerChunk: 3
                          spawnBiomes: [plains]
                        """.trimIndent(),
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
        assertEquals(5, seller.spawn.maxTotal)
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
                        """.trimIndent()))
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
                        """.trimIndent()))
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
                        """.trimIndent()))
        val defs = loader.load()
        val duck = defs["npc_duck"]
        assertNotNull(duck)
        assertFalse(duck.spawn.autoSpawn)
        assertEquals(0, duck.spawn.maxTotal)
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
                        """.trimIndent(),
                    "npc_unknown" to
                        """
                        behavior: totally_fake_behavior
                        width: 0.6
                        height: 1.8
                        wanderSpeed: 0.0
                        wanderRadius: 0.0
                        """.trimIndent(),
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
                        """.trimIndent(),
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
                              maxTotal: 30
                              maxPerChunk: 3
                              spawnBiomes: []
                            """.trimIndent()),
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
                              maxTotal: 30
                              maxPerChunk: 3
                              spawnBiomes: []
                            """.trimIndent()),
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
                            """.trimIndent()))
        assertFalse(dataDir.resolve("npc_goat/npc_goat.yaml").toFile().exists())
    }
}

private fun assertFalse(value: Boolean) = kotlin.test.assertFalse(value)
