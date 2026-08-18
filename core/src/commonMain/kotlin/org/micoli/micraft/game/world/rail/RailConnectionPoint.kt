package org.micoli.micraft.game.world.rail

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * One rail connection point in a block's own unrotated (rotation=0) frame: [direction] the side it
 * connects through, [dy] this block's own surface height at that edge, relative to its placed floor
 * — `1` for a flat piece (straight, curve, switch), nonzero for a slope's two edges (e.g.
 * RAIL_SLOPE_45's low edge at `0`, high edge at `1`). Drives two things together: which grid Y the
 * neighbor through this point sits at ([gridDy], [RailTraversal.connectingNeighbor]) and the smooth
 * within-block Y a vehicle renders at while crossing ([RailTraversal.localHeight]) — a slope both
 * physically steps the track down/up to a neighbor built one level off and visually eases into it.
 * Declarative YAML/wire form is a compact string: `"NORTH"` (dy=1) or `"SOUTH-0"` — see [parse]. B1
 * can be connected to B2 as |--------|--------| |--------|--------| |--------|--------| | | | | | |
 * | | | | B1 | | | B1 | | | B1 E|W B2 | | SE| | | S | | | | | |--------|--------|
 * |--------|--------| |--------|--------| | |NW | | N | | | | | | | B2 | | S2 | | | | | | | | | | |
 * | | | |--------|--------| |--------|--------| |--------|--------|
 */
@Serializable(with = RailConnectionPointSerializer::class)
data class RailConnectionPoint(val direction: Direction, val dy: Float = 1f) {
    fun rotatedBy(steps: Int): RailConnectionPoint = copy(direction = direction.rotatedBy(steps))

    /**
     * Whole-grid-level Y offset of the neighbor connected through this point, relative to this
     * block's own placed floor — `0` unless [dy] departs from the flat default of `1`.
     */
    val gridDy: Int
        get() = (dy - 1f).roundToInt()

    companion object {
        private val pattern = Regex("^(\\w+)-(-?\\d+(\\.\\d+)?)$")

        fun parse(raw: String): RailConnectionPoint {
            val match = pattern.matchEntire(raw)
            return if (match != null) {
                RailConnectionPoint(
                    Direction.valueOf(match.groupValues[1]), match.groupValues[2].toFloat())
            } else {
                RailConnectionPoint(Direction.valueOf(raw))
            }
        }
    }

    override fun toString(): String = if (dy == 1f) direction.name else "${direction.name}-${dy}"
}
