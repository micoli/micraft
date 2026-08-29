package org.micoli.micraft.game.world

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.*
import kotlin.test.Test
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3

class ViewModePersistenceTest {

    private fun persistence(): Pair<WorldPersistence, Path> {
        val dir = createTempDirectory("micraft-test")
        return WorldPersistence(dir) to dir
    }

    private fun minimalState(
        viewMode: String = "FIRST_PERSON",
        disabledViewModes: Set<String> = emptySet(),
    ) =
        PlayerState(
            id = "x",
            name = "Alice",
            pos = Vec3(0f, 0f, 0f),
            orientation = Orientation(0f, 0f),
            viewMode = viewMode,
            disabledViewModes = disabledViewModes,
        )

    @Test
    fun savePlayer_defaultViewMode_presentInJson() {
        val (p, dir) = persistence()
        p.savePlayerState("alice", minimalState("FIRST_PERSON"))
        val raw = dir.resolve("players/alice.yaml").readText()
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
    fun roundTrip_thirdPersonOrbitViewMode_preserved() {
        val (p, _) = persistence()
        p.savePlayerState("alice", minimalState("THIRD_PERSON_ORBIT"))
        val loaded = p.loadPlayerState("alice")
        assertNotNull(loaded)
        assertEquals("THIRD_PERSON_ORBIT", loaded.viewMode)
    }

    @Test
    fun roundTrip_thirdPersonOrbitCursorViewMode_preserved() {
        val (p, _) = persistence()
        p.savePlayerState("alice", minimalState("THIRD_PERSON_ORBIT_CURSOR"))
        val loaded = p.loadPlayerState("alice")
        assertNotNull(loaded)
        assertEquals("THIRD_PERSON_ORBIT_CURSOR", loaded.viewMode)
    }

    @Test
    fun roundTrip_disabledViewModes_preserved() {
        val (p, _) = persistence()
        p.savePlayerState(
            "alice",
            minimalState(disabledViewModes = setOf("THIRD_PERSON", "THIRD_PERSON_ORBIT_CURSOR")))
        val loaded = p.loadPlayerState("alice")
        assertNotNull(loaded)
        assertEquals(setOf("THIRD_PERSON", "THIRD_PERSON_ORBIT_CURSOR"), loaded.disabledViewModes)
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
