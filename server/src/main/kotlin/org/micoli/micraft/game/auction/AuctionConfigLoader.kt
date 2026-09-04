package org.micoli.micraft.game.auction

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(AuctionConfigLoader::class.java)

class AuctionConfigLoader(private val path: Path) {
    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(Yaml.default.encodeToString(AuctionConfig.serializer(), AuctionConfig()))
            log.info("Generated default auction config at {}", path.toAbsolutePath())
        }
    }

    fun load(): AuctionConfig =
        runCatching { Yaml.default.decodeFromString(AuctionConfig.serializer(), path.readText()) }
            .getOrElse { e ->
                log.warn("Failed to load auction.yaml ({}), using defaults", e.message)
                AuctionConfig()
            }
}
