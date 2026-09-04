package org.micoli.micraft.game.trade

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TradeConfigLoaderTest {
    @Test
    fun load_isMemoized_ignoresFileChangesAfterFirstLoad() {
        val dir = Files.createTempDirectory("trade-config-test")
        val path = dir.resolve("trade.yaml")
        path.writeText("maxDistance: 10.0\n")
        val loader = TradeConfigLoader(path)

        val first = loader.load()
        assertEquals(10.0f, first.maxDistance)
        assertSame(first, loader.load(), "second load() must return the cached instance")

        path.writeText("maxDistance: 99.0\n")
        assertSame(
            first, loader.load(), "load() must stay memoized, no reload() exists for this loader")
    }
}
