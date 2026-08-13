package org.micoli.micraft.auth

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.micoli.micraft.config.isYamlEffectivelyEmpty
import org.micoli.micraft.config.mergeConfig
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.yamlConfigSection
import org.micoli.micraft.schema.JsonSchemaRoot
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GroupConfig")

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonSchemaRoot(file = "groups.schema.json")
data class GroupsConfig(
    @EncodeDefault(ALWAYS) val groups: List<GroupEntry> = emptyList(),
    @EncodeDefault(ALWAYS) val defaultGroups: List<String> = listOf("player"),
) {
    companion object {
        val ADMIN_GROUP = GroupEntry("admin", listOf("*"))
    }

    val allGroups: List<GroupEntry>
        get() = listOf(ADMIN_GROUP) + groups

    fun resolvePermissions(groupNames: List<String>): Set<String> =
        allGroups.filter { it.name in groupNames }.flatMap { it.permissions }.toSet()

    fun resolveDefaultPermissions(): Set<String> = resolvePermissions(defaultGroups)
}

fun loadGroupsConfig(path: Path, resourcesPath: Path): GroupsConfig {
    val default = Yaml.default.decodeFromString(GroupsConfig.serializer(), resourcesPath.readText())
    val originalText = if (path.exists()) path.readText() else ""
    path.parent?.createDirectories()
    if (originalText.isBlank()) {
        path.writeText(
            spliceMissingAsComments("", yamlConfigSection(GroupsConfig::class, "", default, null)))
        return default
    }
    val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
    if (node == null) {
        if (!originalText.isYamlEffectivelyEmpty())
            log.warn("groups.yaml has unparseable structure, leaving file untouched")
        return default
    }
    val decoded =
        runCatching { Yaml.default.decodeFromString(GroupsConfig.serializer(), originalText) }
            .getOrElse { default }
    val merged = mergeConfig(GroupsConfig::class, decoded, default, node)
    path.writeText(
        spliceMissingAsComments(
            originalText, yamlConfigSection(GroupsConfig::class, "", merged, node)))
    return merged
}
