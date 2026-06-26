package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3

class ViewModePersistenceTest {

    private fun persistence(): Pair<WorldPersistence, java.nio.file.Path> {
        val dir = createTempDirectory("micraft-test")
        return WorldPersistence(dir) to dir
    }

    private fun minimalState(viewMode: String = "FIRST_PERSON") =
        PlayerState(
            id = "x",
            name = "Alice",
            pos = Vec3(0f, 0f, 0f),
            orientation = Orientation(0f, 0f),
            viewMode = viewMode,
        )

    @Test
    fun savePlayer_defaultViewMode_presentInJson() {
        val (p, dir) = persistence()
        p.savePlayerState("alice", minimalState("FIRST_PERSON"))
        val raw = dir.resolve("players/alice.json").readText()
        assertTrue(raw.contains("viewMode"), "viewMode must be written even for default value")
    }

    @Test
    fun roundTrip_nonDefaultViewMode_preserved() {
        val (p, _) = persistence()
        p.savePlayerState("alice", minimalState("FIRST_PERSON_NO_ARMS"))
        val loaded = p.loadPlayerState("alice")
        assertNotNull(loaded)
        assertEquals("FIRST_PERSON_NO_ARMS", loaded.viewMode)
    }

    @Test
    fun loadPlayer_missingViewMode_defaultsToFirstPerson() {
        val (p, dir) = persistence()
        val json =
            """{"id":"x","name":"Alice","pos":{"x":0,"y":0,"z":0},"orientation":{"yaw":0,"pitch":0}}"""
        dir.resolve("players/alice.json").toFile().writeText(json)
        val loaded = p.loadPlayerState("alice")
        assertNotNull(loaded)
        assertEquals("FIRST_PERSON", loaded.viewMode)
    }
}
