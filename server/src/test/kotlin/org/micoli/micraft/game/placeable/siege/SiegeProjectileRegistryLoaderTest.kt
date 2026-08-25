package org.micoli.micraft.game.placeable.siege

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.EntityType

class SiegeProjectileRegistryLoaderTest {

    private data class LoaderContext(
        val loader: SiegeProjectileRegistryLoader,
        val dataDir: Path,
    )

    private fun loaderWithProjectiles(
        projectiles: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val resourcesDir = createTempDirectory("resources_siege_projectiles")
        val dataDir = createTempDirectory("data_siege_projectiles")
        projectiles.forEach { (name, yaml) ->
            val dir = resourcesDir.resolve(name)
            dir.toFile().mkdir()
            dir.resolve("$name.yaml").writeText(yaml)
        }
        overrides.forEach { (name, yaml) ->
            val overrideDir = dataDir.resolve(name)
            overrideDir.toFile().mkdir()
            overrideDir.resolve("$name.yaml").writeText(yaml)
        }
        return LoaderContext(SiegeProjectileRegistryLoader(resourcesDir, dataDir), dataDir)
    }

    @Test
    fun validYaml_loadsAllProjectiles() {
        val (loader) = loaderWithProjectiles(mapOf("BOULDER" to "bbmodelFile: \"\"\nradius: 0.4\n"))
        val result = loader.load()
        assertEquals(1, result.size)
        val boulder = result[EntityType("BOULDER")]!!
        assertEquals(0.4f, boulder.radius)
    }

    @Test
    fun defaults_appliedWhenFieldsOmitted() {
        val (loader) = loaderWithProjectiles(mapOf("BOULDER" to "bbmodelFile: BOULDER\n"))
        val result = loader.load()
        assertEquals(0.3f, result[EntityType("BOULDER")]?.radius)
    }

    @Test
    fun invalidYaml_skipsProjectile() {
        val (loader) =
            loaderWithProjectiles(
                mapOf(
                    "BOULDER" to "radius: 0.4\n",
                    "BROKEN" to "this is not: [valid yaml: }",
                ))
        val result = loader.load()
        assertEquals(1, result.size)
        assertTrue(result.containsKey(EntityType("BOULDER")))
        assertFalse(result.containsKey(EntityType("BROKEN")))
    }

    @Test
    fun dataOverride_mergesAndWritesBack() {
        val (loader, dataDir) =
            loaderWithProjectiles(
                projectiles = mapOf("BOULDER" to "radius: 0.4\n"),
                overrides = mapOf("BOULDER" to "radius: 0.9\n"),
            )
        val result = loader.load()
        assertEquals(0.9f, result[EntityType("BOULDER")]?.radius)
        val writtenBack = dataDir.resolve("BOULDER/BOULDER.yaml").readText()
        assertTrue(writtenBack.contains("0.9"))
    }
}
