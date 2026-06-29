package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockRegistryLoaderTest {

    private data class LoaderContext(
        val loader: BlockRegistryLoader,
        val dataDir: java.nio.file.Path,
    )

    private fun loaderWithBlocks(
        blocks: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val resourcesDir = createTempDirectory("resources_blocks")
        val dataDir = createTempDirectory("data_blocks")
        val outputFile = createTempFile(suffix = ".yaml")
        blocks.forEach { (name, yaml) ->
            val blockDir = resourcesDir.resolve(name)
            blockDir.toFile().mkdir()
            blockDir.resolve("$name.yaml").writeText(yaml)
        }
        overrides.forEach { (name, yaml) ->
            val overrideDir = dataDir.resolve(name)
            overrideDir.toFile().mkdir()
            overrideDir.resolve("$name.yaml").writeText(yaml)
        }
        return LoaderContext(BlockRegistryLoader(resourcesDir, dataDir, outputFile), dataDir)
    }

    @Test
    fun validYaml_loadsAllBlocks() {
        val (loader) =
            loaderWithBlocks(
                mapOf(
                    "STONE" to "hardness: 5\nsolid: true\nminimapColor: [136, 136, 136]\n",
                    "DIRT" to "hardness: 3\nsolid: true\nminimapColor: [122, 92, 46]\n",
                ))
        val result = loader.load()
        assertEquals(2, result.size)
        assertEquals(5f, result[BlockType.STONE]?.hardness)
        assertEquals(true, result[BlockType.DIRT]?.solid)
    }

    @Test
    fun bedrock_hardnessMinusOne_isUnbreakable() {
        val (loader) =
            loaderWithBlocks(
                mapOf("BEDROCK" to "hardness: -1\nsolid: true\nminimapColor: [0, 0, 0]\n"))
        val result = loader.load()
        val def = result[BlockType.BEDROCK]!!
        assertEquals(-1f, def.hardness)
        BlockRegistry.load(result)
        assertEquals(Float.MAX_VALUE, BlockType.BEDROCK.hardness)
    }

    @Test
    fun unknownBlockKey_isLoaded() {
        val (loader) =
            loaderWithBlocks(
                mapOf("NOT_A_BLOCK" to "hardness: 1\nsolid: true\nminimapColor: [0, 0, 0]\n"))
        val result = loader.load()
        assertEquals(1, result.size)
        assertEquals(1f, result[BlockType("NOT_A_BLOCK")]?.hardness)
    }

    @Test
    fun invalidYaml_skipsBlock() {
        val (loader) =
            loaderWithBlocks(
                mapOf(
                    "STONE" to "hardness: 5\nsolid: true\nminimapColor: [136, 136, 136]\n",
                    "DIRT" to "this is not: [valid yaml: }",
                ))
        val result = loader.load()
        assertEquals(1, result.size)
        assertTrue(result.containsKey(BlockType.STONE))
        assertFalse(result.containsKey(BlockType.DIRT))
    }

    @Test
    fun blocksYaml_generatedFromResources() {
        val (loader) =
            loaderWithBlocks(
                mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n"))
        val result = loader.load()
        assertTrue(result.isNotEmpty())
        assertTrue(result.containsKey(BlockType.STONE))
    }

    @Test
    fun modelElement_defaultIsEmpty() {
        val (loader) =
            loaderWithBlocks(
                mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n"))
        val result = loader.load()
        assertEquals("", result[BlockType.STONE]?.modelElement)
    }

    @Test
    fun modelElement_customValue_loaded() {
        val (loader) =
            loaderWithBlocks(
                mapOf(
                    "STONE" to
                        "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\nmodelElement: GRANITE\n"))
        val result = loader.load()
        assertEquals("GRANITE", result[BlockType.STONE]?.modelElement)
    }

    @Test
    fun newFields_defaultValues() {
        val (loader) =
            loaderWithBlocks(
                mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n"))
        val result = loader.load()
        val def = result[BlockType.STONE]!!
        assertEquals(false, def.replaceable)
        assertEquals(false, def.vegetationHost)
        assertEquals(true, def.treeAllowed)
    }

    @Test
    fun newFields_explicitValues() {
        val (loader) =
            loaderWithBlocks(
                mapOf(
                    "GRASS" to
                        "hardness: 3\nsolid: true\nminimapColor: [74, 122, 40]\nvegetationHost: true\n",
                    "SAND" to
                        "hardness: 2\nsolid: true\nminimapColor: [212, 200, 122]\ntreeAllowed: false\n",
                    "AIR" to
                        "hardness: 0\nsolid: false\ntransparent: true\nminimapColor: [10, 10, 30]\nreplaceable: true\n",
                ))
        val result = loader.load()
        assertEquals(true, result[BlockType.GRASS]?.vegetationHost)
        assertEquals(false, result[BlockType.SAND]?.treeAllowed)
        assertEquals(true, result[BlockType.AIR]?.replaceable)
    }

    @Test
    fun dataOverride_emptyFile_writesBackDefaults() {
        val (loader, dataDir) =
            loaderWithBlocks(
                blocks =
                    mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [136, 136, 136]\n"),
                overrides = mapOf("STONE" to ""),
            )
        val result = loader.load()
        val def = result[BlockType.STONE]!!
        assertEquals(5f, def.hardness)
        assertEquals(true, def.solid)
        val writtenBack = dataDir.resolve("STONE/STONE.yaml").readText()
        assertTrue(writtenBack.contains("hardness"), "Write-back must contain all keys")
        assertTrue(writtenBack.contains("minimapColor"))
    }

    @Test
    fun dataOverride_mergesAndWritesBack() {
        val (loader, dataDir) =
            loaderWithBlocks(
                blocks =
                    mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [136, 136, 136]\n"),
                overrides = mapOf("STONE" to "hardness: 99\n"),
            )
        val result = loader.load()
        val def = result[BlockType.STONE]!!
        assertEquals(99f, def.hardness)
        assertEquals(true, def.solid)
        assertEquals(listOf(136, 136, 136), def.minimapColor)
        val writtenBack = dataDir.resolve("STONE/STONE.yaml").readText()
        assertTrue(writtenBack.contains("99"), "Override value preserved in write-back")
        assertTrue(writtenBack.contains("minimapColor"), "Missing keys added in write-back")
    }

    @Test
    fun dataOverride_noFile_notCreated() {
        val (_, dataDir) =
            loaderWithBlocks(
                blocks = mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n"))
        assertFalse(dataDir.resolve("STONE/STONE.yaml").toFile().exists())
    }
}
