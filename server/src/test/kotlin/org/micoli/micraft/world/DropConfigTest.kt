package org.micoli.micraft.world

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DropConfigTest {

    private fun configWith(yaml: String): DropConfig {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText(yaml)
        return DropConfig(tmp)
    }

    @Test
    fun unknownBlock_returnsEmpty() {
        val config = configWith("{}\n")
        val drops = config.rollDrops(BlockType.DIRT)
        assertTrue(drops.isEmpty())
    }

    @Test
    fun knownBlock_100pctRate_alwaysDrops() {
        val config =
            configWith(
                "STONE:\n  - item: COBBLESTONE\n    dropRate: 100\n    minCount: 1\n    maxCount: 1\n")
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
                "STONE:\n  - item: COBBLESTONE\n    dropRate: 0\n    minCount: 1\n    maxCount: 1\n")
        repeat(50) {
            val drops = config.rollDrops(BlockType.STONE)
            assertTrue(drops.isEmpty(), "Expected no drops with 0% rate")
        }
    }

    @Test
    fun countInRange() {
        val config =
            configWith(
                "SNOW:\n  - item: SNOWBALL\n    dropRate: 100\n    minCount: 1\n    maxCount: 4\n")
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
                "DIRT:\n  - item: DIRT\n    dropRate: 100\n    minCount: 3\n    maxCount: 3\n")
        repeat(10) {
            val drops = config.rollDrops(BlockType.DIRT)
            assertEquals(3, drops[0].second)
        }
    }

    @Test
    fun reload_picksUpNewConfig() {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText("{}\n")
        val config = DropConfig(tmp)
        assertTrue(config.rollDrops(BlockType.STONE).isEmpty())
        tmp.writeText(
            "STONE:\n  - item: COBBLESTONE\n    dropRate: 100\n    minCount: 1\n    maxCount: 1\n")
        config.reload()
        val drops = config.rollDrops(BlockType.STONE)
        assertEquals(1, drops.size)
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val config = configWith("this is: not: valid: yaml\n  content\n")
        // With invalid YAML, parseTable falls back to DEFAULT_DROPS which includes STONE →
        // COBBLESTONE
        val drops = config.rollDrops(BlockType.STONE)
        assertTrue(drops.isNotEmpty())
    }
}
