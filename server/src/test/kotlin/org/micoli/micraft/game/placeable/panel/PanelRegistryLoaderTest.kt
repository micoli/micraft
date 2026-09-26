package org.micoli.micraft.game.placeable.panel

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.placeable.panel.PanelConstants

class PanelRegistryLoaderTest {

    private data class LoaderContext(val loader: PanelRegistryLoader)

    private fun loaderWith(
        panels: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val resourcesDir = createTempDirectory("resources_panels")
        val dataDir = createTempDirectory("data_panels")
        panels.forEach { (name, yaml) ->
            val dir = resourcesDir.resolve(name)
            dir.toFile().mkdir()
            dir.resolve("$name.yaml").writeText(yaml)
        }
        overrides.forEach { (name, yaml) ->
            val dir = dataDir.resolve(name)
            dir.toFile().mkdir()
            dir.resolve("$name.yaml").writeText(yaml)
        }
        return LoaderContext(PanelRegistryLoader(resourcesDir, dataDir))
    }

    @Test
    fun validYaml_loadsPanelWithDefaults() {
        val (loader) = loaderWith(mapOf("SIGN" to "bbmodelFile: SIGN\n"))
        val def = loader.load()[EntityType("SIGN")]!!
        assertEquals("SIGN", def.bbmodelFile)
        assertEquals(PanelConstants.DEFAULT_PIXEL_WIDTH, def.pixelWidth)
        assertTrue(def.rotatable)
    }

    @Test
    fun override_replacesFields() {
        val (loader) =
            loaderWith(
                mapOf("SIGN" to "bbmodelFile: SIGN\npixelWidth: 640\n"),
                mapOf("SIGN" to "pixelWidth: 1024\n"))
        val def = loader.load()[EntityType("SIGN")]!!
        assertEquals(1024, def.pixelWidth)
        assertEquals("SIGN", def.bbmodelFile)
    }

    @Test
    fun noResourcesDir_returnsEmpty() {
        val loader = PanelRegistryLoader(Path.of("/nonexistent"), Path.of("/nonexistent2"))
        assertEquals(emptyMap(), loader.load())
    }
}
