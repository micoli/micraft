package org.micoli.micraft.game.placeable.furniture

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.EntityType

class FurnitureRegistryLoaderTest {

    private data class LoaderContext(
        val loader: FurnitureRegistryLoader,
        val dataDir: Path,
    )

    private fun loaderWith(
        furnitures: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val resourcesDir = createTempDirectory("resources_furnitures")
        val dataDir = createTempDirectory("data_furnitures")
        furnitures.forEach { (name, yaml) ->
            val dir = resourcesDir.resolve(name)
            dir.toFile().mkdir()
            dir.resolve("$name.yaml").writeText(yaml)
        }
        overrides.forEach { (name, yaml) ->
            val dir = dataDir.resolve(name)
            dir.toFile().mkdir()
            dir.resolve("$name.yaml").writeText(yaml)
        }
        return LoaderContext(FurnitureRegistryLoader(resourcesDir, dataDir), dataDir)
    }

    @Test
    fun validYaml_loadsAllFurnitures() {
        val (loader) =
            loaderWith(
                mapOf(
                    "TABLE" to "bbmodelFile: TABLE\nwidth: 2.0\nheight: 1.0\n",
                    "STATUE" to "bbmodelFile: STATUE\nrotatable: false\n",
                ))
        val result = loader.load()
        assertEquals(2, result.size)
        val table = result[EntityType("TABLE")]!!
        assertEquals("TABLE", table.bbmodelFile)
        assertEquals(2.0f, table.width)
        assertEquals(1.0f, table.height)
        assertTrue(table.rotatable)
        assertFalse(result[EntityType("STATUE")]!!.rotatable)
    }

    @Test
    fun defaults_appliedWhenFieldsOmitted() {
        val (loader) = loaderWith(mapOf("TABLE" to "bbmodelFile: TABLE\n"))
        val def = loader.load()[EntityType("TABLE")]!!
        assertEquals(0.8f, def.width)
        assertEquals(0.8f, def.height)
        assertTrue(def.rotatable)
    }

    @Test
    fun missingYamlInDirectory_isSkipped() {
        val resourcesDir = createTempDirectory("resources_furnitures")
        resourcesDir.resolve("EMPTY_DIR").toFile().mkdir()
        val dataDir = createTempDirectory("data_furnitures")
        assertTrue(FurnitureRegistryLoader(resourcesDir, dataDir).load().isEmpty())
    }

    @Test
    fun missingResourcesDir_returnsEmpty() {
        val resourcesDir = createTempDirectory("resources_furnitures").resolve("nope")
        val dataDir = createTempDirectory("data_furnitures")
        assertTrue(FurnitureRegistryLoader(resourcesDir, dataDir).load().isEmpty())
    }

    @Test
    fun dataOverride_mergesAndWritesBack() {
        val (loader, dataDir) =
            loaderWith(
                furnitures = mapOf("TABLE" to "bbmodelFile: TABLE\nwidth: 2.0\n"),
                overrides = mapOf("TABLE" to "rotatable: false\n"),
            )
        val def = loader.load()[EntityType("TABLE")]!!
        assertFalse(def.rotatable)
        assertEquals(2.0f, def.width, "Non-overridden fields stay from the base entry")
        val writtenBack = dataDir.resolve("TABLE/TABLE.yaml").readText()
        assertTrue(writtenBack.contains("width"), "Missing keys added in write-back as comments")
    }
}
