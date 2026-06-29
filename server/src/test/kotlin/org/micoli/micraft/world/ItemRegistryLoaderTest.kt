package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemRegistryLoaderTest {

    private fun loaderWith(yaml: String): ItemRegistryLoader {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText(yaml)
        return ItemRegistryLoader(tmp)
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
        val loader = ItemRegistryLoader(path)
        assertTrue(path.toFile().exists(), "Default file should be generated")
        val result = loader.load()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val loader = loaderWith("this is not: [valid yaml: }")
        val result = loader.load()
        assertTrue(result.isNotEmpty(), "Corrupt YAML should fall back to built-in defaults")
    }
}
