package org.micoli.micraft.world

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

private val projectRoot: Path = Path.of(System.getProperty("projectDir", ".."))

class ResourceYamlDefaultsTest {

    private val blockRequiredKeys = setOf(
        "hardness", "solid", "transparent", "minimapColor", "modelElement",
        "liquid", "viscosity", "replaceable", "vegetationHost", "treeAllowed",
    )
    private val npcRequiredKeys = setOf(
        "behavior", "width", "height", "wanderSpeed", "wanderRadius", "spawn",
    )
    private val spawnRequiredKeys = setOf(
        "autoSpawn", "maxTotal", "maxPerChunk", "spawnBiomes",
    )

    private fun topLevelKeys(content: String): Set<String> =
        content.lines()
            .filter { it.matches(Regex("^[a-zA-Z][a-zA-Z0-9]*:.*")) }
            .map { it.substringBefore(":").trim() }
            .toSet()

    private fun nestedKeys(content: String, section: String): Set<String> {
        val afterSection = content.substringAfter("$section:\n", "")
        return afterSection.lines()
            .takeWhile { it.startsWith("  ") || it.isBlank() }
            .filter { it.matches(Regex("^  [a-zA-Z][a-zA-Z0-9]*:.*")) }
            .map { it.trim().substringBefore(":") }
            .toSet()
    }

    @Test
    fun blocks_allResourceYamlsHaveAllDefaultKeys() {
        val dir = projectRoot.resolve("resources/blocks")
        assertTrue(dir.exists(), "resources/blocks not found")
        val failures = mutableListOf<String>()
        dir.listDirectoryEntries()
            .filter { it.isDirectory() }
            .sortedBy { it.fileName }
            .forEach { subDir ->
                val name = subDir.fileName.toString()
                val yamlFile = subDir.resolve("$name.yaml")
                if (!yamlFile.exists()) return@forEach
                val missing = blockRequiredKeys - topLevelKeys(yamlFile.readText())
                if (missing.isNotEmpty()) failures.add("$name: missing $missing")
            }
        assertTrue(
            failures.isEmpty(),
            "Block resource YAMLs have missing keys — run: ./gradlew :server:patchResourceDefaults\n" +
                failures.joinToString("\n"),
        )
    }

    @Test
    fun entities_allResourceYamlsHaveAllDefaultKeys() {
        val dir = projectRoot.resolve("resources/entities")
        assertTrue(dir.exists(), "resources/entities not found")
        val failures = mutableListOf<String>()
        dir.listDirectoryEntries()
            .filter { it.isDirectory() }
            .sortedBy { it.fileName }
            .forEach { subDir ->
                val name = subDir.fileName.toString()
                val yamlFile = subDir.resolve("$name.yaml")
                if (!yamlFile.exists()) return@forEach
                val content = yamlFile.readText()
                val missingTop = npcRequiredKeys - topLevelKeys(content)
                if (missingTop.isNotEmpty()) failures.add("$name: missing $missingTop")
                val missingSpawn = spawnRequiredKeys - nestedKeys(content, "spawn")
                if (missingSpawn.isNotEmpty()) failures.add("$name.spawn: missing $missingSpawn")
            }
        assertTrue(
            failures.isEmpty(),
            "Entity resource YAMLs have missing keys — run: ./gradlew :server:patchResourceDefaults\n" +
                failures.joinToString("\n"),
        )
    }
}
