package org.micoli.micraft.game.vehicle

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VehicleModelRegistryLoaderTest {

    private data class LoaderContext(val loader: VehicleModelRegistryLoader, val modelsDir: Path)

    private fun loaderWithModels(
        models: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val modelsDir = createTempDirectory("resources_vehicles")
        val dataDir = createTempDirectory("data_vehicles")
        models.forEach { (name, yaml) ->
            val dir = modelsDir.resolve(name)
            dir.toFile().mkdir()
            dir.resolve("$name.yaml").writeText(yaml)
        }
        overrides.forEach { (name, yaml) ->
            val dir = dataDir.resolve(name)
            dir.toFile().mkdir()
            dir.resolve("$name.yaml").writeText(yaml)
        }
        return LoaderContext(VehicleModelRegistryLoader(modelsDir, dataDir), modelsDir)
    }

    private val cartYaml =
        """
        speed: 2.0
        seatOffset:
          x: 0.0
          y: 0.9
          z: 0.0
        """
            .trimIndent()

    @Test
    fun validYaml_loadsSpeedAndSeatOffset() {
        val (loader, _) = loaderWithModels(mapOf("CART" to cartYaml))

        val model = assertNotNull(loader.load("CART"))

        assertEquals(2.0f, model.speed)
        assertEquals(0.9f, model.seatOffset.y)
    }

    @Test
    fun modelWithoutYaml_isAbsent() {
        val (loader, _) = loaderWithModels(mapOf("CART" to cartYaml))

        assertNull(loader.load("MISSING"))
    }

    @Test
    fun dataOverride_replacesOnlyGivenFields() {
        val (loader, _) =
            loaderWithModels(
                models = mapOf("CART" to cartYaml),
                overrides = mapOf("CART" to "speed: 5.0\n"),
            )

        val model = assertNotNull(loader.load("CART"))

        assertEquals(5.0f, model.speed)
        assertEquals(0.9f, model.seatOffset.y)
    }

    @Test
    fun blankOverride_keepsBaseDefinition() {
        val (loader, _) =
            loaderWithModels(
                models = mapOf("CART" to cartYaml),
                overrides = mapOf("CART" to "   "),
            )

        assertEquals(2.0f, assertNotNull(loader.load("CART")).speed)
    }

    @Test
    fun invalidYaml_isSkipped() {
        val (loader, _) = loaderWithModels(mapOf("CART" to "speed: [oops"))

        assertNull(loader.load("CART"))
    }
}
