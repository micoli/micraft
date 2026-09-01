package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator
import org.micoli.micraft.player.EditMode

private val shared by lazy { SharedGameServices.default() }

private fun world(id: String) =
    buildE2eGameWorld(id, EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1), shared)

private fun registry(e2e: Boolean) =
    GameWorldRegistry(
        defaultWorld = world(GameWorldRegistry.DEFAULT_ID),
        e2eEnabled = e2e,
        factory = { id -> world(id) },
    )

class GameWorldRegistryTest {

    @Test
    fun resolve_withoutIdOrOutsideE2e_isAlwaysTheDefaultWorld() {
        val r = registry(e2e = false)
        assertSame(r.defaultWorld, r.resolve(null))
        assertSame(r.defaultWorld, r.resolve(GameWorldRegistry.DEFAULT_ID))
        assertSame(r.defaultWorld, r.resolve("some-session"), "e2e off ignores the id")
        assertEquals(listOf(r.defaultWorld), r.all())
    }

    @Test
    fun resolve_inE2e_spawnsOnePerIdAndReuses() {
        val r = registry(e2e = true)
        val a = r.resolve("a")
        val b = r.resolve("b")
        assertTrue(a !== b)
        assertSame(a, r.resolve("a"), "same id returns the same world")
        assertEquals(3, r.all().size)
    }

    @Test
    fun e2eWorld_joinsPlayersInGameModeNotCreative() {
        assertEquals(EditMode.GAME, world("game-mode").spawnEditMode)
    }

    @Test
    fun resolve_refusesPastTheCap() {
        val r = registry(e2e = true)
        repeat(GameWorldRegistry.MAX_DYNAMIC) { r.resolve("w$it") }
        assertFailsWith<IllegalStateException> { r.resolve("one-too-many") }
    }

    @Test
    fun reapEmpty_dropsAWorldOnlyAfterTheIdleGrace() {
        val r = registry(e2e = true)
        r.resolve("idle")
        val t0 = 1_000_000L
        r.reapEmpty(t0)
        assertNotNull(r.get("idle"), "still within the grace window")
        r.reapEmpty(t0 + GameWorldRegistry.IDLE_TIMEOUT_MS + 1)
        assertNull(r.get("idle"), "reaped after the grace window")
    }
}
