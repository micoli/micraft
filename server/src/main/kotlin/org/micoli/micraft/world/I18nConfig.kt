package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

private val log = LoggerFactory.getLogger("I18nConfig")

private val NESTED_MAP_SERIALIZER = MapSerializer(
    String.serializer(),
    MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())),
)

class I18nConfig(private val dir: Path) {
    // locale → flat "feature:scope:key" → translation
    @Volatile private var tables: Map<String, Map<String, String>> = emptyMap()

    val locales: Set<String> get() = tables.keys

    init { reload() }

    fun reload() {
        if (!dir.exists()) {
            log.warn("i18n directory not found at {}, no translations loaded", dir.toAbsolutePath())
            tables = mapOf("en" to emptyMap())
            return
        }
        val loaded = mutableMapOf<String, Map<String, String>>()
        dir.listDirectoryEntries("*.yaml").forEach { file ->
            val locale = file.nameWithoutExtension
            runCatching {
                val raw = Yaml.default.decodeFromString(NESTED_MAP_SERIALIZER, file.readText())
                loaded[locale] = raw.flatMap { (feature, scopes) ->
                    scopes.flatMap { (scope, keys) ->
                        keys.map { (key, value) -> "$feature:$scope:$key" to value }
                    }
                }.toMap()
                log.info("i18n loaded: {} ({} keys)", locale, loaded[locale]!!.size)
            }.onFailure { e ->
                log.warn("Failed to load i18n/{}.yaml: {}", locale, e.message)
            }
        }
        if ("en" !in loaded) loaded["en"] = emptyMap()
        tables = loaded
    }

    fun t(locale: String, key: String, vararg args: Any): String {
        val translation = tables[locale]?.get(key) ?: tables["en"]?.get(key) ?: key
        return args.foldIndexed(translation) { i, acc, arg -> acc.replace("{$i}", arg.toString()) }
    }

    fun clientKeys(locale: String): Map<String, String> =
        (tables[locale] ?: tables["en"] ?: emptyMap()).filterKeys { ":client:" in it }
}
