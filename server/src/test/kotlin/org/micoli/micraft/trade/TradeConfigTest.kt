package org.micoli.micraft.trade

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TradeConfigTest {
    @Test
    fun missingFile_createsDefaultFile() {
        val dir = Files.createTempDirectory("trade-config")
        val path = dir.resolve("trade.yaml")
        TradeConfigLoader(path)
        assertTrue(path.toFile().exists())
    }

    @Test
    fun missingFile_load_returnsDefaults() {
        val dir = Files.createTempDirectory("trade-config2")
        val config = TradeConfigLoader(dir.resolve("trade.yaml")).load()
        assertEquals(10f, config.maxDistance)
    }

    @Test
    fun validYaml_loadsCustomDistance() {
        val dir = Files.createTempDirectory("trade-config3")
        val path = dir.resolve("trade.yaml")
        path.writeText("maxDistance: 20.0\n")
        val config = TradeConfigLoader(path).load()
        assertEquals(20.0f, config.maxDistance)
    }

    @Test
    fun invalidYaml_returnsDefaults() {
        val dir = Files.createTempDirectory("trade-config4")
        val path = dir.resolve("trade.yaml")
        path.writeText("not: [valid: yaml}")
        val config = TradeConfigLoader(path).load()
        assertEquals(10f, config.maxDistance)
    }

    @Test
    fun defaultTradeConfig_hasReasonableDistance() {
        val config = TradeConfig()
        assertTrue(config.maxDistance > 0f)
    }
}
