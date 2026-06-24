package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val SECTION_SERIALIZER =
    MapSerializer(
        String.serializer(),
        MapSerializer(String.serializer(), ListSerializer(String.serializer())))

fun loadKeyBindings(path: Path): Map<String, List<String>> {
    if (!path.exists()) return emptyMap()
    val sections =
        runCatching { Yaml.default.decodeFromString(SECTION_SERIALIZER, path.readText()) }
            .getOrElse {
                return emptyMap()
            }
    return buildMap { for ((_, actions) in sections) putAll(actions) }
}
