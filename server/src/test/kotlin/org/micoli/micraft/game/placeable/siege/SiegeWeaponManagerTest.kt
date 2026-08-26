package org.micoli.micraft.game.placeable.siege

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.placeable.PlaceableInstance
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.placeable.siege.SiegeWeaponDefinition
import org.micoli.micraft.placeable.siege.SiegeWeaponRegistry
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class SiegeWeaponManagerTest {

    private val catapult = EntityType("TEST_CATAPULT")
    private val ammo = ItemType("TEST_BOULDER")

    private lateinit var savedSiegeWeapons: Map<EntityType, SiegeWeaponDefinition>

    @BeforeTest
    fun setUp() {
        testWorld()
        savedSiegeWeapons =
            SiegeWeaponRegistry.keys().associateWith { SiegeWeaponRegistry.get(it)!! }
        SiegeWeaponRegistry.load(
            savedSiegeWeapons +
                mapOf(
                    catapult to
                        SiegeWeaponDefinition(
                            pitchStepRange = 5,
                            powerStepRange = 8,
                            ammoItem = ammo,
                            cooldownMs = 3000,
                            muzzleOffset = Vec3(0f, 1f, 2f),
                            launchPower = 10f,
                            launchPitchDeg = 45f,
                            launchPitchDegMin = 44f,
                            launchPitchDegMax = 47f)))
    }

    @AfterTest
    fun tearDown() {
        SiegeWeaponRegistry.load(savedSiegeWeapons)
    }

    @Test
    fun spawnFor_linksToPlaceableId() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val placeableManager = PlaceableManager({})
        val placeable = placeableManager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val manager = SiegeWeaponManager({})

        val weapon = manager.spawnFor(placeable)

        assertNotNull(weapon)
        assertEquals(placeable.id, weapon.placeableId)
        assertEquals(weapon.id, manager.getByPlaceableId(placeable.id)?.id)
    }

    @Test
    fun spawnFor_nonSiegeType_isNoOp() = runBlocking {
        // Not registered in SiegeWeaponRegistry — built directly (bypassing
        // PlaceableManager.spawn, which itself now gates on SiegeWeaponRegistry) to prove
        // spawnFor's own registry check independently.
        val nonSiegeType = EntityType("TEST_NON_SIEGE")
        val placeable = PlaceableInstance("p-non-siege", nonSiegeType, Vec3(10f, 5f, 10f))
        val manager = SiegeWeaponManager({})

        val weapon = manager.spawnFor(placeable)

        assertNull(weapon)
    }

    @Test
    fun handleSetPitch_setsAndClampsToDefinitionRange() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val placeableManager = PlaceableManager({})
        val placeable = placeableManager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = SiegeWeaponManager({ broadcasts.add(it) })
        val weapon = manager.spawnFor(placeable)!!

        manager.handleSetPitch(weapon.id, 3)
        assertEquals(3, manager.get(weapon.id)?.pitchStep)

        manager.handleSetPitch(weapon.id, 999)
        assertEquals(5, manager.get(weapon.id)?.pitchStep)

        manager.handleSetPitch(weapon.id, -10)
        assertEquals(0, manager.get(weapon.id)?.pitchStep)
    }

    @Test
    fun handleNudgePitch_bouncesBetweenDegreeBounds() = runBlocking {
        // launchPitchDeg=45, bounds [44,47] — nudging by 1 each call should climb to the top bound,
        // reverse instead of overshooting, and keep bouncing back and forth indefinitely.
        val world = testWorld(Triple(8, 6, 8))
        val placeableManager = PlaceableManager({})
        val placeable = placeableManager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val manager = SiegeWeaponManager({})
        val weapon = manager.spawnFor(placeable)!!

        val steps = mutableListOf<Int>()
        repeat(6) {
            manager.handleNudgePitch(weapon.id)
            steps.add(manager.get(weapon.id)!!.pitchStep)
        }

        assertEquals(listOf(1, 2, 1, 0, -1, 0), steps)
    }

    @Test
    fun handleSetPower_setsAndClampsToDefinitionRange() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val placeableManager = PlaceableManager({})
        val placeable = placeableManager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val manager = SiegeWeaponManager({})
        val weapon = manager.spawnFor(placeable)!!

        manager.handleSetPower(weapon.id, 4)
        assertEquals(4, manager.get(weapon.id)?.powerStep)

        manager.handleSetPower(weapon.id, 999)
        assertEquals(8, manager.get(weapon.id)?.powerStep)
    }

    @Test
    fun despawnFor_removesLinkedInstance() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val placeableManager = PlaceableManager({})
        val placeable = placeableManager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val manager = SiegeWeaponManager({})
        val weapon = manager.spawnFor(placeable)!!

        manager.despawnFor(placeable.id)

        assertNull(manager.get(weapon.id))
        assertNull(manager.getByPlaceableId(placeable.id))
    }

    @Test
    fun fire_withAmmoAndOffCooldown_consumesAmmoAndSetsCooldown() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val placeableManager = PlaceableManager({})
        val placeable = placeableManager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = SiegeWeaponManager({ broadcasts.add(it) })
        val weapon = manager.spawnFor(placeable)!!
        val session = testSession()
        session.inventory[ammo] = 2
        val projectileManager = SiegeProjectileManager({})

        val result = manager.fire(session, placeable.id, placeableManager, world, projectileManager)

        assertNotNull(result)
        assertEquals(1, session.inventory[ammo])
        assertTrue(manager.get(weapon.id)!!.cooldownUntilMs > System.currentTimeMillis())
        assertTrue(broadcasts.filterIsInstance<ServerMessage.SiegeWeaponFired>().isNotEmpty())
        assertTrue(session.sent.filterIsInstance<ServerMessage.InventoryUpdate>().isNotEmpty())
    }

    @Test
    fun fire_lastAmmo_removesKeyFromInventory() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val placeableManager = PlaceableManager({})
        val placeable = placeableManager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val manager = SiegeWeaponManager({})
        manager.spawnFor(placeable)!!
        val session = testSession()
        session.inventory[ammo] = 1
        val projectileManager = SiegeProjectileManager({})

        manager.fire(session, placeable.id, placeableManager, world, projectileManager)

        assertNull(session.inventory[ammo])
    }

    @Test
    fun fire_withoutAmmo_isRejectedNoStateChange() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val placeableManager = PlaceableManager({})
        val placeable = placeableManager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = SiegeWeaponManager({ broadcasts.add(it) })
        val weapon = manager.spawnFor(placeable)!!
        broadcasts.clear()
        val session = testSession()
        val projectileManager = SiegeProjectileManager({})

        val result = manager.fire(session, placeable.id, placeableManager, world, projectileManager)

        assertNull(result)
        assertEquals(0, manager.get(weapon.id)!!.cooldownUntilMs)
        assertTrue(broadcasts.filterIsInstance<ServerMessage.SiegeWeaponFired>().isEmpty())
        assertTrue(broadcasts.filterIsInstance<ServerMessage.InventoryUpdate>().isEmpty())
    }

    @Test
    fun fire_whileOnCooldown_isRejected() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val placeableManager = PlaceableManager({})
        val placeable = placeableManager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val manager = SiegeWeaponManager({})
        manager.spawnFor(placeable)!!
        val session = testSession()
        session.inventory[ammo] = 5
        val projectileManager = SiegeProjectileManager({})

        val first = manager.fire(session, placeable.id, placeableManager, world, projectileManager)
        assertNotNull(first)
        assertEquals(4, session.inventory[ammo])

        val second = manager.fire(session, placeable.id, placeableManager, world, projectileManager)

        assertNull(second)
        assertEquals(4, session.inventory[ammo]) // ammo NOT consumed on the rejected second shot
    }

    @Test
    fun fire_computesMuzzleAndVelocityFromOrientationPitchPower() {
        val def = SiegeWeaponRegistry.get(catapult)!!
        val placeable = PlaceableInstance("p1", catapult, Vec3(10f, 5f, 10f))
        placeable.rotateTo(0) // yaw = 0 -> forward is +Z
        val weapon = SiegeWeaponInstance("w1", placeable.id, catapult)
        weapon.pitchStep = 0
        weapon.powerStep = 0

        val (muzzle, velocity) = SiegeWeaponManager.computeMuzzleAndVelocity(placeable, weapon, def)

        // muzzleOffset = (0, 1, 2), yaw = 0 -> no rotation applied.
        assertEquals(10f, muzzle.x, 0.001f)
        assertEquals(6f, muzzle.y, 0.001f)
        assertEquals(12f, muzzle.z, 0.001f)

        // launchPower=10, launchPitchDeg=45, no step adjustment: horizontal and vertical speed
        // should be equal (45°), and velocity should point purely along +Z (yaw=0, no strafe).
        assertEquals(0f, velocity.x, 0.01f)
        assertTrue(velocity.y > 0f)
        assertTrue(velocity.z > 0f)
        assertEquals(velocity.y, velocity.z, 0.01f)
    }
}
