package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.CombatState
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld
import org.micoli.micraft.vehicle.VehicleDefinition
import org.micoli.micraft.vehicle.VehicleRegistry

class MountCommandTest {
    private val mount = MountCommand()
    private val cart = EntityType("CART")

    private fun spawnedVehicleId(manager: VehicleManager): String = runBlocking {
        val saved = VehicleRegistry.get(cart)
        VehicleRegistry.load(mapOf(cart to (saved ?: VehicleDefinition())))
        manager.spawnVehicle(cart, BlockPos(8, 7, 8), testWorld(), Direction.SOUTH)!!.id
    }

    @Test
    fun `no target sends no_target notification and does not mount`() = runBlocking {
        val session = testSession()
        val manager = VehicleManager({})
        mount.execute(session, "", testContext(vehicleManager = manager))

        assertNull(session.mountedVehicleId)
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun `mounting a targeted vehicle sets mountedVehicleId and sends MountUpdate`() = runBlocking {
        val manager = VehicleManager({})
        val vehicleId = spawnedVehicleId(manager)
        val session = testSession()
        session.combatState = CombatState(targetId = vehicleId)

        mount.execute(session, "", testContext(vehicleManager = manager))

        assertEquals(vehicleId, session.mountedVehicleId)
        assertEquals(vehicleId, manager.get(vehicleId)?.riderSessionId)
        assertEquals(
            vehicleId,
            session.sent.filterIsInstance<ServerMessage.MountUpdate>().single().vehicleId)
    }

    @Test
    fun `mounting again dismounts`() = runBlocking {
        val manager = VehicleManager({})
        val vehicleId = spawnedVehicleId(manager)
        val session = testSession()
        session.combatState = CombatState(targetId = vehicleId)
        mount.execute(session, "", testContext(vehicleManager = manager))

        mount.execute(session, "", testContext(vehicleManager = manager))

        assertNull(session.mountedVehicleId)
        assertNull(manager.get(vehicleId)?.riderSessionId)
        assertNull(session.sent.filterIsInstance<ServerMessage.MountUpdate>().last().vehicleId)
    }

    @Test
    fun `mounting an already occupied vehicle sends occupied notification`() = runBlocking {
        val manager = VehicleManager({})
        val vehicleId = spawnedVehicleId(manager)
        val rider = testSession(id = "rider")
        rider.combatState = CombatState(targetId = vehicleId)
        mount.execute(rider, "", testContext(vehicleManager = manager))

        val other = testSession(id = "other")
        other.combatState = CombatState(targetId = vehicleId)
        mount.execute(other, "", testContext(vehicleManager = manager))

        assertNull(other.mountedVehicleId)
        assertTrue(other.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }
}
