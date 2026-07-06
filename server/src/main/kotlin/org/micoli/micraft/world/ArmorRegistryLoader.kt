package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.StatBonus
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ArmorRegistryLoader")

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

@Serializable
data class ArmorDefinition(
    val wearable: WearableSlots = WearableSlots(),
    val statBonus: StatBonus = StatBonus(),
)

@Serializable
private data class ArmorYamlEntry(
    val wearable: WearableSlots = WearableSlots(),
    val statBonus: StatBonus = StatBonus(),
)

/**
 * Whole-block override, not per-leaf-field: a user overriding `wearable`/`statBonus` must supply
 * the full nested block, same convention as [ServerConfigLoader]'s `AuthSection.oauth`.
 */
@Serializable
private data class ArmorYamlOverride(
    val wearable: WearableSlots? = null,
    val statBonus: StatBonus? = null,
)

private fun ArmorYamlEntry.applyOverride(o: ArmorYamlOverride) =
    copy(wearable = o.wearable ?: wearable, statBonus = o.statBonus ?: statBonus)

class ArmorRegistryLoader(
    private val armorsPath: Path,
    private val dataArmorsPath: Path,
) {
    fun load(): Map<String, ArmorDefinition> {
        if (!armorsPath.exists()) return emptyMap()
        val result =
            armorsPath
                .listDirectoryEntries()
                .filter { it.isDirectory() }
                .mapNotNull { dir ->
                    val name = dir.fileName.toString()
                    val yaml = dir.resolve("$name.yaml")
                    if (!yaml.exists()) return@mapNotNull null
                    runCatching {
                            Yaml.default.decodeFromString(
                                ArmorYamlEntry.serializer(), yaml.readText())
                        }
                        .onFailure { log.warn("Failed to load armor '{}': {}", name, it.message) }
                        .getOrNull()
                        ?.let { entry ->
                            val dataYaml = dataArmorsPath.resolve("$name/$name.yaml")
                            val merged =
                                if (dataYaml.exists()) {
                                    val content = dataYaml.readText()
                                    val overrideResult =
                                        if (content.isNotBlank()) {
                                            runCatching {
                                                Yaml.default.decodeFromString(
                                                    ArmorYamlOverride.serializer(), content)
                                            }
                                        } else Result.success(ArmorYamlOverride())
                                    val override = overrideResult.getOrNull()
                                    val overridden =
                                        override?.let { entry.applyOverride(it) } ?: entry
                                    overrideResult
                                        .mapCatching {
                                            Yaml.default.parseToYamlNode(content.ifBlank { "{}" })
                                        }
                                        .fold(
                                            onSuccess = { node ->
                                                dataYaml.writeText(
                                                    spliceMissingAsComments(
                                                        content,
                                                        yamlConfigSection(
                                                            ArmorYamlEntry::class,
                                                            "",
                                                            overridden,
                                                            node)))
                                                log.debug(
                                                    "Wrote back merged data override for {}", name)
                                            },
                                            onFailure = {
                                                log.warn(
                                                    "Failed to apply override for {}, leaving file untouched: {}",
                                                    name,
                                                    it.message)
                                            })
                                    overridden
                                } else entry
                            name to
                                ArmorDefinition(
                                    wearable = merged.wearable, statBonus = merged.statBonus)
                        }
                }
                .toMap()
        log.info("Armor registry loaded: {} wearable types", result.size)
        return result
    }
}
