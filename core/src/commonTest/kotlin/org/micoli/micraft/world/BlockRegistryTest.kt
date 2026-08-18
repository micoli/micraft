package org.micoli.micraft.world

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType

class BlockRegistryTest {

    @AfterTest
    fun resetToDefaults() {
        BlockRegistry.load(emptyMap())
    }

    @Test
    fun wireIndex_airIsAlwaysZero() {
        assertEquals(0, BlockRegistry.wireIndex(BlockType.AIR))
    }

    @Test
    fun byWireIndex_isInverseOfWireIndex() {
        for (type in BlockRegistry.all()) {
            assertEquals(type, BlockRegistry.byWireIndex(BlockRegistry.wireIndex(type)))
        }
    }

    @Test
    fun byWireIndex_unknownIndex_fallsBackToAir() {
        assertEquals(BlockType.AIR, BlockRegistry.byWireIndex(9999))
    }

    @Test
    fun get_unknownBlock_returnsDefaultDefinition() {
        val def = BlockRegistry.get(BlockType("NOT_A_REAL_BLOCK"))
        assertEquals(BlockDefinition(), def)
    }

    @Test
    fun get_knownBlock_returnsConfiguredHardness() {
        assertEquals(5f, BlockRegistry.get(BlockType.STONE).hardness)
    }

    @Test
    fun load_addsCustomBlockAndAssignsWireIndex() {
        val custom = BlockType("CUSTOM_TEST_BLOCK")
        BlockRegistry.load(mapOf(custom to BlockDefinition(hardness = 42f)))
        assertEquals(42f, BlockRegistry.get(custom).hardness)
        assertTrue(BlockRegistry.all().contains(custom))
    }

    @Test
    fun load_overridesExistingBlockDefinition() {
        BlockRegistry.load(mapOf(BlockType.STONE to BlockDefinition(hardness = 100f)))
        assertEquals(100f, BlockRegistry.get(BlockType.STONE).hardness)
    }

    @Test
    fun load_resetsToDefaultsWhenGivenEmptyMap() {
        BlockRegistry.load(mapOf(BlockType.STONE to BlockDefinition(hardness = 100f)))
        BlockRegistry.load(emptyMap())
        assertEquals(5f, BlockRegistry.get(BlockType.STONE).hardness)
    }

    @Test
    fun orderedList_sizeMatchesAllBlocks() {
        assertEquals(BlockRegistry.all().size, BlockRegistry.orderedList().size)
    }

    @Test
    fun hardnessExtension_unbreakableBlockIsMaxValue() {
        assertNotEquals(-1f, BlockType.BEDROCK.hardness)
        assertEquals(Float.MAX_VALUE, BlockType.BEDROCK.hardness)
    }

    @Test
    fun load_withWireOrder_assignsIdsByGivenOrder() {
        val a = BlockType("ZZZ_LAST")
        val b = BlockType("AAA_FIRST")
        BlockRegistry.load(
            mapOf(a to BlockDefinition(), b to BlockDefinition()), wireOrder = listOf(a, b))
        assertEquals(1, BlockRegistry.wireIndex(a))
        assertEquals(2, BlockRegistry.wireIndex(b))
    }

    @Test
    fun load_withWireOrder_unlistedTypeStillGetsASlot() {
        val known = BlockType("KNOWN_IN_ORDER")
        val orphan = BlockType("MISSING_FROM_ORDER")
        BlockRegistry.load(
            mapOf(known to BlockDefinition(), orphan to BlockDefinition()),
            wireOrder = listOf(known))
        assertTrue(BlockRegistry.all().contains(orphan))
        assertEquals(orphan, BlockRegistry.byWireIndex(BlockRegistry.wireIndex(orphan)))
    }
}
