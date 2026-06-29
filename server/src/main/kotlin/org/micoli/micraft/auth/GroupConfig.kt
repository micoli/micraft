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

@Serializable
data class GroupEntry(
    val name: String,
    val permissions: List<String> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
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

fun loadGroupsConfig(path: Path): GroupsConfig {
    val config =
        if (path.exists())
            runCatching {
                    Yaml.default.decodeFromString(GroupsConfig.serializer(), path.readText())
                }
                .getOrElse { GroupsConfig() }
        else GroupsConfig()
    path.parent?.createDirectories()
    path.writeText(Yaml.default.encodeToString(GroupsConfig.serializer(), config))
    return config
}
