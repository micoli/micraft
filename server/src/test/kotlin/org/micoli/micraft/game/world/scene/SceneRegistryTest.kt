package org.micoli.micraft.game.world.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneRegistryTest {
    private fun registry() = SceneRegistry(null)

    @Test
    fun create_allocatesAirFilledBuffersOfRequestedSize() {
        val r = registry()
        val scene = r.create(name = "Room", width = 2, height = 3, depth = 4, ownerName = "Alice")
        assertEquals(2, scene.width)
        assertEquals(3, scene.height)
        assertEquals(4, scene.depth)
        assertEquals(2 * 3 * 4, scene.blocks.size)
        assertEquals(2 * 3 * 4, scene.states.size)
        assertTrue(scene.blocks.all { it == 0.toByte() })
        assertEquals(scene, r.get(scene.id))
    }

    @Test
    fun get_unknownId_returnsNull() {
        assertNull(registry().get("nope"))
    }

    @Test
    fun all_returnsAllCreatedScenes() {
        val r = registry()
        val a = r.create(name = "A", width = 1, height = 1, depth = 1, ownerName = "Alice")
        val b = r.create(name = "B", width = 1, height = 1, depth = 1, ownerName = "Alice")
        assertEquals(setOf(a.id, b.id), r.all().map { it.id }.toSet())
    }

    @Test
    fun rename_updatesName_keepsDimensionsAndBuffers() {
        val r = registry()
        val scene = r.create(name = "Old", width = 2, height = 2, depth = 2, ownerName = "Alice")
        scene.setBlock(0, 0, 0, type = 7, state = 1)
        val renamed = r.rename(scene.id, "New")
        assertEquals("New", renamed?.name)
        assertEquals(scene.width, renamed?.width)
        assertEquals(7.toByte(), renamed?.blockAt(0, 0, 0))
    }

    @Test
    fun rename_unknownId_returnsNull() {
        assertNull(registry().rename("nope", "New"))
    }

    @Test
    fun setBlock_insideBounds_mutatesBufferAndReturnsTrue() {
        val r = registry()
        val scene = r.create(name = "Room", width = 2, height = 2, depth = 2, ownerName = "Alice")
        assertTrue(r.setBlock(scene.id, 1, 1, 1, type = 9, state = 2))
        assertEquals(9.toByte(), r.get(scene.id)?.blockAt(1, 1, 1))
        assertEquals(2.toByte(), r.get(scene.id)?.stateAt(1, 1, 1))
    }

    @Test
    fun setBlock_outOfBounds_isRejected() {
        val r = registry()
        val scene = r.create(name = "Room", width = 2, height = 2, depth = 2, ownerName = "Alice")
        assertFalse(r.setBlock(scene.id, 2, 0, 0, type = 9, state = 0))
        assertFalse(r.setBlock(scene.id, -1, 0, 0, type = 9, state = 0))
    }

    @Test
    fun setBlock_unknownId_returnsFalse() {
        assertFalse(registry().setBlock("nope", 0, 0, 0, type = 1, state = 0))
    }

    @Test
    fun resize_grow_keepsExistingBlocksAtOriginAndPadsWithAir() {
        val r = registry()
        val scene = r.create(name = "Room", width = 2, height = 2, depth = 2, ownerName = "Alice")
        r.setBlock(scene.id, 1, 1, 1, type = 4, state = 0)
        val resized = r.resize(scene.id, width = 3, height = 3, depth = 3)
        assertEquals(3, resized?.width)
        assertEquals(3 * 3 * 3, resized?.blocks?.size)
        assertEquals(4.toByte(), resized?.blockAt(1, 1, 1))
        // New region beyond the old bounds stays air.
        assertEquals(0.toByte(), resized?.blockAt(2, 2, 2))
    }

    @Test
    fun resize_shrink_dropsBlocksOutsideNewBounds() {
        val r = registry()
        val scene = r.create(name = "Room", width = 3, height = 3, depth = 3, ownerName = "Alice")
        r.setBlock(scene.id, 2, 2, 2, type = 6, state = 0)
        r.setBlock(scene.id, 0, 0, 0, type = 6, state = 0)
        val resized = r.resize(scene.id, width = 1, height = 1, depth = 1)
        assertEquals(1, resized?.width)
        assertEquals(1, resized?.blocks?.size)
        assertEquals(6.toByte(), resized?.blockAt(0, 0, 0))
    }

    @Test
    fun resize_unknownId_returnsNull() {
        assertNull(registry().resize("nope", 1, 1, 1))
    }

    @Test
    fun delete_removesScene() {
        val r = registry()
        val scene = r.create(name = "Room", width = 1, height = 1, depth = 1, ownerName = "Alice")
        assertTrue(r.delete(scene.id))
        assertNull(r.get(scene.id))
    }

    @Test
    fun delete_unknownId_returnsFalse() {
        assertFalse(registry().delete("nope"))
    }
}
