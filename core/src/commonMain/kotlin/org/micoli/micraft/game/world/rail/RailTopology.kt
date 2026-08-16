package org.micoli.micraft.game.world.rail

import org.micoli.micraft.game.world.BlockPos

/** Segment/loop classification of a connected rail network component — see [RailConnection]. */
sealed class RailTopology {
    abstract val positions: List<BlockPos>

    /** Open-ended chain of rail blocks — both ends are dead ends (no further rail neighbor). */
    data class Segment(override val positions: List<BlockPos>) : RailTopology()

    /** Closed chain of rail blocks — the last block's exit connects back to the first. */
    data class Loop(override val positions: List<BlockPos>) : RailTopology()
}
