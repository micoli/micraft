package org.micoli.micraft.game.world.block

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import org.micoli.micraft.config.validateYamlConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(BlockIdRegistryLoader::class.java)

@Serializable private data class BlockIdsYaml(val blocks: Map<String, Int> = emptyMap())

/**
 * Persists the block-name -> wire-id assignment used to store blocks compactly on disk (see
 * [org.micoli.micraft.game.world.BlockRegistry.wireIndex]). An id, once assigned, is never reused
 * or reassigned: block names discovered under `resources/blocks` that aren't in the shipped or
 * persisted ledger yet get the next free id appended to [path], so existing world saves keep
 * decoding to the right block type across releases.
 */
class BlockIdRegistryLoader(
    private val path: Path,
    private val resourcesPath: Path = Path.of("resources/config/block_ids.yaml"),
) {
    private fun decode(text: String): Map<String, Int> =
        if (text.isBlank()) emptyMap()
        else Yaml.default.decodeFromString(BlockIdsYaml.serializer(), text).blocks

    fun load(discoveredNames: Set<String>): Map<String, Int> {
        val shipped = decode(resourcesPath.readText())
        val persisted = if (path.exists()) decode(path.readText()) else emptyMap()
        val assigned = (shipped + persisted).toMutableMap()
        assigned.putIfAbsent("AIR", 0)
        var nextId = (assigned.values.maxOrNull() ?: -1) + 1
        val newNames = (discoveredNames + "AIR" - assigned.keys).sorted()
        for (name in newNames) assigned[name] = nextId++

        if (newNames.isNotEmpty() || assigned != persisted) {
            path.parent.createDirectories()
            val ordered = assigned.entries.sortedBy { it.value }.associate { it.key to it.value }
            path.writeText(
                Yaml.default.encodeToString(BlockIdsYaml.serializer(), BlockIdsYaml(ordered)))
            if (newNames.isNotEmpty())
                log.info("Assigned wire ids to new block(s): {}", newNames.joinToString(", "))
        }
        validateYamlConfig(path, "block_ids.schema.json")
        return assigned
    }
}
