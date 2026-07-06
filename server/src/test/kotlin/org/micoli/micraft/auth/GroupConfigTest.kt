package org.micoli.micraft.auth

import com.charleskorn.kaml.Yaml
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupConfigTest {

    private fun defaultResourcesFile(dir: java.nio.file.Path) =
        dir.resolve("groups-defaults.yaml").apply {
            writeText(Yaml.default.encodeToString(GroupsConfig.serializer(), GroupsConfig()))
        }

    @Test
    fun missingFile_createsWithDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("groups.yaml")
        val config = loadGroupsConfig(path, defaultResourcesFile(dir))
        assertTrue(path.toFile().exists(), "groups.yaml should be created")
        assertEquals(GroupsConfig(), config)
    }

    @Test
    fun existingFile_loadsValues() {
        val dir = createTempDirectory()
        val path = dir.resolve("groups.yaml")
        path.writeText(
            """
            groups:
              - name: moderator
                permissions: [kick, mute]
            """
                .trimIndent())
        val config = loadGroupsConfig(path, defaultResourcesFile(dir))
        assertEquals(listOf(GroupEntry("moderator", listOf("kick", "mute"))), config.groups)
    }

    @Test
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("groups.yaml")
        path.writeText("groups: []\n")
        loadGroupsConfig(path, defaultResourcesFile(dir))
        val written = path.readText()
        assertTrue(written.contains("defaultGroups"), "Missing keys must be written back to file")
    }

    @Test
    fun missingField_isMergedFromResourcesDefault() {
        val dir = createTempDirectory()
        val path = dir.resolve("groups.yaml")
        val resources = dir.resolve("groups-defaults.yaml")
        resources.writeText(
            Yaml.default.encodeToString(
                GroupsConfig.serializer(), GroupsConfig(defaultGroups = listOf("guest"))))
        path.writeText("groups: []\n")
        val config = loadGroupsConfig(path, resources)
        assertEquals(
            listOf("guest"), config.defaultGroups, "absent field is active, sourced from resources")
    }

    @Test
    fun corruptFile_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("groups.yaml")
        path.writeText("this: [is: not: valid yaml: }")
        val config = loadGroupsConfig(path, defaultResourcesFile(dir))
        assertEquals(GroupsConfig(), config)
    }

    @Test
    fun corruptFile_leftUntouched() {
        val dir = createTempDirectory()
        val path = dir.resolve("groups.yaml")
        val original = "this: [is: not: valid yaml: }"
        path.writeText(original)
        loadGroupsConfig(path, defaultResourcesFile(dir))
        assertEquals(original, path.readText(), "Unparseable file must not be rewritten")
    }

    @Test
    fun reload_isIdempotent_doesNotDuplicateComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("groups.yaml")
        val resources = defaultResourcesFile(dir)
        path.writeText("groups: []\n")
        loadGroupsConfig(path, resources)
        val afterFirstLoad = path.readText()
        loadGroupsConfig(path, resources)
        val afterSecondLoad = path.readText()
        assertEquals(
            afterFirstLoad, afterSecondLoad, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("defaultGroups").findAll(afterSecondLoad).count())
    }
}
