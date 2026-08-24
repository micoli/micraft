package org.micoli.micraft.game.vehicle

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.world.EntityType

class VehicleRegistryLoaderTest {

    @Test
    fun load_sourcesSpeedAndSeatOffsetFromPerModelFile_notFromTheRegistryEntry() {
        val configDir = createTempDirectory("config")
        val resourcesConfig = configDir.resolve("vehicles.yaml")
        resourcesConfig.writeText(
            """
            CART:
              bbmodelFile: CART
              width: 0.8
              height: 0.8
            """
                .trimIndent())
        val path = configDir.resolve("data-vehicles.yaml")

        val modelsDir = createTempDirectory("resources_vehicles")
        modelsDir.resolve("CART").toFile().mkdir()
        modelsDir
            .resolve("CART/CART.yaml")
            .writeText(
                """
                speed: 3.5
                seatOffset:
                  x: 0.0
                  y: 1.2
                  z: 0.0
                """
                    .trimIndent())
        val dataModelsDir = createTempDirectory("data_resources_vehicles")

        val loader =
            VehicleRegistryLoader(
                path = path,
                resourcesPath = resourcesConfig,
                modelsPath = modelsDir,
                dataModelsPath = dataModelsDir,
            )

        val result = loader.load()

        val cart = result[EntityType("CART")]!!
        assertEquals(3.5f, cart.speed)
        assertEquals(1.2f, cart.seatOffset.y)
    }

    @Test
    fun load_fallsBackToDefaultsWhenNoPerModelFileExists() {
        val configDir = createTempDirectory("config")
        val resourcesConfig = configDir.resolve("vehicles.yaml")
        resourcesConfig.writeText(
            """
            CART:
              bbmodelFile: CART
              width: 0.8
              height: 0.8
            """
                .trimIndent())
        val path = configDir.resolve("data-vehicles.yaml")
        val modelsDir = createTempDirectory("resources_vehicles_empty")
        val dataModelsDir = createTempDirectory("data_resources_vehicles_empty")

        val loader =
            VehicleRegistryLoader(
                path = path,
                resourcesPath = resourcesConfig,
                modelsPath = modelsDir,
                dataModelsPath = dataModelsDir,
            )

        val cart = loader.load()[EntityType("CART")]!!

        assertEquals(2f, cart.speed)
        assertEquals(0f, cart.seatOffset.y)
    }
}
