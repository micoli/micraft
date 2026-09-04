package org.micoli.micraft.game.trade

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(TradeConfigLoader::class.java)

class TradeConfigLoader(private val path: Path) {
    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(Yaml.default.encodeToString(TradeConfig.serializer(), TradeConfig()))
            log.info("Generated default trade config at {}", path.toAbsolutePath())
        }
    }

    fun load(): TradeConfig =
        runCatching { Yaml.default.decodeFromString(TradeConfig.serializer(), path.readText()) }
            .getOrElse { e ->
                log.warn("Failed to load trade.yaml ({}), using defaults", e.message)
                TradeConfig()
            }
}
