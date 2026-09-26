package org.micoli.micraft.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoxelRaycastTest {
    private fun solidAt(vararg cells: Triple<Int, Int, Int>): (Int, Int, Int) -> Boolean =
        { x, y, z ->
            Triple(x, y, z) in cells
        }

    @Test
    fun `hits the first solid block along the ray and reports the face it entered through`() {
        val hit =
            VoxelRaycast.cast(
                0.5, 0.5, 0.5, 1.0, 0.0, 0.0, 10.0, solidAt(Triple(3, 0, 0), Triple(5, 0, 0)))!!

        assertEquals(Triple(3, 0, 0), Triple(hit.x, hit.y, hit.z))
        assertEquals(Triple(-1, 0, 0), Triple(hit.normalX, hit.normalY, hit.normalZ))
        assertEquals(3.0, hit.pointX, 1e-9)
        assertEquals(2.5, hit.distance, 1e-9)
    }

    @Test
    fun `looking down onto the ground reports the top face`() {
        val hit = VoxelRaycast.cast(4.2, 10.0, 7.8, 0.0, -1.0, 0.0, 20.0) { _, y, _ -> y <= 3 }!!

        assertEquals(Triple(4, 3, 7), Triple(hit.x, hit.y, hit.z))
        assertEquals(Triple(0, 1, 0), Triple(hit.normalX, hit.normalY, hit.normalZ))
        assertEquals(4.0, hit.pointY, 1e-9)
    }

    @Test
    fun `stepping the point along the normal gives the placement cell, against it the hit cell`() {
        val hit = VoxelRaycast.cast(-2.3, 5.7, 1.1, 1.0, -0.6, 0.3, 30.0) { _, y, _ -> y <= 2 }!!

        val place =
            Triple(
                kotlin.math.floor(hit.pointX + hit.normalX * 0.5).toInt(),
                kotlin.math.floor(hit.pointY + hit.normalY * 0.5).toInt(),
                kotlin.math.floor(hit.pointZ + hit.normalZ * 0.5).toInt(),
            )
        val broken =
            Triple(
                kotlin.math.floor(hit.pointX - hit.normalX * 0.5).toInt(),
                kotlin.math.floor(hit.pointY - hit.normalY * 0.5).toInt(),
                kotlin.math.floor(hit.pointZ - hit.normalZ * 0.5).toInt(),
            )

        assertEquals(Triple(hit.x, hit.y, hit.z), broken)
        assertEquals(Triple(hit.x, hit.y + 1, hit.z), place)
    }

    @Test
    fun `misses beyond the maximum distance`() {
        assertNull(VoxelRaycast.cast(0.5, 0.5, 0.5, 1.0, 0.0, 0.0, 2.0, solidAt(Triple(5, 0, 0))))
    }
}
