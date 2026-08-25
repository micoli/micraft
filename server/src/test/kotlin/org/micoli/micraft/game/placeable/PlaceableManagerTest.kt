package org.micoli.micraft.game.placeable

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.placeable.siege.SiegeWeaponDefinition
import org.micoli.micraft.placeable.siege.SiegeWeaponRegistry
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld

class PlaceableManagerTest {

    private val catapult = EntityType("TEST_CATAPULT")
    private val catapultItem = ItemType("TEST_SIEGE_CATAPULT")

    private lateinit var savedSiegeWeapons: Map<EntityType, SiegeWeaponDefinition>
    private lateinit var savedItems: Map<ItemType, ItemDefinition>

    @BeforeTest
    fun setUp() {
        testWorld() // warm up TestFixtures static init before snapshotting the registries
        savedSiegeWeapons =
            SiegeWeaponRegistry.keys().associateWith { SiegeWeaponRegistry.get(it)!! }
        savedItems = ItemRegistry.keys().associateWith { ItemRegistry.get(it) }
        SiegeWeaponRegistry.load(savedSiegeWeapons + mapOf(catapult to SiegeWeaponDefinition()))
        ItemRegistry.load(
            savedItems + mapOf(catapultItem to ItemDefinition(spawnsEntity = catapult)))
    }

    @AfterTest
    fun tearDown() {
        SiegeWeaponRegistry.load(savedSiegeWeapons)
        ItemRegistry.load(savedItems)
    }

    @Test
    fun spawn_onValidGround_succeedsAndBroadcasts() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = PlaceableManager({ broadcasts.add(it) })

        val spawned = manager.spawn(catapult, BlockPos(8, 7, 8), world)

        assertNotNull(spawned)
        assertEquals(catapult, spawned.type)
        assertEquals(1, manager.getAll().size)
        val spawnedMsg = broadcasts.filterIsInstance<ServerMessage.PlaceableSpawned>().single()
        assertEquals(spawned.id, spawnedMsg.state.id)
    }

    @Test
    fun spawn_onLiquidOrAirGround_isRejected() = runBlocking {
        val world = testWorld() // no solid block below (8, 7, 8) -> AIR
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = PlaceableManager({ broadcasts.add(it) })

        val spawned = manager.spawn(catapult, BlockPos(8, 7, 8), world)

        assertNull(spawned)
        assertEquals(emptyList(), manager.getAll().toList())
        assertEquals(emptyList(), broadcasts)
    }

    @Test
    fun spawn_unknownType_isRejected() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val manager = PlaceableManager({})

        val spawned = manager.spawn(EntityType("NOT_A_PLACEABLE"), BlockPos(8, 7, 8), world)

        assertNull(spawned)
    }

    @Test
    fun handleRotate_advancesStep_andWrapsAt11() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val manager = PlaceableManager({})
        val instance = manager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        instance.rotationStep = 11

        manager.handleRotate(instance.id)

        assertEquals(0, manager.get(instance.id)?.rotationStep)
    }

    @Test
    fun handleInteract_despawnsAndReturnsItemToInventory() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val broadcasts = mutableListOf<ServerMessage>()
        val manager = PlaceableManager({ broadcasts.add(it) })
        val instance = manager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        val session = testSession()

        manager.handleInteract(instance.id, session)

        assertNull(manager.get(instance.id))
        assertEquals(1, session.inventory[catapultItem])
        assertTrue(broadcasts.any { it is ServerMessage.PlaceableDespawned })
    }

    @Test
    fun loadSave_roundTrips() = runBlocking {
        val world = testWorld(Triple(8, 6, 8))
        val manager = PlaceableManager({})
        val instance = manager.spawn(catapult, BlockPos(8, 7, 8), world)!!
        instance.rotationStep = 5
        val savePath = Files.createTempFile("placeables", ".yaml")

        manager.save(savePath)
        val restored = PlaceableManager({})
        restored.load(savePath)

        val restoredInstance = restored.get(instance.id)
        assertNotNull(restoredInstance)
        assertEquals(catapult, restoredInstance.type)
        assertEquals(5, restoredInstance.rotationStep)
        Files.deleteIfExists(savePath)
        Unit
    }
}
