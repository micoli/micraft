package org.micoli.micraft.game.skin

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkinRegistryLoaderTest {

    private data class LoaderContext(val loader: SkinRegistryLoader, val resourcesDir: Path)

    private fun loaderWithSkins(
        skins: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val resourcesDir = createTempDirectory("resources_skins")
        val dataDir = createTempDirectory("data_skins")
        skins.forEach { (name, yaml) ->
            val dir = resourcesDir.resolve(name)
            dir.toFile().mkdir()
            dir.resolve("$name.yaml").writeText(yaml)
        }
        overrides.forEach { (name, yaml) ->
            val dir = dataDir.resolve(name)
            dir.toFile().mkdir()
            dir.resolve("$name.yaml").writeText(yaml)
        }
        return LoaderContext(SkinRegistryLoader(resourcesDir, dataDir), resourcesDir)
    }

    private val articulatedYaml =
        """
        eyes:
          x: 0.0
          y: 27.5
          z: 0.0
        firstPersonHiddenBones:
          - head
        """
            .trimIndent()

    @Test
    fun validYaml_loadsEyesAndHiddenBones() {
        val (loader, _) = loaderWithSkins(mapOf("articulated" to articulatedYaml))

        val skin = loader.load("articulated")

        assertNotNull(skin)
        assertEquals(0f, skin.eyes.x)
        assertEquals(27.5f, skin.eyes.y)
        assertEquals(0f, skin.eyes.z)
        assertEquals(listOf("head"), skin.firstPersonHiddenBones)
    }

    @Test
    fun skinWithoutYaml_isAbsent() {
        val (loader, resourcesDir) = loaderWithSkins(mapOf("articulated" to articulatedYaml))
        resourcesDir.resolve("player").toFile().mkdir()

        assertNull(loader.load("player"))
        assertEquals(setOf("articulated"), loader.load().keys)
    }

    @Test
    fun dataOverride_replacesOnlyGivenFields() {
        val (loader, _) =
            loaderWithSkins(
                skins = mapOf("articulated" to articulatedYaml),
                overrides = mapOf("articulated" to "eyes:\n  x: 0.0\n  y: 26.0\n  z: -1.0\n"),
            )

        val skin = assertNotNull(loader.load("articulated"))

        assertEquals(26f, skin.eyes.y)
        assertEquals(-1f, skin.eyes.z)
        assertEquals(listOf("head"), skin.firstPersonHiddenBones)
    }

    @Test
    fun blankOverride_keepsBaseDefinition() {
        val (loader, _) =
            loaderWithSkins(
                skins = mapOf("articulated" to articulatedYaml),
                overrides = mapOf("articulated" to "   "),
            )

        assertEquals(27.5f, assertNotNull(loader.load("articulated")).eyes.y)
    }

    @Test
    fun invalidYaml_isSkipped() {
        val (loader, _) = loaderWithSkins(mapOf("articulated" to "eyes: [oops"))

        assertNull(loader.load("articulated"))
        assertTrue(loader.load().isEmpty())
    }

    @Test
    fun missingSkinsDirectory_yieldsEmptyRegistry() {
        val missing = createTempDirectory("resources_skins").resolve("nope")

        assertTrue(SkinRegistryLoader(missing, missing).load().isEmpty())
    }
}
