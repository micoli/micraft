package org.micoli.micraft.game.world.actionblock

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldPersistence

class ActionBlockRegistryTest {
    private val solid: (BlockPos) -> BlockType = { BlockType.STONE }
    private val air: (BlockPos) -> BlockType = { BlockType.AIR }

    @Test
    fun create_generatesUniqueSequentialNames() {
        val r = ActionBlockRegistry(null)
        val a = r.create(BlockPos(0, 64, 0), "Alice", solid)
        val b = r.create(BlockPos(1, 64, 0), "Alice", solid)
        assertEquals("actionblock-1", a?.name)
        assertEquals("actionblock-2", b?.name)
    }

    @Test
    fun create_onAir_returnsNull() {
        val r = ActionBlockRegistry(null)
        assertNull(r.create(BlockPos(0, 64, 0), "Alice", air))
    }

    @Test
    fun upsert_rejectsDuplicateName() {
        val r = ActionBlockRegistry(null)
        r.create(BlockPos(0, 64, 0), "Alice", solid)
        val b = r.create(BlockPos(1, 64, 0), "Alice", solid)!!
        val result = r.upsert(b.pos, "actionblock-1", "", "", "", emptyMap())
        assertEquals(ActionBlockRegistry.UpsertResult.NAME_TAKEN, result)
    }

    @Test
    fun upsert_renameReindexesLookup() {
        val r = ActionBlockRegistry(null)
        val a = r.create(BlockPos(0, 64, 0), "Alice", solid)!!
        r.upsert(a.pos, "door", "", "", "", emptyMap())
        assertNull(r.byName("actionblock-1"))
        assertEquals(a.pos, r.byName("door")?.pos)
    }

    @Test
    fun setVariable_persistsOnBlock() {
        val r = ActionBlockRegistry(null)
        val a = r.create(BlockPos(0, 64, 0), "Alice", solid)!!
        assertTrue(r.setVariable(a.name, "count", "3"))
        assertEquals("3", r.byName(a.name)?.variables?.get("count"))
    }

    @Test
    fun removeAt_dropsNameAndPos() {
        val r = ActionBlockRegistry(null)
        val a = r.create(BlockPos(0, 64, 0), "Alice", solid)!!
        assertNotNull(r.removeAt(a.pos))
        assertNull(r.byName(a.name))
        assertNull(r.at(a.pos))
    }

    @Test
    fun pruneAgainst_removesEntriesThatBecameAir() {
        val r = ActionBlockRegistry(null)
        val a = r.create(BlockPos(0, 64, 0), "Alice", solid)!!
        val stale = r.pruneAgainst(air)
        assertEquals(listOf(a.pos), stale)
        assertNull(r.at(a.pos))
    }

    @Test
    fun persistence_roundTrip() {
        val dir = createTempDirectory("actionblocks")
        val p = WorldPersistence(dir)
        ActionBlockRegistry(p).apply {
            val a = create(BlockPos(2, 64, 3), "Alice", solid)!!
            setVariable(a.name, "k", "v")
        }
        val reloaded = ActionBlockRegistry(p)
        val block = reloaded.at(BlockPos(2, 64, 3))
        assertNotNull(block)
        assertEquals("v", block.variables["k"])
    }
}
