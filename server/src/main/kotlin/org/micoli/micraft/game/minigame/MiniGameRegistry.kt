package org.micoli.micraft.game.minigame

import java.nio.file.Path
import kotlinx.serialization.Serializable
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableMapConfig
import org.micoli.micraft.schema.JsonSchemaRoot
import org.micoli.micraft.schema.JsonSchemaRootShape

/**
 * The one thing the server knows about a mini-game: its identity and player bounds. Never its rules
 * — those live entirely in the mini-game's own client-side bundle at [entryUrl].
 */
@Serializable
@JsonSchemaRoot(file = "minigames.schema.json", root = JsonSchemaRootShape.MAP_OF)
data class MiniGameYamlEntry(
    val displayName: String = "",
    val entryUrl: String = "",
    val minPlayers: Int = 1,
    val maxPlayers: Int = 8,
)

@Serializable
data class MiniGameDefinition(
    val gameType: String,
    val displayName: String,
    val entryUrl: String,
    val minPlayers: Int = 1,
    val maxPlayers: Int = 8,
)

/** Loads `data/config/minigames.yaml`, falling back to `resources/config/minigames.yaml`. */
class MiniGameRegistryLoader(
    private val path: Path = ConfigPaths.dataConfig("minigames.yaml"),
    private val resourcesPath: Path = ConfigPaths.resourcesConfig("minigames.yaml"),
) {
    fun load(): Map<String, MiniGameDefinition> =
        loadOverridableMapConfig<MiniGameYamlEntry>(
                "minigames.yaml", OverridablePaths(resourcesPath, path))
            .mapValues { (gameType, e) ->
                MiniGameDefinition(gameType, e.displayName, e.entryUrl, e.minPlayers, e.maxPlayers)
            }
}

/**
 * The list of mini-games the server is willing to host a room for — config only, reloaded by
 * `/reload`. It never sees a mini-game's own code or rules, just this registration.
 */
class MiniGameRegistry
private constructor(
    private val loader: MiniGameRegistryLoader?,
    initial: Map<String, MiniGameDefinition>,
) {
    constructor(
        loader: MiniGameRegistryLoader = MiniGameRegistryLoader()
    ) : this(loader, loader.load())

    @Volatile private var definitions: Map<String, MiniGameDefinition> = initial

    fun all(): List<MiniGameDefinition> = definitions.values.toList()

    fun find(gameType: String): MiniGameDefinition? = definitions[gameType]

    /** No-op when built via [fixed] (tests): there is no loader to reload from. */
    fun reload() {
        loader?.let { definitions = it.load() }
    }

    companion object {
        /** A registry fixed to [definitions], never reloaded — for tests. */
        fun fixed(definitions: Map<String, MiniGameDefinition>): MiniGameRegistry =
            MiniGameRegistry(loader = null, initial = definitions)
    }
}
