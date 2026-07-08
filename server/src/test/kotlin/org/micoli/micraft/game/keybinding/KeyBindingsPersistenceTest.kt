package org.micoli.micraft.game.keybinding

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.WorldPersistence

class KeyBindingsPersistenceTest {

    private fun tempPersistence(): WorldPersistence {
        val dir = Files.createTempDirectory("micraft-keybindings-test")
        return WorldPersistence(dir)
    }

    @Test
    fun loadPlayerKeyBindings_noFile_createsWithDefaults() {
        val p = tempPersistence()
        val bindings = p.loadPlayerKeyBindings("Alice")
        assertNotNull(bindings["forward"])
        assertTrue(bindings["forward"]!!.contains("KeyW"))
        val file = p.worldDir.resolve("players/Alice-keybindings.json")
        assertTrue(file.toFile().exists(), "file should be auto-created")
    }

    @Test
    fun saveAndLoadPlayerKeyBindings_roundTrip() {
        val p = tempPersistence()
        val custom = mapOf("forward" to listOf("KeyZ"), "backward" to listOf("KeyX"))
        p.savePlayerKeyBindings("Bob", custom)
        val loaded = p.loadPlayerKeyBindings("Bob")
        assertEquals(listOf("KeyZ"), loaded["forward"])
        assertEquals(listOf("KeyX"), loaded["backward"])
    }

    @Test
    fun loadPlayerKeyBindings_differentPlayers_isolated() {
        val p = tempPersistence()
        p.savePlayerKeyBindings("Alice", mapOf("forward" to listOf("KeyZ")))
        p.savePlayerKeyBindings("Bob", mapOf("forward" to listOf("KeyW")))
        assertEquals(listOf("KeyZ"), p.loadPlayerKeyBindings("Alice")["forward"])
        assertEquals(listOf("KeyW"), p.loadPlayerKeyBindings("Bob")["forward"])
    }

    @Test
    fun defaultKeyBindings_containsExpectedActions() {
        val d = defaultKeyBindings()
        listOf("forward", "backward", "strafe_left", "strafe_right", "fly_toggle", "inventory")
            .forEach { action -> assertNotNull(d[action], "missing action: $action") }
    }
}
