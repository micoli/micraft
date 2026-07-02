package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("StuffRegistryLoader")

@Serializable
data class WearableSlots(
    val head: Boolean = false,
    val body: Boolean = false,
    val rightArm: Boolean = false,
    val leftArm: Boolean = false,
    val rightLeg: Boolean = false,
    val leftLeg: Boolean = false,
) {
    fun toSet(): Set<String> = buildSet {
        if (head) add("head")
        if (body) add("body")
        if (rightArm) add("rightArm")
        if (leftArm) add("leftArm")
        if (rightLeg) add("rightLeg")
        if (leftLeg) add("leftLeg")
    }

    fun overlaps(other: WearableSlots): Boolean = toSet().intersect(other.toSet()).isNotEmpty()
}

@Serializable private data class StuffYamlEntry(val wearable: WearableSlots = WearableSlots())

class StuffRegistryLoader(private val stuffPath: Path) {
    fun load(): Map<String, WearableSlots> {
        if (!stuffPath.exists()) return emptyMap()
        val result =
            stuffPath
                .listDirectoryEntries()
                .filter { it.isDirectory() }
                .mapNotNull { dir ->
                    val name = dir.fileName.toString()
                    val yaml = dir.resolve("$name.yaml")
                    if (!yaml.exists()) return@mapNotNull null
                    runCatching {
                            val entry =
                                Yaml.default.decodeFromString(
                                    StuffYamlEntry.serializer(), yaml.readText())
                            name to entry.wearable
                        }
                        .onFailure { log.warn("Failed to load stuff '{}': {}", name, it.message) }
                        .getOrNull()
                }
                .toMap()
        log.info("Stuff registry loaded: {} wearable types", result.size)
        return result
    }
}
