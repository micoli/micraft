package org.micoli.micraft.game.world.block

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockIdRegistryLoaderTest {

    private fun loaderWithShipped(shipped: String): Pair<BlockIdRegistryLoader, Path> {
        val resourcesFile = createTempDirectory("block_ids_resources").resolve("block_ids.yaml")
        resourcesFile.writeText(shipped)
        val dataFile = createTempDirectory("block_ids_data").resolve("block_ids.yaml")
        return BlockIdRegistryLoader(dataFile, resourcesFile) to dataFile
    }

    @Test
    fun firstLoad_usesShippedIdsAndAssignsAirZero() {
        val (loader) = loaderWithShipped("blocks:\n  AIR: 0\n  STONE: 1\n")
        val result = loader.load(setOf("STONE"))
        assertEquals(0, result["AIR"])
        assertEquals(1, result["STONE"])
    }

    @Test
    fun newlyDiscoveredBlock_getsNextFreeId_appendedNotInserted() {
        val (loader) = loaderWithShipped("blocks:\n  AIR: 0\n  STONE: 1\n  DIRT: 2\n")
        val result = loader.load(setOf("STONE", "DIRT", "NEW_BLOCK"))
        assertEquals(0, result["AIR"])
        assertEquals(1, result["STONE"])
        assertEquals(2, result["DIRT"])
        assertEquals(3, result["NEW_BLOCK"])
    }

    @Test
    fun idsStayStableAcrossReloadsOnceAssigned() {
        val (loader) = loaderWithShipped("blocks:\n  AIR: 0\n  STONE: 1\n")
        val first = loader.load(setOf("STONE", "NEW_BLOCK"))
        val second = loader.load(setOf("STONE", "NEW_BLOCK"))
        assertEquals(first, second)
    }

    @Test
    fun addingAnAlphabeticallyEarlierBlock_doesNotShiftExistingIds() {
        val (loader) = loaderWithShipped("blocks:\n  AIR: 0\n  STONE: 1\n")
        loader.load(setOf("STONE"))
        val afterAddingEarlierBlock = loader.load(setOf("STONE", "AAA_NEW_BLOCK"))
        assertEquals(0, afterAddingEarlierBlock["AIR"])
        assertEquals(1, afterAddingEarlierBlock["STONE"])
        assertEquals(2, afterAddingEarlierBlock["AAA_NEW_BLOCK"])
    }
}
