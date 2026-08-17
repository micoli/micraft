package org.micoli.micraft.game.world.rail

import org.micoli.micraft.game.world.BlockType

/**
 * Read-only block lookup [RailTraversal] needs, decoupled from any concrete world/buffer type so
 * the same traversal logic serves the server's authoritative `WorldState`, the web client's live
 * chunk preview (`ChunkManager`), and the admin scene editor's unsaved buffer (`SceneMesher`).
 */
interface RailWorldView {
    fun getBlock(wx: Int, wy: Int, wz: Int): BlockType

    fun getBlockState(wx: Int, wy: Int, wz: Int): Byte

    fun getExtraState(wx: Int, wy: Int, wz: Int): Byte
}
