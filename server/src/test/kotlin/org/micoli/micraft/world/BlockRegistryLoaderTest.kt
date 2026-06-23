package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockRegistryLoaderTest {

    private fun loaderWith(yaml: String): BlockRegistryLoader {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText(yaml)
        return BlockRegistryLoader(tmp)
    }

    @Test
    fun validYaml_loadsAllBlocks() {
        val loader = loaderWith(
            """
            STONE:
              hardness: 5
              solid: true
              minimapColor: [136, 136, 136]
            DIRT:
              hardness: 3
              solid: true
              minimapColor: [122, 92, 46]
            """.trimIndent()
        )
        val result = loader.load()
        assertEquals(2, result.size)
        assertEquals(5, result[BlockType.STONE]?.hardness)
        assertEquals(true, result[BlockType.DIRT]?.solid)
    }

    @Test
    fun bedrock_hardnessMinusOne_isUnbreakable() {
        val loader = loaderWith("BEDROCK:\n  hardness: -1\n  solid: true\n  minimapColor: [0, 0, 0]\n")
        val result = loader.load()
        val def = result[BlockType.BEDROCK]!!
        assertEquals(-1, def.hardness)
        // extension property converts -1 to Int.MAX_VALUE
        BlockRegistry.load(result)
        assertEquals(Int.MAX_VALUE, BlockType.BEDROCK.hardness)
    }

    @Test
    fun unknownBlockKey_isSkipped() {
        val loader = loaderWith("NOT_A_BLOCK:\n  hardness: 1\n  solid: true\n  minimapColor: [0, 0, 0]\n")
        val result = loader.load()
        assertTrue(result.isEmpty())
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val loader = loaderWith("this is not: [valid yaml: }")
        val result = loader.load()
        assertTrue(result.isNotEmpty(), "Corrupt YAML should fall back to built-in defaults")
    }

    @Test
    fun missingFile_generatesDefault() {
        val dir = createTempDirectory()
        val path = dir.resolve("blocks.yaml")
        val loader = BlockRegistryLoader(path)
        assertTrue(path.toFile().exists(), "Default file should be generated")
        val result = loader.load()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun modelElement_defaultIsEmpty() {
        val loader = loaderWith("STONE:\n  hardness: 5\n  solid: true\n  minimapColor: [0, 0, 0]\n")
        val result = loader.load()
        assertEquals("", result[BlockType.STONE]?.modelElement)
    }

    @Test
    fun modelElement_customValue_loaded() {
        val loader = loaderWith("STONE:\n  hardness: 5\n  solid: true\n  minimapColor: [0, 0, 0]\n  modelElement: GRANITE\n")
        val result = loader.load()
        assertEquals("GRANITE", result[BlockType.STONE]?.modelElement)
    }
}
