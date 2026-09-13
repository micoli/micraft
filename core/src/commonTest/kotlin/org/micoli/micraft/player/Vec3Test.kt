package org.micoli.micraft.player

import kotlin.test.Test
import kotlin.test.assertEquals

class Vec3Test {

    @Test
    fun distanceSquaredTo_computesFullThreeAxisDistance() {
        val a = Vec3(0f, 0f, 0f)
        val b = Vec3(1f, 2f, 2f)
        assertEquals(9f, a.distanceSquaredTo(b))
    }

    @Test
    fun distanceTo_takesTheSquareRoot() {
        val a = Vec3(0f, 0f, 0f)
        val b = Vec3(3f, 4f, 0f)
        assertEquals(5f, a.distanceTo(b))
    }

    @Test
    fun distanceSquaredXZTo_ignoresYAxis() {
        val a = Vec3(0f, 100f, 0f)
        val b = Vec3(3f, -50f, 4f)
        assertEquals(25f, a.distanceSquaredXZTo(b))
    }

    @Test
    fun distanceXZTo_takesTheSquareRoot() {
        val a = Vec3(0f, 0f, 0f)
        val b = Vec3(3f, 999f, 4f)
        assertEquals(5f, a.distanceXZTo(b))
    }
}
