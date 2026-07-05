package org.micoli.micraft.combat

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("CombatConfig")

@Serializable
data class CombatConfig(
    val maxCombatRange: Float = 10.0f,
    val downingRollIntervalMs: Long = 3000L,
    val maxRage: Int = 100,
)

class CombatConfigLoader(private val path: Path) {
    fun load(): CombatConfig {
        if (!path.exists()) {
            log.info("No combat.yaml found at {}, using defaults", path)
            return CombatConfig()
        }
        return runCatching {
                Yaml.default.decodeFromString(CombatConfig.serializer(), path.readText())
            }
            .onFailure { log.warn("Failed to load combat.yaml ({}), using defaults", it.message) }
            .getOrElse { CombatConfig() }
            .also { log.info("Combat config loaded: {}", it) }
    }
}
