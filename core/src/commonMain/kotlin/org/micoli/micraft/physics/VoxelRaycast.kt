package org.micoli.micraft.physics

import kotlin.math.abs
import kotlin.math.floor

/**
 * First voxel a ray enters that [VoxelRaycast.cast]'s `isHit` accepts: the block, the unit normal
 * of the face the ray crossed into it (zero when the ray starts inside it), and the point on that
 * face.
 */
data class VoxelHit(
    val x: Int,
    val y: Int,
    val z: Int,
    val normalX: Int,
    val normalY: Int,
    val normalZ: Int,
    val pointX: Double,
    val pointY: Double,
    val pointZ: Double,
    val distance: Double,
)

/**
 * Grid traversal (Amanatides & Woo) over unit voxels: visits every cell the ray crosses, in order.
 */
object VoxelRaycast {
    fun cast(
        ox: Double,
        oy: Double,
        oz: Double,
        dx: Double,
        dy: Double,
        dz: Double,
        maxDistance: Double,
        isHit: (x: Int, y: Int, z: Int) -> Boolean,
    ): VoxelHit? {
        val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (length == 0.0) return null
        val ux = dx / length
        val uy = dy / length
        val uz = dz / length

        var x = floor(ox).toInt()
        var y = floor(oy).toInt()
        var z = floor(oz).toInt()
        val stepX = sign(ux)
        val stepY = sign(uy)
        val stepZ = sign(uz)
        val deltaX = if (ux != 0.0) abs(1.0 / ux) else Double.MAX_VALUE
        val deltaY = if (uy != 0.0) abs(1.0 / uy) else Double.MAX_VALUE
        val deltaZ = if (uz != 0.0) abs(1.0 / uz) else Double.MAX_VALUE
        var tMaxX = firstBoundary(ox, x, ux)
        var tMaxY = firstBoundary(oy, y, uy)
        var tMaxZ = firstBoundary(oz, z, uz)
        var t = 0.0
        var nx = 0
        var ny = 0
        var nz = 0

        while (t <= maxDistance) {
            if (isHit(x, y, z)) {
                return VoxelHit(x, y, z, nx, ny, nz, ox + ux * t, oy + uy * t, oz + uz * t, t)
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                t = tMaxX
                tMaxX += deltaX
                x += stepX
                nx = -stepX
                ny = 0
                nz = 0
            } else if (tMaxY <= tMaxZ) {
                t = tMaxY
                tMaxY += deltaY
                y += stepY
                nx = 0
                ny = -stepY
                nz = 0
            } else {
                t = tMaxZ
                tMaxZ += deltaZ
                z += stepZ
                nx = 0
                ny = 0
                nz = -stepZ
            }
        }
        return null
    }

    private fun sign(v: Double) =
        when {
            v > 0 -> 1
            v < 0 -> -1
            else -> 0
        }

    private fun firstBoundary(origin: Double, cell: Int, direction: Double): Double =
        when {
            direction > 0 -> (cell + 1 - origin) / direction
            direction < 0 -> (cell - origin) / direction
            else -> Double.MAX_VALUE
        }
}
