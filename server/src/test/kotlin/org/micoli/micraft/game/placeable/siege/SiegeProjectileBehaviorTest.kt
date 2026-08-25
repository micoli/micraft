package org.micoli.micraft.game.placeable.siege

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.game.GRAVITY
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.testWorld

class SiegeProjectileBehaviorTest {

    private val type = EntityType("TEST_BOULDER")

    private fun projectile(pos: Vec3, velocity: Vec3) =
        SiegeProjectileInstance(
            id = "p1",
            type = type,
            pos = pos,
            velocity = velocity,
            ownerId = "owner",
            impactRadius = 3f,
            impactDamage = 25,
            damageType = DamageType.PHYSICAL,
        )

    @Test
    fun tick_appliesGravity_velocityYDecreasesEachTick() {
        val world = testWorld()
        val instance = projectile(Vec3(0f, 100f, 0f), Vec3(2f, 0f, 0f))

        val impact = SiegeProjectileBehavior.tick(instance, world, TICK_SECONDS)

        assertNull(impact)
        assertEquals(GRAVITY * TICK_SECONDS, instance.velocity.y, 0.001f)
        assertEquals(2f, instance.velocity.x, 0.001f)
    }

    @Test
    fun tick_parabolicTrajectory_matchesAnalyticGravityIntegration() {
        val world = testWorld()
        val v0 = Vec3(5f, 10f, 0f)
        val instance = projectile(Vec3(0f, 200f, 0f), v0)

        var elapsed = 0f
        repeat(20) {
            val impact = SiegeProjectileBehavior.tick(instance, world, TICK_SECONDS)
            assertNull(impact, "must stay airborne far above the world for this test's duration")
            elapsed += TICK_SECONDS
        }

        // Closed-form velocity Verlet-equivalent: each tick does v += g*dt then pos += v*dt (semi
        // -implicit Euler), so the analytic comparison must use the same integration scheme, not
        // the
        // continuous-time y0 + v0*t + 0.5*g*t^2 formula (those diverge slightly over many steps).
        var analyticY = 200f
        var analyticX = 0f
        var vy = v0.y
        repeat(20) {
            vy += GRAVITY * TICK_SECONDS
            analyticY += vy * TICK_SECONDS
            analyticX += v0.x * TICK_SECONDS
        }

        assertEquals(analyticY, instance.pos.y, 0.01f)
        assertEquals(analyticX, instance.pos.x, 0.01f)
        assertEquals(vy, instance.velocity.y, 0.001f)
    }

    @Test
    fun tick_hitsTerrain_returnsImpactAtOrNearCollisionPoint() {
        // Solid block occupies the voxel cell y=6 (i.e. world space [6,7)) at x=8,z=8.
        val world = testWorld(Triple(8, 6, 8))
        val instance = projectile(Vec3(8.5f, 20f, 8.5f), Vec3(0f, 0f, 0f))

        var impact: SiegeProjectileBehavior.ImpactResult? = null
        repeat(200) {
            if (impact == null) impact = SiegeProjectileBehavior.tick(instance, world, TICK_SECONDS)
        }

        assertNotNull(impact)
        assertTrue(
            impact.pos.y in 5.5f..7.5f, "impact y=${impact.pos.y} should be near the block top")
    }

    @Test
    fun tick_highSpeedThroughThinWall_doesNotTunnel() {
        // A single 1-block-thick wall at x=10, spanning a wide z/y range so a fast horizontal shot
        // can't just miss it vertically.
        val solidBlocks = (0..20).map { y -> Triple(10, y, 8) }.toTypedArray()
        val world = testWorld(*solidBlocks)
        // Naive single-step (no sub-stepping) would travel start.x=0 -> end.x=200 in one tick,
        // jumping clean over/through the x=10 wall without ever sampling a point inside it.
        val instance = projectile(Vec3(0f, 10f, 8.5f), Vec3(4000f, 0f, 0f))

        val impact = SiegeProjectileBehavior.tick(instance, world, TICK_SECONDS)

        assertNotNull(
            impact, "sub-stepping must catch the thin wall instead of tunneling through it")
        assertTrue(impact.pos.x in 9.5f..10.5f, "impact x=${impact.pos.x} should be at the wall")
    }
}
