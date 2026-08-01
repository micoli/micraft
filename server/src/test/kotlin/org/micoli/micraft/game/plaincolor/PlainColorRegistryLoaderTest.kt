package org.micoli.micraft.game.plaincolor

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.PlainColor

private const val DEFAULTS = "plainColors:\n  blue: \"0055BF\"\n  red: \"C4281B\"\n"

class PlainColorRegistryLoaderTest {

    private fun loaderWith(
        yaml: String,
        resourcesYaml: String = DEFAULTS,
    ): PlainColorRegistryLoader {
        val dir = createTempDirectory()
        val path = dir.resolve("plain_colors.yaml")
        path.writeText(yaml)
        val resources = dir.resolve("plain_colors-defaults.yaml")
        resources.writeText(resourcesYaml)
        return PlainColorRegistryLoader(path, resources)
    }

    @Test
    fun validYaml_loadsPalette() {
        val result = loaderWith(DEFAULTS).load()
        assertEquals(listOf(PlainColor("blue", 0, 85, 191), PlainColor("red", 196, 40, 27)), result)
    }

    @Test
    fun missingFile_generatesDefaultsAsComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("plain_colors.yaml")
        val resources = dir.resolve("plain_colors-defaults.yaml")
        resources.writeText(DEFAULTS)
        val loader = PlainColorRegistryLoader(path, resources)
        assertTrue(path.toFile().exists(), "default file should be generated")
        val written = path.readText()
        assertTrue(written.contains("# blue:"), "defaults written as comments")
        assertTrue(
            written.lines().any { it.trim() == "plainColors:" },
            "root key stays uncommented so a single color can be uncommented on its own")
        assertEquals(2, loader.load().size, "commented defaults are still active")
    }

    @Test
    fun override_winsOverResourcesValue() {
        val result = loaderWith("plainColors:\n  blue: \"000000\"\n").load()
        assertEquals(PlainColor("blue", 0, 0, 0), result.first())
        assertEquals("red", result[1].name, "non-overridden default still present")
    }

    @Test
    fun dataOnlyColors_areAppendedAfterResourcesOrder() {
        val result = loaderWith("plainColors:\n  gold: \"FFD700\"\n").load()
        assertEquals(listOf("blue", "red", "gold"), result.map { it.name })
    }

    @Test
    fun writeBack_isIdempotent_doesNotDuplicateComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("plain_colors.yaml")
        path.writeText("plainColors:\n  blue: \"0055BF\"\n")
        val resources = dir.resolve("plain_colors-defaults.yaml")
        resources.writeText(DEFAULTS)
        PlainColorRegistryLoader(path, resources)
        val first = path.readText()
        PlainColorRegistryLoader(path, resources)
        assertEquals(first, path.readText())
        assertEquals(1, first.split("# red:").size - 1, "single comment for the missing color")
    }

    @Test
    fun invalidYaml_leftUntouched_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("plain_colors.yaml")
        val corrupt = "this is not: [valid yaml: }"
        path.writeText(corrupt)
        val resources = dir.resolve("plain_colors-defaults.yaml")
        resources.writeText(DEFAULTS)
        val loader = PlainColorRegistryLoader(path, resources)
        assertEquals(corrupt, path.readText(), "corrupt file left untouched")
        assertEquals(2, loader.load().size)
    }

    @Test
    fun invalidHex_isSkipped() {
        val result = loaderWith("plainColors:\n  blue: \"nothex\"\n").load()
        assertEquals(listOf("red"), result.map { it.name })
    }

    @Test
    fun paletteBeyondSixBits_isTruncated() {
        val many =
            (1..80).joinToString("\n", prefix = "plainColors:\n") {
                "  c$it: \"0000${"%02X".format(it)}\""
            }
        val result = loaderWith(many, "plainColors: {}\n").load()
        assertEquals(BlockState.MAX_COLOR_INDEX, result.size)
        assertNull(result.getOrNull(BlockState.MAX_COLOR_INDEX))
    }
}
