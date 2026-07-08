package org.micoli.micraft.game.item

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemType

class ItemRegistryLoaderTest {

    private fun loaderWith(yaml: String, resourcesYaml: String = "{}\n"): ItemRegistryLoader {
        val dir = createTempDirectory()
        val tmp = dir.resolve("items.yaml")
        tmp.writeText(yaml)
        val resources = dir.resolve("items-defaults.yaml")
        resources.writeText(resourcesYaml)
        return ItemRegistryLoader(tmp, resources)
    }

    @Test
    fun validYaml_loadsItems() {
        val loader =
            loaderWith(
                """
            COBBLESTONE:
              buildable: true
              placesBlock: STONE
            SNOWBALL:
              buildable: false
            """
                    .trimIndent())
        val result = loader.load()
        assertEquals(2, result.size)
        assertEquals(true, result[ItemType("COBBLESTONE")]?.buildable)
        assertEquals(BlockType.STONE, result[ItemType("COBBLESTONE")]?.placesBlock)
        assertEquals(false, result[ItemType("SNOWBALL")]?.buildable)
        assertNull(result[ItemType("SNOWBALL")]?.placesBlock)
    }

    @Test
    fun anyItemKey_isAccepted() {
        val loader = loaderWith("CUSTOM_ITEM:\n  buildable: false\n")
        val result = loader.load()
        assertEquals(1, result.size)
        assertEquals(false, result[ItemType("CUSTOM_ITEM")]?.buildable)
    }

    @Test
    fun unknownPlacesBlock_isAccepted() {
        val loader = loaderWith("COBBLESTONE:\n  buildable: true\n  placesBlock: UNKNOWN_BLOCK\n")
        val result = loader.load()
        assertEquals(1, result.size)
        assertEquals(BlockType("UNKNOWN_BLOCK"), result[ItemType("COBBLESTONE")]?.placesBlock)
    }

    @Test
    fun missingFile_generatesDefault() {
        val dir = createTempDirectory()
        val path = dir.resolve("items.yaml")
        val resources = dir.resolve("items-defaults.yaml")
        resources.writeText("COBBLESTONE:\n  buildable: true\n  label: COB\n  bg: \"#7A7A7A\"\n")
        val loader = ItemRegistryLoader(path, resources)
        assertTrue(path.toFile().exists(), "Default file should be generated")
        val result = loader.load()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val loader = loaderWith("this is not: [valid yaml: }", "COBBLESTONE:\n  buildable: true\n")
        val result = loader.load()
        assertTrue(result.isNotEmpty(), "Corrupt YAML should fall back to built-in defaults")
    }

    @Test
    fun partialFile_missingDefaultEntriesAppendedAsComments() {
        val dir = createTempDirectory()
        val tmp = dir.resolve("items.yaml")
        tmp.writeText(
            "COBBLESTONE:\n  buildable: true\n  placesBlock: STONE\n  label: COB\n  bg: \"#7A7A7A\"\n")
        val resources = dir.resolve("items-defaults.yaml")
        resources.writeText(
            "COBBLESTONE:\n  buildable: true\n  placesBlock: STONE\n  label: COB\n  bg: \"#7A7A7A\"\nDIRT:\n  buildable: true\n  placesBlock: DIRT\n  label: DRT\n  bg: \"#8B5A2B\"\n")
        ItemRegistryLoader(tmp, resources)
        val written = tmp.readText()
        assertTrue(written.contains("COBBLESTONE:\n  buildable: true"), "existing entry untouched")
        assertTrue(written.contains("# DIRT:"), "missing default entry added as comment")
    }

    @Test
    fun missingEntry_isMergedFromResourcesDefault() {
        val dir = createTempDirectory()
        val tmp = dir.resolve("items.yaml")
        tmp.writeText("COBBLESTONE:\n  buildable: true\n")
        val resources = dir.resolve("items-defaults.yaml")
        resources.writeText("COBBLESTONE:\n  buildable: true\nDIRT:\n  buildable: true\n")
        val result = ItemRegistryLoader(tmp, resources).load()
        assertEquals(2, result.size, "missing default entry is active in loaded result")
        assertTrue(result.containsKey(ItemType("DIRT")))
    }
}
