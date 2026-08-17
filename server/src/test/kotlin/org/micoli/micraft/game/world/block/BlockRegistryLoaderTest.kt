package org.micoli.micraft.game.world.block

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.game.world.rail.RailConnectionPoint

class BlockRegistryLoaderTest {

    private data class LoaderContext(
        val loader: BlockRegistryLoader,
        val dataDir: Path,
    )

    private fun loaderWithBlocks(
        blocks: Map<String, String>,
        overrides: Map<String, String> = emptyMap(),
    ): LoaderContext {
        val resourcesDir = createTempDirectory("resources_blocks")
        val dataDir = createTempDirectory("data_blocks")
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
        return LoaderContext(BlockRegistryLoader(resourcesDir, dataDir), dataDir)
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
    fun plainColorable_defaultsToFalse_andIsReadFromYaml() {
        val (loader) =
            loaderWithBlocks(
                mapOf(
                    "STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n",
                    "LEGO_BRICK" to
                        "hardness: 1\nsolid: true\nminimapColor: [220, 50, 50]\nplainColorable: true\n",
                ))
        val result = loader.load()
        assertEquals(false, result[BlockType.STONE]?.plainColorable)
        assertEquals(true, result[BlockType("LEGO_BRICK")]?.plainColorable)
    }

    @Test
    fun plainColorable_canBeToggledByDataOverride() {
        val (loader) =
            loaderWithBlocks(
                blocks = mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n"),
                overrides = mapOf("STONE" to "plainColorable: true\n"),
            )
        assertEquals(true, loader.load()[BlockType.STONE]?.plainColorable)
    }

    @Test
    fun isCubic_defaultsToTrue_andCanBeSetFalse() {
        val (loader) =
            loaderWithBlocks(
                mapOf(
                    "STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n",
                    "LEGO_SLOPE" to
                        "hardness: 1\nsolid: true\nisCubic: false\nminimapColor: [180, 50, 50]\n",
                ))
        val result = loader.load()
        assertEquals(true, result[BlockType.STONE]?.isCubic)
        val slope = result[BlockType("LEGO_SLOPE")]!!
        assertEquals(true, slope.solid)
        assertEquals(false, slope.isCubic)
    }

    @Test
    fun rotatable_defaultsToFalse_andCanBeSetTrue() {
        val (loader) =
            loaderWithBlocks(
                mapOf(
                    "STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n",
                    "RAIL_STRAIGHT" to
                        "hardness: 1\nsolid: true\nisCubic: false\nrotatable: true\nminimapColor: [110, 110, 120]\n",
                ))
        val result = loader.load()
        assertEquals(false, result[BlockType.STONE]?.rotatable)
        val rail = result[BlockType("RAIL_STRAIGHT")]!!
        assertEquals(true, rail.rotatable)
        assertEquals(false, rail.isCubic)
    }

    @Test
    fun connections_defaultsToEmpty_andCanBeSet() {
        val (loader) =
            loaderWithBlocks(
                mapOf(
                    "STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n",
                    "RAIL_STRAIGHT" to
                        "hardness: 1\nsolid: true\nisCubic: false\nrotatable: true\nminimapColor: [110, 110, 120]\nconnections: [[NORTH, SOUTH]]\n",
                ))
        val result = loader.load()
        assertEquals(emptyList(), result[BlockType.STONE]?.connections)
        assertEquals(
            listOf(
                listOf(RailConnectionPoint(Direction.NORTH), RailConnectionPoint(Direction.SOUTH))),
            result[BlockType("RAIL_STRAIGHT")]?.connections)
    }

    @Test
    fun connections_canBeSetByDataOverride() {
        val (loader) =
            loaderWithBlocks(
                blocks = mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n"),
                overrides = mapOf("STONE" to "connections: [[EAST, WEST]]\n"),
            )
        assertEquals(
            listOf(
                listOf(RailConnectionPoint(Direction.EAST), RailConnectionPoint(Direction.WEST))),
            loader.load()[BlockType.STONE]?.connections)
    }

    @Test
    fun isCubic_canBeToggledByDataOverride() {
        val (loader) =
            loaderWithBlocks(
                blocks = mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n"),
                overrides = mapOf("STONE" to "isCubic: false\n"),
            )
        val def = loader.load()[BlockType.STONE]!!
        assertEquals(false, def.isCubic)
        assertEquals(true, def.solid)
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
    fun dataOverride_reload_isIdempotent_doesNotDuplicateComments() {
        val (loader, dataDir) =
            loaderWithBlocks(
                blocks =
                    mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [136, 136, 136]\n"),
                overrides = mapOf("STONE" to "hardness: 99\n"),
            )
        loader.reload()
        val afterFirstReload = dataDir.resolve("STONE/STONE.yaml").readText()
        loader.reload()
        val afterSecondReload = dataDir.resolve("STONE/STONE.yaml").readText()
        assertEquals(
            afterFirstReload, afterSecondReload, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("minimapColor").findAll(afterSecondReload).count())
    }

    @Test
    fun dataOverride_invalidYaml_leftUntouched() {
        val (loader, dataDir) =
            loaderWithBlocks(
                blocks =
                    mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [136, 136, 136]\n"),
                overrides = mapOf("STONE" to "this is not: [valid yaml: }"),
            )
        loader.reload()
        assertEquals("this is not: [valid yaml: }", dataDir.resolve("STONE/STONE.yaml").readText())
    }

    @Test
    fun dataOverride_noFile_notCreated() {
        val (_, dataDir) =
            loaderWithBlocks(
                blocks = mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n"))
        assertFalse(dataDir.resolve("STONE/STONE.yaml").toFile().exists())
    }

    @Test
    fun drops_absentInBase_notInDropTable() {
        val (loader) =
            loaderWithBlocks(
                mapOf("STONE" to "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\n"))
        val dropTable = loader.loadDropTable()
        assertFalse(dropTable.containsKey(BlockType.STONE))
    }

    @Test
    fun drops_presentInBase_loadedIntoDropTable() {
        val (loader) =
            loaderWithBlocks(
                mapOf(
                    "STONE" to
                        "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\ndrops:\n- item: COBBLESTONE\n  dropRate: 100\n  minCount: 1\n  maxCount: 1\n"))
        val dropTable = loader.loadDropTable()
        val entries = dropTable.getValue(BlockType.STONE)
        assertEquals(1, entries.size)
        assertEquals(ItemType("COBBLESTONE"), entries[0].item)
        assertEquals(100, entries[0].dropRate)
    }

    @Test
    fun drops_override_replacesWholeList() {
        val (loader) =
            loaderWithBlocks(
                blocks =
                    mapOf(
                        "STONE" to
                            "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\ndrops:\n- item: COBBLESTONE\n  dropRate: 100\n  minCount: 1\n  maxCount: 1\n"),
                overrides =
                    mapOf(
                        "STONE" to
                            "drops:\n- item: FLINT\n  dropRate: 50\n  minCount: 1\n  maxCount: 2\n"),
            )
        val entries = loader.loadDropTable().getValue(BlockType.STONE)
        assertEquals(1, entries.size)
        assertEquals(ItemType("FLINT"), entries[0].item)
        assertEquals(50, entries[0].dropRate)
    }

    @Test
    fun drops_override_absentKey_inheritsBaseList() {
        val (loader) =
            loaderWithBlocks(
                blocks =
                    mapOf(
                        "STONE" to
                            "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\ndrops:\n- item: COBBLESTONE\n  dropRate: 100\n  minCount: 1\n  maxCount: 1\n"),
                overrides = mapOf("STONE" to "hardness: 99\n"),
            )
        val entries = loader.loadDropTable().getValue(BlockType.STONE)
        assertEquals(1, entries.size)
        assertEquals(ItemType("COBBLESTONE"), entries[0].item)
    }

    @Test
    fun drops_writeback_missingFieldAppendedAsComment() {
        val (loader, dataDir) =
            loaderWithBlocks(
                blocks =
                    mapOf(
                        "STONE" to
                            "hardness: 5\nsolid: true\nminimapColor: [0, 0, 0]\ndrops:\n- item: COBBLESTONE\n  dropRate: 100\n  minCount: 1\n  maxCount: 1\n"),
                overrides = mapOf("STONE" to "hardness: 99\n"),
            )
        loader.loadDropTable()
        val writtenBack = dataDir.resolve("STONE/STONE.yaml").readText()
        assertTrue(writtenBack.contains("drops"), "Missing drops key must be added as comment")
    }
}
