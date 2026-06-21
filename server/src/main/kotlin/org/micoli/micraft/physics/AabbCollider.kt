package org.micoli.micraft.physics

import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.WorldState
import kotlin.math.floor

object AabbCollider {

    private fun solid(world: WorldState, bx: Int, by: Int, bz: Int) =
        world.getBlock(bx, by, bz) != BlockType.AIR

    /** Int range covering blocks between [min, max). The -0.001 avoids counting a
     *  block when the AABB face sits exactly on its boundary (touching ≠ inside). */
    private fun blocks(min: Float, max: Float): IntRange =
        floor(min.toDouble()).toInt()..floor((max - 0.001).toDouble()).toInt()

    /** True if any block under the full footprint is solid (player is on ground). */
    fun isGrounded(world: WorldState, cx: Float, cy: Float, cz: Float, w: Float): Boolean {
        val hw = w / 2f
        val by = floor((cy - 0.001).toDouble()).toInt()
        for (bx in blocks(cx - hw, cx + hw))
            for (bz in blocks(cz - hw, cz + hw))
                if (solid(world, bx, by, bz)) return true
        return false
    }

    /** Resolve X movement against the world. Sweeps all intermediate columns to prevent tunneling at high speed. */
    fun resolveX(world: WorldState, cx: Float, cy: Float, cz: Float, w: Float, h: Float, dx: Float): Float {
        if (dx == 0f) return 0f
        val hw = w / 2f
        val newCx = cx + dx
        val ys = blocks(cy, cy + h)
        val zs = blocks(cz - hw, cz + hw)
        if (dx > 0) {
            val from = floor((cx + hw).toDouble()).toInt()
            val to   = floor((newCx + hw).toDouble()).toInt()
            for (bx in from..to)
                for (by in ys) for (bz in zs)
                    if (solid(world, bx, by, bz)) {
                        val snap = bx.toFloat() - hw
                        return (snap - cx).coerceIn(0f, dx)
                    }
        } else {
            val from = floor((cx - hw).toDouble()).toInt()
            val to   = floor((newCx - hw).toDouble()).toInt()
            for (bx in from downTo to)
                for (by in ys) for (bz in zs)
                    if (solid(world, bx, by, bz)) {
                        val snap = (bx + 1).toFloat() + hw
                        return (snap - cx).coerceIn(dx, 0f)
                    }
        }
        return dx
    }

    /** Resolve Z movement against the world. Sweeps all intermediate columns to prevent tunneling at high speed. */
    fun resolveZ(world: WorldState, cx: Float, cy: Float, cz: Float, w: Float, h: Float, dz: Float): Float {
        if (dz == 0f) return 0f
        val hw = w / 2f
        val newCz = cz + dz
        val ys = blocks(cy, cy + h)
        val xs = blocks(cx - hw, cx + hw)
        if (dz > 0) {
            val from = floor((cz + hw).toDouble()).toInt()
            val to   = floor((newCz + hw).toDouble()).toInt()
            for (bz in from..to)
                for (by in ys) for (bx in xs)
                    if (solid(world, bx, by, bz)) {
                        val snap = bz.toFloat() - hw
                        return (snap - cz).coerceIn(0f, dz)
                    }
        } else {
            val from = floor((cz - hw).toDouble()).toInt()
            val to   = floor((newCz - hw).toDouble()).toInt()
            for (bz in from downTo to)
                for (by in ys) for (bx in xs)
                    if (solid(world, bx, by, bz)) {
                        val snap = (bz + 1).toFloat() + hw
                        return (snap - cz).coerceIn(dz, 0f)
                    }
        }
        return dz
    }

    /** Resolve Y movement. Sweeps all intermediate blocks when falling fast. */
    fun resolveY(world: WorldState, cx: Float, cy: Float, cz: Float, w: Float, h: Float, dy: Float): Float {
        if (dy == 0f) return 0f
        val hw = w / 2f
        val xs = blocks(cx - hw, cx + hw)
        val zs = blocks(cz - hw, cz + hw)

        if (dy < 0) {
            val from = floor((cy - 0.001).toDouble()).toInt()
            val to   = floor((cy + dy - 0.001).toDouble()).toInt()
            for (by in from downTo to)
                for (bx in xs) for (bz in zs)
                    if (solid(world, bx, by, bz))
                        return ((by + 1f) - cy).coerceAtMost(0f)
        } else {
            val from = floor((cy + h).toDouble()).toInt()
            val to   = floor((cy + dy + h).toDouble()).toInt()
            for (by in from..to)
                for (bx in xs) for (bz in zs)
                    if (solid(world, bx, by, bz))
                        return (by.toFloat() - h - cy).coerceAtLeast(0f)
        }
        return dy
    }

    /** Returns true if the player can expand from currentH to newH without hitting blocks. */
    fun canAdoptStance(world: WorldState, cx: Float, cy: Float, cz: Float, w: Float, newH: Float, currentH: Float): Boolean {
        if (newH <= currentH) return true
        val hw = w / 2f
        val from = floor((cy + currentH).toDouble()).toInt()
        val to   = floor((cy + newH - 0.001).toDouble()).toInt()
        for (by in from..to)
            for (bx in blocks(cx - hw, cx + hw))
                for (bz in blocks(cz - hw, cz + hw))
                    if (solid(world, bx, by, bz)) return false
        return true
    }
}
