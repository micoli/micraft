package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SectionedBytesTest {
    private fun volume() = SectionedBytes(size = 40, sectionSize = 8)

    @Test
    fun `reads zero everywhere and allocates nothing until a non-zero byte is written`() {
        val v = volume()
        v[3] = 0

        assertEquals(0, v[39])
        assertEquals(0, v.allocatedBytes())
        assertTrue(v.isAllZero())
    }

    @Test
    fun `allocates only the section a byte lands in`() {
        val v = volume()
        v[17] = 5

        assertEquals(5, v[17])
        assertEquals(8, v.allocatedBytes())
        assertEquals(17, v.highestNonZeroIndex())
    }

    @Test
    fun `withByte leaves the original untouched and shares the other sections`() {
        val v = volume()
        v[1] = 1
        v[20] = 2

        val edited = v.withByte(21, 3)

        assertEquals(0, v[21])
        assertEquals(3, edited[21])
        assertEquals(1, edited[1])
        assertFalse(v.contentEquals(edited))
    }

    @Test
    fun `builds from a shorter flat array and round-trips it`() {
        val flat =
            ByteArray(20).also {
                it[2] = 7
                it[19] = 9
            }

        val v = SectionedBytes.of(flat, size = 40, sectionSize = 8)

        assertEquals(16, v.allocatedBytes())
        assertEquals(flat.toList(), v.toByteArray(20).toList())
        assertEquals(0, v[39])
    }

    @Test
    fun `equal content gives equal hashes whatever the allocation`() {
        val a = volume().also { it[5] = 4 }
        val b =
            volume().also {
                it[30] = 1
                it[5] = 4
                it[30] = 0
            }

        assertTrue(a.contentEquals(b))
        assertEquals(a.contentHashCode(), b.contentHashCode())
    }

    @Test
    fun `works with a section size that is not a power of two`() {
        val v = SectionedBytes(size = 30, sectionSize = 7)
        v[13] = 3

        assertEquals(3, v[13])
        assertEquals(7, v.allocatedBytes())
    }
}
