package org.micoli.micraft.game.world

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        val bindings = mapOf("action.jump" to listOf("Space"), "action.fly" to listOf("F"))
        p.savePlayerKeyBindings("alice", bindings)
        val loaded = p.loadPlayerKeyBindings("alice")
        assertTrue(loaded.containsKey("action.jump"))
        assertEquals(listOf("Space"), loaded["action.jump"])
    }
}
