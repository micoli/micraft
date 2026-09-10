package org.micoli.micraft.game.world.claim

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ClaimConfigLoader::class.java)

class ClaimConfigLoader(
    private val path: Path = org.micoli.micraft.config.ConfigPaths.dataConfig("claims.yaml")
) {
    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(Yaml.default.encodeToString(ClaimConfig.serializer(), ClaimConfig()))
            log.info("Generated default claim config at {}", path.toAbsolutePath())
        }
    }

    @Volatile private var cached: ClaimConfig? = null

    fun load(): ClaimConfig =
        cached ?: synchronized(this) { cached ?: computeLoad().also { cached = it } }

    private fun computeLoad(): ClaimConfig =
        runCatching { Yaml.default.decodeFromString(ClaimConfig.serializer(), path.readText()) }
            .getOrElse { e ->
                log.warn("Failed to load claims.yaml ({}), using defaults", e.message)
                ClaimConfig()
            }
}
