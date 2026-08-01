package org.micoli.micraft.game.world

/**
 * Layout of the per-block state byte, shared by client and server.
 *
 * bits 0-1: rotation (0..3) bits 2-7: plain color index (0 = untinted → block keeps its texture)
 */
object BlockState {
    const val MAX_COLOR_INDEX = 63

    fun rotation(state: Byte): Int = state.toInt() and 0x03

    fun colorIndex(state: Byte): Int = (state.toInt() shr 2) and 0x3F

    fun pack(rotation: Int, colorIndex: Int): Byte =
        (((colorIndex and 0x3F) shl 2) or (rotation and 0x03)).toByte()

    fun withColor(state: Byte, colorIndex: Int): Byte = pack(rotation(state), colorIndex)
}
