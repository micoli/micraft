package org.micoli.micraft.config

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.system.exitProcess

private fun schemaUri(name: String): URI? =
    object {}::class.java.getResource("/schemas/$name")?.toURI()

private fun MutableList<Pair<Path, String>>.addIfExists(path: Path, schema: String) {
    if (path.exists()) add(path to schema)
}

private fun MutableList<Pair<Path, String>>.addGlob(dir: Path, glob: String, schema: String) {
    if (dir.exists() && dir.isDirectory()) {
        dir.listDirectoryEntries(glob).forEach { add(it to schema) }
    }
}

private fun MutableList<Pair<Path, String>>.addBlockDir(blocksDir: Path, schema: String) {
    if (!blocksDir.exists() || !blocksDir.isDirectory()) return
    blocksDir
        .listDirectoryEntries()
        .filter { it.isDirectory() }
        .forEach { blockDir ->
            val name = blockDir.fileName.toString()
            val yaml = blockDir.resolve("$name.yaml")
            addIfExists(yaml, schema)
        }
}

fun main() {
    val root = Path.of(".")
    val resources = root.resolve("resources")
    val dataConfig = root.resolve("data/config")
    val resourcesConfig = resources.resolve("config")

    val pairs = mutableListOf<Pair<Path, String>>()

    // Single-file configs (resources override + data override)
    for ((name, schema) in
        listOf(
            "biomes.yaml" to "biomes.schema.json",
            "classes.yaml" to "classes.schema.json",
            "combat.yaml" to "combat.schema.json",
            "houses.yaml" to "houses.schema.json",
            "items.yaml" to "items.schema.json",
            "keybindings.yaml" to "keybindings.schema.json",
            "recipes.yaml" to "recipes.schema.json",
            "roads.yaml" to "roads.schema.json",
            "weather.yaml" to "weather.schema.json",
        )) {
        pairs.addIfExists(resourcesConfig.resolve(name), schema)
        pairs.addIfExists(dataConfig.resolve(name), schema)
    }

    pairs.addIfExists(resourcesConfig.resolve("groups.yaml"), "groups.schema.json")
    pairs.addIfExists(dataConfig.resolve("server.yaml"), "server.schema.json")
    pairs.addIfExists(
        dataConfig.resolve("auth/noauth_accounts.yaml"), "noauth-accounts.schema.json")
    pairs.addIfExists(dataConfig.resolve("auth/groups.yaml"), "groups.schema.json")

    // Glob patterns
    pairs.addGlob(dataConfig.resolve("i18n"), "*.yaml", "i18n.schema.json")
    pairs.addGlob(resourcesConfig.resolve("skills/attacks"), "*.yaml", "skill-attack.schema.json")
    pairs.addGlob(dataConfig.resolve("skills/attacks"), "*.yaml", "skill-attack.schema.json")
    pairs.addGlob(resourcesConfig.resolve("skills/spells"), "*.yaml", "skill-spell.schema.json")
    pairs.addGlob(dataConfig.resolve("skills/spells"), "*.yaml", "skill-spell.schema.json")

    // Block directories
    pairs.addBlockDir(resources.resolve("blocks"), "blocks.schema.json")
    pairs.addBlockDir(root.resolve("data/blocks"), "blocks.schema.json")

    val errors = mutableListOf<String>()
    var validated = 0

    for ((yaml, schemaName) in pairs) {
        val uri =
            schemaUri(schemaName)
                ?: run {
                    System.err.println("WARN: no schema resource for $schemaName — skipping $yaml")
                    continue
                }
        val fileErrors = validateYamlErrors(yaml, uri)
        if (fileErrors.isEmpty()) {
            validated++
        } else {
            errors.addAll(fileErrors)
        }
    }

    if (errors.isEmpty()) {
        println("Validated $validated file(s) — 0 errors.")
    } else {
        errors.forEach { System.err.println("ERROR: $it") }
        System.err.println("\nValidated $validated file(s) — ${errors.size} error(s).")
        exitProcess(1)
    }
}
