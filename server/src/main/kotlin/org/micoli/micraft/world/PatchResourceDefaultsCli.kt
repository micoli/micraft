package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main() {
    val projectDir = Path.of(".")
    var patched = 0

    fun <T> patchDir(dir: Path, label: String, serializer: kotlinx.serialization.KSerializer<T>, requiredKeys: Set<String>) {
        if (!dir.exists()) { println("  SKIP: $dir not found"); return }
        dir.listDirectoryEntries()
            .filter { it.isDirectory() }
            .sortedBy { it.fileName }
            .forEach { subDir ->
                val name = subDir.fileName.toString()
                val yamlFile = subDir.resolve("$name.yaml")
                if (!yamlFile.exists()) return@forEach
                runCatching {
                    val content = yamlFile.readText()
                    val presentKeys = content.lines()
                        .filter { it.matches(Regex("^[a-zA-Z][a-zA-Z0-9]*:.*")) }
                        .map { it.substringBefore(":").trim() }
                        .toSet()
                    val missing = requiredKeys - presentKeys
                    if (missing.isNotEmpty()) {
                        val parsed = Yaml.default.decodeFromString(serializer, content)
                        yamlFile.writeText(Yaml.default.encodeToString(serializer, parsed))
                        println("  patched $label/$name (added: $missing)")
                        patched++
                    }
                }.onFailure { println("  WARN: $label/$name: ${it.message}") }
            }
    }

    val blockKeys = setOf(
        "hardness", "solid", "transparent", "minimapColor", "modelElement",
        "liquid", "viscosity", "replaceable", "vegetationHost", "treeAllowed",
    )
    val npcKeys = setOf("behavior", "width", "height", "wanderSpeed", "wanderRadius", "spawn")

    println("Patching blocks...")
    patchDir(projectDir.resolve("resources/blocks"), "blocks", BlockDefinition.serializer(), blockKeys)
    println("Patching entities...")
    patchDir(projectDir.resolve("resources/entity"), "entity", NpcYamlEntry.serializer(), npcKeys)
    println("Done — $patched file(s) patched.")
}
