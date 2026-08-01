package org.micoli.micraft.world

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.world.BlockState

class BlockStateTest {

    @Test
    fun pack_roundTripsRotationAndColor() {
        for (rotation in 0..3) {
            for (colorIndex in 0..BlockState.MAX_COLOR_INDEX) {
                val state = BlockState.pack(rotation, colorIndex)
                assertEquals(rotation, BlockState.rotation(state), "rotation $rotation/$colorIndex")
                assertEquals(
                    colorIndex, BlockState.colorIndex(state), "color $rotation/$colorIndex")
            }
        }
    }

    @Test
    fun defaultState_isRotationZeroUntinted() {
        assertEquals(0, BlockState.rotation(0))
        assertEquals(0, BlockState.colorIndex(0))
    }

    @Test
    fun pack_masksOutOfRangeValues() {
        // rotation 4 wraps to 0, color 64 wraps to 0 — never bleeds into the neighboring field
        assertEquals(0, BlockState.rotation(BlockState.pack(4, 0)))
        assertEquals(0, BlockState.colorIndex(BlockState.pack(0, 64)))
        assertEquals(1, BlockState.colorIndex(BlockState.pack(0, 65)))
    }

    @Test
    fun colorIndex_isReadableFromNegativeByte() {
        // color 63 sets bit 7 → the byte is negative; the shift must not sign-extend
        val state = BlockState.pack(2, 63)
        assertEquals(2, BlockState.rotation(state))
        assertEquals(63, BlockState.colorIndex(state))
    }

    @Test
    fun withColor_keepsRotation() {
        val state = BlockState.pack(3, 5)
        val recolored = BlockState.withColor(state, 12)
        assertEquals(3, BlockState.rotation(recolored))
        assertEquals(12, BlockState.colorIndex(recolored))
    }
}
