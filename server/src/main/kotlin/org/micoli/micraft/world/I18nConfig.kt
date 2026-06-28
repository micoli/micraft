package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.net.JarURLConnection
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("I18nConfig")

private val NESTED_MAP_SERIALIZER =
    MapSerializer(
        String.serializer(),
        MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())),
    )

class I18nConfig(
    private val dirs: List<Path>,
    private val classpathYamls: Map<String, String> = emptyMap(),
) {
    constructor(dir: Path) : this(listOf(dir))

    companion object {
        fun fromClasspath(
            classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
            pluginsRoot: Path? = null,
        ): I18nConfig {
            val dirs = mutableListOf<Path>()
            val classpathYamls = mutableMapOf<String, String>()

            classLoader.getResources("i18n").toList().forEach { url ->
                when (url.protocol) {
                    "file" ->
                        runCatching { dirs.add(Path.of(url.toURI())) }
                            .onFailure {
                                log.warn("i18n: cannot resolve file URL {}: {}", url, it.message)
                            }
                    "jar" ->
                        runCatching {
                                val conn = url.openConnection() as JarURLConnection
                                conn.jarFile.use { jar ->
                                    jar.entries()
                                        .asSequence()
                                        .filter {
                                            it.name.startsWith("i18n/") && it.name.endsWith(".yaml")
                                        }
                                        .forEach { entry ->
                                            val locale =
                                                entry.name
                                                    .removePrefix("i18n/")
                                                    .removeSuffix(".yaml")
                                            classpathYamls[locale] =
                                                jar.getInputStream(entry)
                                                    .bufferedReader()
                                                    .readText()
                                        }
                                }
                            }
                            .onFailure {
                                log.warn("i18n: cannot read jar URL {}: {}", url, it.message)
                            }
                    else -> log.warn("i18n: unsupported URL protocol {} for {}", url.protocol, url)
                }
            }

            pluginsRoot
                ?.takeIf { it.toFile().exists() }
                ?.toFile()
                ?.listFiles { f -> f.isDirectory }
                ?.forEach { plugin ->
                    val d = pluginsRoot.resolve(plugin.name).resolve("resources/i18n")
                    if (d.toFile().exists()) dirs.add(d)
                }

            return I18nConfig(dirs, classpathYamls)
        }
    }

    // locale → flat "feature:scope:key" → translation
    @Volatile private var tables: Map<String, Map<String, String>> = emptyMap()

    val locales: Set<String>
        get() = tables.keys

    init {
        reload()
    }

    fun reload() {
        val merged = mutableMapOf<String, MutableMap<String, String>>()

        for ((locale, yaml) in classpathYamls) {
            mergeYaml(locale, yaml, source = "classpath", merged)
        }

        for (dir in dirs) {
            if (!dir.exists()) {
                log.warn("i18n directory not found at {}, skipping", dir.toAbsolutePath())
                continue
            }
            dir.listDirectoryEntries("*.yaml").forEach { file ->
                val locale = file.nameWithoutExtension
                mergeYaml(locale, file.readText(), source = dir.toString(), merged)
            }
        }

        if ("en" !in merged) merged["en"] = mutableMapOf()
        tables = merged
    }

    private fun mergeYaml(
        locale: String,
        yaml: String,
        source: String,
        merged: MutableMap<String, MutableMap<String, String>>,
    ) {
        runCatching {
                val raw = Yaml.default.decodeFromString(NESTED_MAP_SERIALIZER, yaml)
                val flat =
                    raw.flatMap { (feature, scopes) ->
                            scopes.flatMap { (scope, keys) ->
                                keys.map { (key, value) -> "$feature:$scope:$key" to value }
                            }
                        }
                        .toMap()
                merged.getOrPut(locale) { mutableMapOf() }.putAll(flat)
                log.debug("i18n merged: {} from {} ({} keys)", locale, source, flat.size)
            }
            .onFailure { e -> log.warn("Failed to load i18n {}/{}: {}", source, locale, e.message) }
    }

    fun t(locale: String, key: String, vararg args: Any): String {
        val translation = tables[locale]?.get(key) ?: tables["en"]?.get(key) ?: key
        return args.foldIndexed(translation) { i, acc, arg -> acc.replace("{$i}", arg.toString()) }
    }

    fun clientKeys(locale: String): Map<String, String> =
        (tables[locale] ?: tables["en"] ?: emptyMap()).filterKeys { ":client:" in it }
}
