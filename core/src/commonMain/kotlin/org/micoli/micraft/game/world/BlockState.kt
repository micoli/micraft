package org.micoli.micraft.game.world

/**
 * Layout of the per-block state, shared by client and server. 2 bytes per block.
 *
 * byte 0 — bits 0-1: rotation (0..3), bits 2-7: plain color index (0 = untinted → block keeps its
 * texture). byte 1 — generic extra state, meaning defined per block type (e.g. RAIL_Y_SPLIT_90's
 * active-branch bit); unused bits reserved for future stateful blocks.
 */
object BlockState {
    const val MAX_COLOR_INDEX = 63

    fun rotation(state: Byte): Int = state.toInt() and 0x03

    fun colorIndex(state: Byte): Int = (state.toInt() shr 2) and 0x3F

    fun pack(rotation: Int, colorIndex: Int): Byte =
        (((colorIndex and 0x3F) shl 2) or (rotation and 0x03)).toByte()

    fun withColor(state: Byte, colorIndex: Int): Byte = pack(rotation(state), colorIndex)

    fun extra(extraState: Byte): Int = extraState.toInt() and 0xFF

    fun packExtra(extra: Int): Byte = (extra and 0xFF).toByte()
}
