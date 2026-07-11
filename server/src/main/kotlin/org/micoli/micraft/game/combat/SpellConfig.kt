package org.micoli.micraft.game.combat

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SpellConfig")

class SpellConfig(
    private val path: Path = Path.of("data/config/spells.yaml"),
    private val resourcesPath: Path = Path.of("resources/config/spells.yaml"),
) {
    @Volatile
    var data: SpellConfigData = SpellConfigData()
        private set

    init {
        data = load()
        log.info("Spell config loaded: {} spells", data.spells.size)
    }

    private fun load(): SpellConfigData {
        val default =
            Yaml.default.decodeFromString(SpellConfigData.serializer(), resourcesPath.readText())
        if (!path.exists()) return default
        return runCatching {
                Yaml.default.decodeFromString(SpellConfigData.serializer(), path.readText())
            }
            .getOrElse { e ->
                log.warn("Failed to load spells.yaml ({}), using defaults", e.message)
                default
            }
    }

    fun reload(): SpellConfigData {
        data = load()
        log.info("Spell config reloaded: {} spells", data.spells.size)
        return data
    }
}
