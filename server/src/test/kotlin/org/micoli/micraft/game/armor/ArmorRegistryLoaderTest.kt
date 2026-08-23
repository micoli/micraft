package org.micoli.micraft.game.armor

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmorRegistryLoaderTest {

    private data class LoaderContext(
        val loader: ArmorRegistryLoader,
        val dataDir: Path,
    )

    private fun loaderWithArmors(
        armors: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val resourcesDir = createTempDirectory("resources_armors")
        val dataDir = createTempDirectory("data_armors")
        armors.forEach { (name, yaml) ->
            val armorDir = resourcesDir.resolve(name)
            armorDir.toFile().mkdir()
            armorDir.resolve("$name.yaml").writeText(yaml)
        }
        overrides.forEach { (name, yaml) ->
            val overrideDir = dataDir.resolve(name)
            overrideDir.toFile().mkdir()
            overrideDir.resolve("$name.yaml").writeText(yaml)
        }
        return LoaderContext(ArmorRegistryLoader(resourcesDir, dataDir), dataDir)
    }

    @Test
    fun validYaml_loadsAllArmors() {
        val (loader) =
            loaderWithArmors(
                mapOf(
                    "leather_helmet" to
                        """
                        wearable:
                          head: true
                        statBonus:
                          con: 1
                        """
                            .trimIndent()))
        val defs = loader.load()
        assertEquals(1, defs.size)
        val helmet = defs["leather_helmet"]
        assertNotNull(helmet)
        assertTrue(helmet.wearable.head)
        assertFalse(helmet.wearable.body)
        assertEquals(1, helmet.statBonus.con)
    }

    @Test
    fun validYaml_loadsSegmentedLimbAndCapeSlots() {
        val (loader) =
            loaderWithArmors(
                mapOf(
                    "cloak" to
                        """
                        wearable:
                          cape: true
                          rightBiceps: true
                          leftFoot: true
                        """
                            .trimIndent()))
        val defs = loader.load()
        val cloak = defs["cloak"]
        assertNotNull(cloak)
        assertTrue(cloak.wearable.cape)
        assertTrue(cloak.wearable.rightBiceps)
        assertTrue(cloak.wearable.leftFoot)
        assertFalse(cloak.wearable.rightForearm)
        assertFalse(cloak.wearable.rightHand)
        assertFalse(cloak.wearable.leftThigh)
    }

    @Test
    fun noOverrideFile_usesResourceDefaults() {
        val (loader) =
            loaderWithArmors(
                mapOf(
                    "leather_helmet" to
                        """
                        wearable:
                          head: true
                        """
                            .trimIndent()))
        val defs = loader.load()
        assertTrue(defs["leather_helmet"]!!.wearable.head)
    }

    @Test
    fun dataOverride_emptyFile_writesBackDefaults() {
        val (loader, dataDir) =
            loaderWithArmors(
                armors =
                    mapOf(
                        "leather_helmet" to
                            """
                            wearable:
                              head: true
                            statBonus:
                              con: 1
                            """
                                .trimIndent()),
                overrides = mapOf("leather_helmet" to ""),
            )
        val defs = loader.load()
        assertTrue(defs["leather_helmet"]!!.wearable.head)
        val writtenBack = dataDir.resolve("leather_helmet/leather_helmet.yaml").readText()
        assertTrue(writtenBack.contains("wearable"), "Write-back must contain all keys")
        assertTrue(writtenBack.contains("statBonus"))
    }

    @Test
    fun dataOverride_mergesAndWritesBack() {
        val (loader, dataDir) =
            loaderWithArmors(
                armors =
                    mapOf(
                        "leather_helmet" to
                            """
                            wearable:
                              head: true
                            statBonus:
                              con: 1
                            """
                                .trimIndent()),
                overrides =
                    mapOf(
                        "leather_helmet" to
                            """
                            statBonus:
                              con: 5
                              str: 2
                            """
                                .trimIndent()),
            )
        val defs = loader.load()
        val helmet = defs["leather_helmet"]!!
        assertEquals(5, helmet.statBonus.con)
        assertEquals(2, helmet.statBonus.str)
        assertTrue(helmet.wearable.head, "Non-overridden block keeps resource default")
        val writtenBack = dataDir.resolve("leather_helmet/leather_helmet.yaml").readText()
        assertTrue(writtenBack.contains("con: 5"), "Override value preserved in write-back")
        assertTrue(writtenBack.contains("# wearable:"), "Missing block added in write-back")
    }

    @Test
    fun dataOverride_reload_isIdempotent_doesNotDuplicateComments() {
        val (loader, dataDir) =
            loaderWithArmors(
                armors =
                    mapOf(
                        "leather_helmet" to
                            """
                            wearable:
                              head: true
                            statBonus:
                              con: 1
                            """
                                .trimIndent()),
                overrides = mapOf("leather_helmet" to "statBonus:\n  con: 5\n"),
            )
        loader.load()
        val afterFirstLoad = dataDir.resolve("leather_helmet/leather_helmet.yaml").readText()
        loader.load()
        val afterSecondLoad = dataDir.resolve("leather_helmet/leather_helmet.yaml").readText()
        assertEquals(
            afterFirstLoad, afterSecondLoad, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("wearable").findAll(afterSecondLoad).count())
    }

    @Test
    fun dataOverride_invalidYaml_leftUntouched() {
        val (loader, dataDir) =
            loaderWithArmors(
                armors =
                    mapOf(
                        "leather_helmet" to
                            """
                            wearable:
                              head: true
                            """
                                .trimIndent()),
                overrides = mapOf("leather_helmet" to "this is not: [valid yaml: }"),
            )
        loader.load()
        assertEquals(
            "this is not: [valid yaml: }",
            dataDir.resolve("leather_helmet/leather_helmet.yaml").readText())
    }

    @Test
    fun dataOverride_noFile_notCreated() {
        val (_, dataDir) =
            loaderWithArmors(
                armors =
                    mapOf(
                        "leather_helmet" to
                            """
                            wearable:
                              head: true
                            """
                                .trimIndent()))
        assertFalse(dataDir.resolve("leather_helmet/leather_helmet.yaml").toFile().exists())
    }

    @Test
    fun invalidYaml_skipsArmor() {
        val (loader) =
            loaderWithArmors(
                mapOf(
                    "leather_helmet" to "wearable:\n  head: true\n",
                    "broken_armor" to "this is not: [valid yaml: }",
                ))
        val defs = loader.load()
        assertEquals(1, defs.size)
        assertTrue(defs.containsKey("leather_helmet"))
        assertFalse(defs.containsKey("broken_armor"))
    }

    @Test
    fun missingArmorsPath_returnsEmpty() {
        val resourcesDir = createTempDirectory("resources_armors_missing")
        val loader = ArmorRegistryLoader(resourcesDir.resolve("nope"), resourcesDir.resolve("data"))
        assertTrue(loader.load().isEmpty())
    }
}
