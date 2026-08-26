package org.micoli.micraft.game.auction

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.protocol.AuctionDuration

class AuctionConfigTest {
    @Test
    fun missingFile_createsDefaultFile() {
        val dir = Files.createTempDirectory("auction-config")
        val path = dir.resolve("auction.yaml")
        AuctionConfigLoader(path)
        assertTrue(path.toFile().exists())
    }

    @Test
    fun missingFile_load_returnsDefaults() {
        val dir = Files.createTempDirectory("auction-config2")
        val config = AuctionConfigLoader(dir.resolve("auction.yaml")).load()
        assertEquals(3, config.taxPercentFor(AuctionDuration.H12))
        assertEquals(6, config.taxPercentFor(AuctionDuration.H24))
        assertEquals(10, config.taxPercentFor(AuctionDuration.H48))
        assertEquals(15, config.taxPercentFor(AuctionDuration.H96))
    }

    @Test
    fun invalidYaml_returnsDefaults() {
        val dir = Files.createTempDirectory("auction-config3")
        val path = dir.resolve("auction.yaml")
        path.writeText("not: [valid: yaml}")
        val config = AuctionConfigLoader(path).load()
        assertEquals(3, config.taxPercentFor(AuctionDuration.H12))
    }

    @Test
    fun validYaml_loadsCustomTax() {
        val dir = Files.createTempDirectory("auction-config4")
        val path = dir.resolve("auction.yaml")
        path.writeText(
            "tax12h: 1\ntax24h: 2\ntax48h: 3\ntax96h: 4\nmaxActiveListingsPerPlayer: 5\n")
        val config = AuctionConfigLoader(path).load()
        assertEquals(1, config.taxPercentFor(AuctionDuration.H12))
        assertEquals(5, config.maxActiveListingsPerPlayer)
    }
}
