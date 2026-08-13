package org.micoli.micraft.game.world.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneTest {
    private fun scene(width: Int, height: Int, depth: Int) =
        Scene(
            id = "s",
            name = "Scene",
            width = width,
            height = height,
            depth = depth,
            ownerName = "Alice",
            createdAt = 0,
            blocks = ByteArray(width * height * depth),
            states = ByteArray(width * height * depth),
        )

    @Test
    fun idx_isXMajorYMidZMinor() {
        val s = scene(2, 3, 4)
        // Matches Chunk.index convention: x*height*depth + y*depth + z.
        assertEquals(0, s.idx(0, 0, 0))
        assertEquals(1, s.idx(0, 0, 1))
        assertEquals(4, s.idx(0, 1, 0))
        assertEquals(12, s.idx(1, 0, 0))
    }

    @Test
    fun contains_insideAndOutsideBounds() {
        val s = scene(2, 3, 4)
        assertTrue(s.contains(0, 0, 0))
        assertTrue(s.contains(1, 2, 3))
        assertFalse(s.contains(2, 0, 0))
        assertFalse(s.contains(0, 3, 0))
        assertFalse(s.contains(0, 0, 4))
        assertFalse(s.contains(-1, 0, 0))
    }

    @Test
    fun setBlock_thenBlockAtAndStateAt_returnStoredValues() {
        val s = scene(2, 2, 2)
        s.setBlock(1, 1, 1, type = 5, state = 3)
        assertEquals(5.toByte(), s.blockAt(1, 1, 1))
        assertEquals(3.toByte(), s.stateAt(1, 1, 1))
        // Untouched cell remains AIR (0).
        assertEquals(0.toByte(), s.blockAt(0, 0, 0))
    }
}
