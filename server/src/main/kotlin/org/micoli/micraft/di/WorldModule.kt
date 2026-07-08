package org.micoli.micraft.di

import java.nio.file.Path
import java.time.Instant
import org.koin.dsl.module
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.configDir
import org.micoli.micraft.dataPath
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.world.WorldMetadata
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.biome.BiomeRegistry
import org.micoli.micraft.game.world.biome.loadBiomeRegistry
import org.micoli.micraft.game.world.house.loadHouseConfig
import org.micoli.micraft.game.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.DebugChunkGenerator
import org.micoli.micraft.game.world.road.loadRoadConfig
import org.micoli.micraft.resourcesConfigDir
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("WorldModule")

fun worldName(): String =
    System.getenv("MICRAFT_WORLD_NAME")?.takeIf { it.isNotBlank() } ?: "default_world"

val worldModule = module {
    single {
        val gameConfig = get<GameConfig>()
        if (gameConfig.debugWorld) return@single OptionalWorldPersistence(null)
        val dir = Path.of("$dataPath/world/${worldName()}")
        val persistence =
            WorldPersistence(dir).also { p ->
                if (p.loadMetadata() == null) {
                    p.saveMetadata(
                        WorldMetadata(
                            seed = 42L,
                            generator = "procedural",
                            createdAt = Instant.now().toString()))
                }
            }
        OptionalWorldPersistence(persistence)
    }

    single<BiomeRegistry> {
        val gameConfig = get<GameConfig>()
        if (gameConfig.debugWorld) {
            log.info("Debug world mode — biomes disabled")
            BiomeRegistry.default()
        } else {
            loadBiomeRegistry(
                Path.of("$dataPath/config/biomes.yaml"), resourcesConfigDir.resolve("biomes.yaml"))
        }
    }

    single {
        validateYamlConfig(configDir.resolve("roads.yaml"), "roads.schema.json")
        val gameConfig = get<GameConfig>()
        OptionalRoadConfig(
            if (gameConfig.debugWorld) null
            else
                loadRoadConfig(
                    Path.of("$dataPath/config/roads.yaml"),
                    resourcesConfigDir.resolve("roads.yaml")))
    }

    single {
        val gameConfig = get<GameConfig>()
        OptionalHouseConfig(
            if (gameConfig.debugWorld) null
            else
                loadHouseConfig(
                    Path.of("$dataPath/config/houses.yaml"),
                    resourcesConfigDir.resolve("houses.yaml")))
    }

    single<ChunkGenerator> {
        val gameConfig = get<GameConfig>()
        val generator =
            if (gameConfig.debugWorld) DebugChunkGenerator()
            else
                ProceduralChunkGenerator(
                    seed = 42L,
                    biomeRegistry = get<BiomeRegistry>(),
                    roadConfig = get<OptionalRoadConfig>().value,
                    houseConfig = get<OptionalHouseConfig>().value,
                )
        log.info("World: {} | generator={} | seed=42", worldName(), generator::class.simpleName)
        generator
    }

    single {
        WorldState(
            generator = get<ChunkGenerator>(), persistence = get<OptionalWorldPersistence>().value)
    }
}
