package org.micoli.micraft.di

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.micoli.micraft.support.testSession
import org.micoli.micraft.world.ItemType
import org.micoli.micraft.world.WorldPersistence

class PlayerPersisterTest {

    @Test
    fun save_withNullPersistence_doesNotThrow() {
        val persister = PlayerPersister(null)
        persister.save(testSession(name = "Alice"))
    }

    @Test
    fun save_persistsInventoryAndRecipes() {
        val worldDir = createTempDirectory("player-persister-world")
        val persistence = WorldPersistence(worldDir)
        val persister = PlayerPersister(persistence)

        val session = testSession(name = "Alice")
        session.inventory[ItemType("COBBLESTONE")] = 5
        session.knownRecipes.add("stone_pickaxe")

        persister.save(session)

        val loaded = assertNotNull(persistence.loadPlayerState("Alice"))
        assertEquals(5, loaded.inventory[ItemType("COBBLESTONE")])
        assertEquals(setOf("stone_pickaxe"), loaded.knownRecipes)
    }

    @Test
    fun loadPlayerState_forNeverSavedPlayer_returnsNull() {
        val worldDir = createTempDirectory("player-persister-world-empty")
        val persistence = WorldPersistence(worldDir)
        assertNull(persistence.loadPlayerState("Nobody"))
    }
}
