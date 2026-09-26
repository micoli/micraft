package org.micoli.micraft.game.npc

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NpcTickContextTest {
    private fun draws(ctxOf: () -> NpcTickContext) = List(5) { ctxOf().random.nextInt() }

    @Test
    fun `seeded live contexts replay the same draws across runs`() {
        assertEquals(draws(NpcTickContext.liveOf(42L)), draws(NpcTickContext.liveOf(42L)))
    }

    @Test
    fun `seeded live contexts share one source across calls`() {
        val ctxOf = NpcTickContext.liveOf(42L)

        assertSame(ctxOf().random, ctxOf().random)
    }

    @Test
    fun `unseeded live contexts use the default source`() {
        assertSame(Random, NpcTickContext.liveOf(null)().random)
    }
}
