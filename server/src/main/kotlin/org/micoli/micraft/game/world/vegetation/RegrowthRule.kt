package org.micoli.micraft.game.world.vegetation

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.BlockPos

/**
 * What grows back on a cell after its plant was eaten, and how long it takes.
 *
 * Grazing animals remove FLOWER and WEED blocks. Without regrowth a pasture is stripped bare and
 * every herbivore starves, so this is a rule of the world, not a debugging aid: the same delay
 * applies in the live game and in the admin world simulator.
 */
@Serializable
data class RegrowthRule(
    /** Block that was eaten. */
    val grazed: String,
    /** Block that grows back in its place. */
    val regrows: String,
    val minTicks: Int = 600,
    val maxTicks: Int = 2_400,
    /** Only regrow when the block underneath can host vegetation (grass, dirt, …). */
    val requiresVegetationHost: Boolean = true,
)

/** A cell waiting to grow its plant back. */
@Serializable
data class PendingRegrowth(
    val pos: BlockPos,
    val block: String,
    val ticksAccumulated: Int,
    val ticksRequired: Int,
)
