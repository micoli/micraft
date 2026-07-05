package org.micoli.micraft.trade

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TradeConfigLoader")

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TradeConfig(
    @EncodeDefault(ALWAYS) val maxDistance: Float = 10f,
)

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
