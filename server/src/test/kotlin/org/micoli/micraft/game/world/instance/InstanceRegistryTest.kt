package org.micoli.micraft.game.world.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.ChunkPos

class InstanceRegistryTest {
    private fun registry() = InstanceRegistry(null)

    @Test
    fun create_thenZoneAtInsideChunkAndYRange_findsZone() {
        val r = registry()
        val zone =
            r.create(
                name = "Arena",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0)),
                ownerName = "Alice",
            )
        assertEquals(zone, r.zoneAt(5, 5, 5))
    }

    @Test
    fun zoneAt_yOutsideRange_returnsNull() {
        val r = registry()
        r.create(
            name = "Arena",
            yMin = 0,
            yMax = 10,
            chunks = setOf(ChunkPos(0, 0)),
            ownerName = "Alice",
        )
        assertNull(r.zoneAt(5, 11, 5))
        assertNull(r.zoneAt(5, -1, 5))
    }

    @Test
    fun zoneAt_outsideSelectedChunk_returnsNull() {
        val r = registry()
        r.create(
            name = "Arena",
            yMin = 0,
            yMax = 10,
            chunks = setOf(ChunkPos(0, 0)),
            ownerName = "Alice",
        )
        // chunk (1,0) covers x in 16..31 — not part of the zone
        assertNull(r.zoneAt(20, 5, 5))
    }

    @Test
    fun zoneAt_nonContiguousChunkSet_bothChunksMatch() {
        val r = registry()
        val zone =
            r.create(
                name = "Arena",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0), ChunkPos(5, 5)),
                ownerName = "Alice",
            )
        assertEquals(zone, r.zoneAt(5, 5, 5))
        assertEquals(zone, r.zoneAt(5 * 16 + 3, 5, 5 * 16 + 3))
    }

    @Test
    fun rename_updatesName_keepsChunksAndIndex() {
        val r = registry()
        val zone =
            r.create(
                name = "Old",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0)),
                ownerName = "Alice",
            )
        val renamed = r.rename(zone.id, "New")
        assertEquals("New", renamed?.name)
        assertEquals(zone.chunks, renamed?.chunks)
        assertEquals("New", r.get(zone.id)?.name)
        assertEquals(renamed, r.zoneAt(5, 5, 5))
    }

    @Test
    fun rename_unknownId_returnsNull() {
        val r = registry()
        assertNull(r.rename("nope", "New"))
    }

    @Test
    fun updateBounds_changesYRange_keepsChunksAndIndex() {
        val r = registry()
        val zone =
            r.create(
                name = "Arena",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0)),
                ownerName = "Alice",
            )
        val updated = r.updateBounds(zone.id, 20, 30)
        assertEquals(20, updated?.yMin)
        assertEquals(30, updated?.yMax)
        assertEquals(zone.chunks, updated?.chunks)
        assertNull(r.zoneAt(5, 5, 5))
        assertEquals(updated, r.zoneAt(5, 25, 5))
    }

    @Test
    fun updateBounds_unknownId_returnsNull() {
        val r = registry()
        assertNull(r.updateBounds("nope", 0, 10))
    }

    @Test
    fun updateChunks_changesFootprint_oldChunksNoLongerMatchNewOnesDo() {
        val r = registry()
        val zone =
            r.create(
                name = "Arena",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0)),
                ownerName = "Alice",
            )
        val updated = r.updateChunks(zone.id, setOf(ChunkPos(5, 5)))
        assertEquals(setOf(ChunkPos(5, 5)), updated?.chunks)
        assertNull(r.zoneAt(5, 5, 5))
        assertEquals(updated, r.zoneAt(5 * 16 + 3, 5, 5 * 16 + 3))
    }

    @Test
    fun updateChunks_unknownId_returnsNull() {
        val r = registry()
        assertNull(r.updateChunks("nope", setOf(ChunkPos(0, 0))))
    }

    @Test
    fun setEnabled_false_zoneAtNoLongerMatches() {
        val r = registry()
        val zone =
            r.create(
                name = "Arena",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0)),
                ownerName = "Alice",
            )
        val updated = r.setEnabled(zone.id, false)
        assertEquals(false, updated?.enabled)
        assertNull(r.zoneAt(5, 5, 5))
        assertEquals(false, r.get(zone.id)?.enabled)
    }

    @Test
    fun setEnabled_trueAfterFalse_zoneAtMatchesAgain() {
        val r = registry()
        val zone =
            r.create(
                name = "Arena",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0)),
                ownerName = "Alice",
            )
        r.setEnabled(zone.id, false)
        val reenabled = r.setEnabled(zone.id, true)
        assertEquals(reenabled, r.zoneAt(5, 5, 5))
    }

    @Test
    fun setEnabled_unknownId_returnsNull() {
        val r = registry()
        assertNull(r.setEnabled("nope", false))
    }

    @Test
    fun delete_removesZone_andClearsIndex() {
        val r = registry()
        val zone =
            r.create(
                name = "Arena",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0)),
                ownerName = "Alice",
            )
        assertTrue(r.delete(zone.id))
        assertNull(r.get(zone.id))
        assertNull(r.zoneAt(5, 5, 5))
    }

    @Test
    fun delete_unknownId_returnsFalse() {
        val r = registry()
        assertTrue(!r.delete("nope"))
    }

    @Test
    fun updateLayout_changesClipPlanesAndShortcutBar_keepsChunksAndIndex() {
        val r = registry()
        val zone =
            r.create(
                name = "Arena",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0)),
                ownerName = "Alice",
            )
        val clipPlanes =
            InstanceClipPlanes(x = ClipPlaneAxisState(enabled = true, flipped = true, pos = 3.0))
        val shortcutBarPages = listOf(listOf(null, "STONE", null))
        val updated = r.updateLayout(zone.id, clipPlanes, shortcutBarPages)
        assertEquals(clipPlanes, updated?.clipPlanes)
        assertEquals(shortcutBarPages, updated?.shortcutBarPages)
        assertEquals(zone.chunks, updated?.chunks)
        assertEquals(updated, r.zoneAt(5, 5, 5))
    }

    @Test
    fun updateLayout_unknownId_returnsNull() {
        val r = registry()
        assertNull(r.updateLayout("nope", InstanceClipPlanes(), emptyList()))
    }

    @Test
    fun overlaps_sameChunkOverlappingYRange_returnsTrue() {
        val r = registry()
        r.create(
            name = "Arena",
            yMin = 0,
            yMax = 10,
            chunks = setOf(ChunkPos(0, 0)),
            ownerName = "Alice",
        )
        assertTrue(r.overlaps(setOf(ChunkPos(0, 0)), 5, 15))
    }

    @Test
    fun overlaps_sameChunkDisjointYRange_returnsFalse() {
        val r = registry()
        r.create(
            name = "Arena",
            yMin = 0,
            yMax = 10,
            chunks = setOf(ChunkPos(0, 0)),
            ownerName = "Alice",
        )
        assertTrue(!r.overlaps(setOf(ChunkPos(0, 0)), 11, 20))
    }

    @Test
    fun overlaps_differentChunk_returnsFalse() {
        val r = registry()
        r.create(
            name = "Arena",
            yMin = 0,
            yMax = 10,
            chunks = setOf(ChunkPos(0, 0)),
            ownerName = "Alice",
        )
        assertTrue(!r.overlaps(setOf(ChunkPos(1, 0)), 0, 10))
    }

    @Test
    fun overlaps_excludingOwnId_returnsFalse() {
        val r = registry()
        val zone =
            r.create(
                name = "Arena",
                yMin = 0,
                yMax = 10,
                chunks = setOf(ChunkPos(0, 0)),
                ownerName = "Alice",
            )
        assertTrue(!r.overlaps(setOf(ChunkPos(0, 0)), 0, 10, excludeId = zone.id))
    }
}
