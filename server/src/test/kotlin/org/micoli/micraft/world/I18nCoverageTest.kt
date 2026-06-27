package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val NESTED_MAP =
    MapSerializer(
        String.serializer(),
        MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())),
    )

// Matches "feature:server:key" or "feature:client:key" string literals in source files.
// Excludes "feature:scope:key" (documentation placeholder).
private val I18N_KEY_LITERAL = Regex("""\"([a-z][a-z0-9_]+:(?:server|client):[a-z][a-z0-9_]+)\"""")

class I18nCoverageTest {

    private val projectRoot: Path = Path.of(System.getProperty("projectDir", ".."))

    private fun loadYamlKeys(dirs: List<Path>): Map<String, Set<String>> {
        val byLocale = mutableMapOf<String, MutableSet<String>>()
        for (dir in dirs) {
            if (!dir.exists()) continue
            dir.listDirectoryEntries("*.yaml").forEach { file ->
                val locale = file.nameWithoutExtension
                runCatching {
                    val raw = Yaml.default.decodeFromString(NESTED_MAP, file.readText())
                    val flat =
                        raw.flatMap { (feature, scopes) ->
                            scopes.flatMap { (scope, keys) ->
                                keys.map { (key, _) -> "$feature:$scope:$key" }
                            }
                        }
                    byLocale.getOrPut(locale) { mutableSetOf() }.addAll(flat)
                }
            }
        }
        return byLocale
    }

    // data/config/ is gitignored (runtime config). Use versioned test fixtures as the canonical
    // translation set, plus plugin data/i18n/ dirs which are versioned.
    private fun i18nDirs(): List<Path> {
        val dirs = mutableListOf<Path>()
        val res = javaClass.classLoader.getResource("i18n")
        if (res != null) dirs.add(Path.of(res.toURI()))
        val pluginsRoot = projectRoot.resolve("plugins")
        if (pluginsRoot.exists()) {
            pluginsRoot
                .toFile()
                .listFiles { f -> f.isDirectory }
                ?.forEach { plugin ->
                    val d = pluginsRoot.resolve(plugin.name).resolve("data/i18n")
                    if (d.exists()) dirs.add(d)
                }
        }
        return dirs
    }

    private fun allYamlKeys(): Map<String, Set<String>> = loadYamlKeys(i18nDirs())

    private fun scanSourceKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        // Production server code
        val serverMain = projectRoot.resolve("server/src/main")
        // Plugin server subdirectories only (exclude plugin test dirs)
        val pluginServerDirs = run {
            val pluginsRoot = projectRoot.resolve("plugins")
            if (!pluginsRoot.exists()) emptyList()
            else
                pluginsRoot
                    .toFile()
                    .listFiles { f -> f.isDirectory }
                    ?.map { pluginsRoot.resolve(it.name).resolve("server") }
                    ?.filter { it.exists() } ?: emptyList()
        }
        // TypeScript frontend (excluding node_modules)
        val tsRoot = projectRoot.resolve("app/webApp/ts-src")
        val roots = listOf(serverMain) + pluginServerDirs + listOf(tsRoot)
        for (root in roots) {
            if (!root.exists()) continue
            root
                .walk()
                .filter { path ->
                    val name = path.fileName.toString()
                    (name.endsWith(".kt") || name.endsWith(".ts") || name.endsWith(".tsx")) &&
                        "node_modules" !in path.toString()
                }
                .forEach { file ->
                    I18N_KEY_LITERAL.findAll(file.readText()).forEach { match ->
                        keys.add(match.groupValues[1])
                    }
                }
        }
        return keys
    }

    @Test
    fun `all i18n keys used in code exist in translation files`() {
        val codeKeys = scanSourceKeys()
        val yamlKeys = allYamlKeys()
        // Every locale must cover all keys used in code.
        val locales = listOf("en", "fr")
        for (locale in locales) {
            val defined = yamlKeys[locale] ?: emptySet()
            val missing = codeKeys - defined
            assertTrue(
                missing.isEmpty(),
                "[$locale] Keys used in code but missing from translation files:\n" +
                    missing.sorted().joinToString("\n") { "  - $it" },
            )
        }
    }

    @Test
    fun `no unused keys in translation files`() {
        val codeKeys = scanSourceKeys()
        val yamlKeys = allYamlKeys()
        // "en" is canonical — check only once to avoid duplicate reports.
        val defined = yamlKeys["en"] ?: emptySet()
        // :client: keys are served to the browser via GET /api/i18n/{locale} and consumed
        // dynamically by the TypeScript UI — they don't appear as literals in source files.
        val checkable = defined.filter { ":client:" !in it }.toSet()
        val unused = checkable - codeKeys
        assertTrue(
            unused.isEmpty(),
            "Keys defined in translation files but never used in code:\n" +
                unused.sorted().joinToString("\n") { "  - $it" },
        )
    }
}
