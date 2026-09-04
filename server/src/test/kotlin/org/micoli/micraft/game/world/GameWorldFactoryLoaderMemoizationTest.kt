package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertSame
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator

private val shared by lazy { SharedGameServices.default() }

private fun gen() = EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1)

/**
 * Every dynamic E2E `GameWorld` re-invokes the static-config loaders on `SharedGameServices`. Their
 * outputs are read-only catalogues, so two worlds built against the same `SharedGameServices` must
 * see the disk parsed once, not once per world.
 */
class GameWorldFactoryLoaderMemoizationTest {
    @Test
    fun questAndNpcDefinitions_areSharedAcrossWorlds_notReparsedPerWorld() {
        val worldA = buildGameWorld("memo-a", gen(), shared)
        val worldB = buildGameWorld("memo-b", gen(), shared)

        assertSame(
            worldA.questManager!!.getDefinitions(),
            worldB.questManager!!.getDefinitions(),
            "quest catalogue must be parsed once and shared across worlds")
        assertSame(
            worldA.npcManager.getDefinitions(),
            worldB.npcManager.getDefinitions(),
            "NPC catalogue must be parsed once and shared across worlds")
    }
}
