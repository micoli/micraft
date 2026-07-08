package org.micoli.micraft.game.drop

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.block.BlockRegistryLoader

class DropConfigTest {

    private data class BlockDirs(val resourcesDir: Path, val dataDir: Path)

    private fun writeBlock(dirs: BlockDirs, name: String, yaml: String) {
        val blockDir = dirs.resourcesDir.resolve(name)
        blockDir.toFile().mkdirs()
        blockDir.resolve("$name.yaml").writeText(yaml)
    }

    private fun newBlockDirs(): BlockDirs =
        BlockDirs(createTempDirectory("resources_blocks"), createTempDirectory("data_blocks"))

    private fun configWith(blocks: Map<String, String>): DropConfig {
        val dirs = newBlockDirs()
        blocks.forEach { (name, yaml) -> writeBlock(dirs, name, yaml) }
        return DropConfig(BlockRegistryLoader(dirs.resourcesDir, dirs.dataDir))
    }

    private fun baseYaml(drops: String = ""): String =
        "hardness: 1\nsolid: true\nminimapColor: [0, 0, 0]\n$drops"

    @Test
    fun unknownBlock_returnsEmpty() {
        val config = configWith(emptyMap())
        val drops = config.rollDrops(BlockType.DIRT)
        assertTrue(drops.isEmpty())
    }

    @Test
    fun knownBlock_100pctRate_alwaysDrops() {
        val config =
            configWith(
                mapOf(
                    "STONE" to
                        baseYaml(
                            "drops:\n- item: COBBLESTONE\n  dropRate: 100\n  minCount: 1\n  maxCount: 1\n")))
        repeat(20) {
            val drops = config.rollDrops(BlockType.STONE)
            assertEquals(1, drops.size)
            assertEquals(ItemType("COBBLESTONE"), drops[0].first)
            assertEquals(1, drops[0].second)
        }
    }

    @Test
    fun dropRate_0pct_neverDrops() {
        val config =
            configWith(
                mapOf(
                    "STONE" to
                        baseYaml(
                            "drops:\n- item: COBBLESTONE\n  dropRate: 0\n  minCount: 1\n  maxCount: 1\n")))
        repeat(50) {
            val drops = config.rollDrops(BlockType.STONE)
            assertTrue(drops.isEmpty(), "Expected no drops with 0% rate")
        }
    }

    @Test
    fun countInRange() {
        val config =
            configWith(
                mapOf(
                    "SNOW" to
                        baseYaml(
                            "drops:\n- item: SNOWBALL\n  dropRate: 100\n  minCount: 1\n  maxCount: 4\n")))
        repeat(50) {
            val drops = config.rollDrops(BlockType.SNOW)
            assertEquals(1, drops.size)
            val count = drops[0].second
            assertTrue(count in 1..4, "Expected count in [1,4], got $count")
        }
    }

    @Test
    fun fixedCount_whenMinEqualsMax() {
        val config =
            configWith(
                mapOf(
                    "DIRT" to
                        baseYaml(
                            "drops:\n- item: DIRT\n  dropRate: 100\n  minCount: 3\n  maxCount: 3\n")))
        repeat(10) {
            val drops = config.rollDrops(BlockType.DIRT)
            assertEquals(3, drops[0].second)
        }
    }

    @Test
    fun reload_picksUpNewConfig() {
        val dirs = newBlockDirs()
        writeBlock(dirs, "STONE", baseYaml())
        val loader = BlockRegistryLoader(dirs.resourcesDir, dirs.dataDir)
        val config = DropConfig(loader)
        assertTrue(config.rollDrops(BlockType.STONE).isEmpty())
        writeBlock(
            dirs,
            "STONE",
            baseYaml(
                "drops:\n- item: COBBLESTONE\n  dropRate: 100\n  minCount: 1\n  maxCount: 1\n"))
        config.reload()
        val drops = config.rollDrops(BlockType.STONE)
        assertEquals(1, drops.size)
    }
}
