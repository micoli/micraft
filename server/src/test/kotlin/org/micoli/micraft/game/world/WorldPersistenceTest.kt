package org.micoli.micraft.game.world

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.support.MapChunkGenerator
import org.micoli.micraft.support.testPlayerState

class WorldPersistenceTest {
    private fun persistence(): WorldPersistence =
        WorldPersistence(Files.createTempDirectory("world-persistence-test"))

    @Test
    fun loadMissingChunk_returnsNull() {
        assertNull(persistence().loadChunk(ChunkPos(0, 0)))
    }

    @Test
    fun chunkRoundtrip_saveThenLoad() {
        val p = persistence()
        val world = WorldState(MapChunkGenerator())
        val chunk = world.getOrGenerate(ChunkPos(3, -2))
        p.saveChunk(ChunkPos(3, -2), chunk)
        val loaded = p.loadChunk(ChunkPos(3, -2))
        assertNotNull(loaded)
        assertEquals(ChunkPos(3, -2), loaded.pos)
        assertTrue(loaded.blocks.contentEquals(chunk.blocks))
    }

    @Test
    fun loadMissingPlayer_returnsNull() {
        assertNull(persistence().loadPlayerState("nobody"))
    }

    @Test
    fun playerStateRoundtrip_saveThenLoad() {
        val p = persistence()
        val state = testPlayerState(name = "TestPlayer")
        p.savePlayerState("TestPlayer", state)
        val loaded = p.loadPlayerState("TestPlayer")
        assertNotNull(loaded)
        assertEquals(state.name, loaded.name)
        assertEquals(state.pos, loaded.pos)
    }

    @Test
    fun playerStateSanitizesName() {
        val p = persistence()
        val state = testPlayerState(name = "weird/name")
        p.savePlayerState("weird/name", state)
        val loaded = p.loadPlayerState("weird/name")
        assertNotNull(loaded)
    }

    @Test
    fun keyBindingsRoundtrip() {
        val p = persistence()
        p.savePlayerState("alice", testPlayerState(name = "alice"))
        val bindings = mapOf("action.jump" to listOf("Space"), "action.fly" to listOf("F"))
        p.savePlayerKeyBindings("alice", bindings)
        val loaded = p.loadPlayerKeyBindings("alice")
        assertTrue(loaded.containsKey("action.jump"))
        assertEquals(listOf("Space"), loaded["action.jump"])
    }

    @Test
    fun savePlayerState_skipsOverwrite_whenFileExternallyModified() {
        val p = persistence()
        val original = testPlayerState(name = "bob", pos = Vec3(1f, 2f, 3f))
        p.savePlayerState("bob", original)
        p.loadPlayerState("bob")

        val yamlFile = p.worldDir.resolve("players/bob.yaml")
        Files.setLastModifiedTime(yamlFile, FileTime.fromMillis(System.currentTimeMillis() + 5_000))

        val updated = original.copy(pos = Vec3(99f, 99f, 99f))
        p.savePlayerState("bob", updated)

        val reloaded = p.loadPlayerState("bob")
        assertNotNull(reloaded)
        assertEquals(1f, reloaded.pos.x, "External edit must not be overwritten")
    }

    @Test
    fun savePlayerState_writes_whenFileNotExternallyModified() {
        val p = persistence()
        val original = testPlayerState(name = "carol", pos = Vec3(1f, 2f, 3f))
        p.savePlayerState("carol", original)
        p.loadPlayerState("carol")

        val updated = original.copy(pos = Vec3(50f, 50f, 50f))
        p.savePlayerState("carol", updated)

        val reloaded = p.loadPlayerState("carol")
        assertNotNull(reloaded)
        assertEquals(50f, reloaded.pos.x, "Normal save must persist")
    }

    @Test
    fun savePlayerMacros_skipsOverwrite_whenFileExternallyModified() {
        val p = persistence()
        p.savePlayerState("dave", testPlayerState(name = "dave"))
        val macros = mapOf("hello" to "/say hello")
        p.savePlayerMacros("dave", macros)
        p.loadPlayerMacros("dave")

        val macrosFile = p.worldDir.resolve("players/dave-macros.yaml")
        Files.setLastModifiedTime(
            macrosFile, FileTime.fromMillis(System.currentTimeMillis() + 5_000))

        p.savePlayerMacros("dave", mapOf("hello" to "/say overwritten"))

        val reloaded = p.loadPlayerMacros("dave")
        assertEquals("/say hello", reloaded["hello"], "External macro edit must not be overwritten")
    }
}
