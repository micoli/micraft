package org.micoli.micraft.game.world

import kotlin.test.Test
import org.micoli.micraft.game.SharedGameServices
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator

private val shared by lazy { SharedGameServices.default() }

private fun gen() = EndToEndBoundedChunkGenerator(halfChunksX = 1, halfChunksZ = 1)

/**
 * Not a correctness test — prints timing so a before/after run of the static-loader memoization
 * change (quest/npc/trade/claim) can be compared by eye. Run manually: `make dc CMD="./gradlew
 * :server:test --tests GameWorldFactoryBenchmarkTest"`.
 */
class GameWorldFactoryBenchmarkTest {
    @Test
    fun buildGameWorld_repeated_reportsTiming() {
        val worldCount = 20
        val start = System.nanoTime()
        repeat(worldCount) { i -> buildGameWorld("bench-$i", gen(), shared) }
        val totalMs = (System.nanoTime() - start) / 1_000_000
        println(
            "GameWorldFactoryBenchmark: built $worldCount worlds in ${totalMs}ms " +
                "(avg ${totalMs / worldCount}ms/world)")
    }
}
