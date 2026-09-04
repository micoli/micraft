package org.micoli.micraft.game

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(GameTimePersistence::class.java)

@Serializable private data class GameTimeData(val gameTimeSeconds: Double)

object GameTimePersistence {
    fun load(path: Path, service: GameTimeService) {
        if (!path.exists()) return
        runCatching {
                val data = Yaml.default.decodeFromString(GameTimeData.serializer(), path.readText())
                service.load(data.gameTimeSeconds)
                log.info(
                    "Game time restored: {:.1f}s (day {:.2f})",
                    data.gameTimeSeconds,
                    service.currentGameDay)
            }
            .onFailure { log.warn("Failed to load game time: {}", it.message) }
    }

    fun save(path: Path, service: GameTimeService) {
        runCatching {
                path.parent?.createDirectories()
                path.writeText(
                    Yaml.default.encodeToString(
                        GameTimeData.serializer(), GameTimeData(service.gameTimeSeconds)))
            }
            .onFailure { log.warn("Failed to save game time: {}", it.message) }
    }
}
