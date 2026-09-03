package org.micoli.micraft.di

import java.nio.file.Path
import java.time.Instant
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.configDir
import org.micoli.micraft.dataPath
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.world.WorldMetadata
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.actionblock.ActionBlockRegistry
import org.micoli.micraft.game.world.biome.BiomeRegistry
import org.micoli.micraft.game.world.biome.loadBiomeRegistry
import org.micoli.micraft.game.world.claim.ClaimRegistry
import org.micoli.micraft.game.world.house.loadHouseConfig
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.EndToEndBoundedChunkGenerator
import org.micoli.micraft.game.world.road.loadRoadConfig
import org.micoli.micraft.game.world.scene.SceneRegistry
import org.micoli.micraft.resourcesConfigDir
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("WorldModule")

fun worldName(): String =
    System.getenv("MICRAFT_WORLD_NAME")?.takeIf { it.isNotBlank() } ?: "default_world"

@Module
class WorldModule {
    @Single
    fun optionalWorldPersistence(gameConfig: GameConfig): OptionalWorldPersistence {
        val dir = Path.of("$dataPath/world/${worldName()}")
        val persistence =
            WorldPersistence(dir).also { p ->
                if (p.loadMetadata() == null) {
                    p.saveMetadata(
                        WorldMetadata(
                            seed = gameConfig.worldSeed,
                            generator = "procedural",
                            createdAt = Instant.now().toString()))
                }
            }
        return OptionalWorldPersistence(persistence)
    }

    @Single
    fun biomeRegistry(): BiomeRegistry =
        loadBiomeRegistry(
            Path.of("$dataPath/config/biomes.yaml"), resourcesConfigDir.resolve("biomes.yaml"))

    @Single
    fun optionalRoadConfig(): OptionalRoadConfig {
        validateYamlConfig(configDir.resolve("roads.yaml"), "roads.schema.json")
        return OptionalRoadConfig(
            loadRoadConfig(
                Path.of("$dataPath/config/roads.yaml"), resourcesConfigDir.resolve("roads.yaml")))
    }

    @Single
    fun optionalHouseConfig(): OptionalHouseConfig =
        OptionalHouseConfig(
            loadHouseConfig(
                Path.of("$dataPath/config/houses.yaml"), resourcesConfigDir.resolve("houses.yaml")))

    @Single
    fun chunkGenerator(
        gameConfig: GameConfig,
        biomeRegistry: BiomeRegistry,
        optionalRoadConfig: OptionalRoadConfig,
        optionalHouseConfig: OptionalHouseConfig,
    ): ChunkGenerator {
        System.getenv("MICRAFT_E2E_BOUNDS")
            ?.takeIf { it.isNotBlank() }
            ?.let { spec ->
                val (w, h) = spec.lowercase().split("x").map { it.trim().toInt() }
                val groundY = System.getenv("MICRAFT_E2E_GROUND_Y")?.toIntOrNull() ?: 64
                log.info(
                    "World: {} | generator=E2E bounded {}x{} groundY={}",
                    worldName(),
                    w,
                    h,
                    groundY)
                return EndToEndBoundedChunkGenerator(
                    halfChunksX = w / 2, halfChunksZ = h / 2, groundY = groundY)
            }
        val generator =
            ProceduralChunkGenerator(
                seed = gameConfig.worldSeed,
                biomeRegistry = biomeRegistry,
                roadConfig = optionalRoadConfig.value,
                houseConfig = optionalHouseConfig.value,
            )
        log.info(
            "World: {} | generator={} | seed={}",
            worldName(),
            generator::class.simpleName,
            gameConfig.worldSeed)
        return generator
    }

    @Single
    fun worldState(
        chunkGenerator: ChunkGenerator,
        optionalWorldPersistence: OptionalWorldPersistence,
    ): WorldState =
        WorldState(generator = chunkGenerator, persistence = optionalWorldPersistence.value)

    @Single
    fun instanceRegistry(optionalWorldPersistence: OptionalWorldPersistence): InstanceRegistry =
        InstanceRegistry(optionalWorldPersistence.value)

    @Single
    fun claimRegistry(optionalWorldPersistence: OptionalWorldPersistence): ClaimRegistry =
        ClaimRegistry(optionalWorldPersistence.value)

    @Single
    fun sceneRegistry(optionalWorldPersistence: OptionalWorldPersistence): SceneRegistry =
        SceneRegistry(optionalWorldPersistence.value)

    @Single
    fun actionBlockRegistry(
        optionalWorldPersistence: OptionalWorldPersistence
    ): ActionBlockRegistry = ActionBlockRegistry(optionalWorldPersistence.value)
}
