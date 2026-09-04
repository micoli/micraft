package org.micoli.micraft.game.world.claim

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ClaimConfigLoaderTest {
    @Test
    fun load_isMemoized_ignoresFileChangesAfterFirstLoad() {
        val dir = Files.createTempDirectory("claim-config-test")
        val path = dir.resolve("claims.yaml")
        path.writeText("costPerChunk: 50\nmaxChunksPerClaim: 64\nmaxClaimsPerPlayer: 3\n")
        val loader = ClaimConfigLoader(path)

        val first = loader.load()
        assertEquals(50L, first.costPerChunk)
        assertSame(first, loader.load(), "second load() must return the cached instance")

        path.writeText("costPerChunk: 999\nmaxChunksPerClaim: 64\nmaxClaimsPerPlayer: 3\n")
        assertSame(
            first, loader.load(), "load() must stay memoized, no reload() exists for this loader")
    }
}
