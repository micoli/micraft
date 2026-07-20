package org.micoli.micraft.game.combat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkillsConfigTest {

    private fun resourcesRoot(vararg attackFiles: Pair<String, String>): Path {
        val root = Files.createTempDirectory("skills-resources")
        val attacksDir = root.resolve("attacks").createDirectories()
        val spellsDir = root.resolve("spells").createDirectories()
        for ((name, content) in attackFiles) {
            attacksDir.resolve("$name.yaml").writeText(content)
        }
        spellsDir
            .resolve("tokenRageConsume.yaml")
            .writeText(
                """
                type: TOKEN_RAGE_CONSUME
                enabled: true
                rageGain: 20
                tokenCost: 1
                cooldownMs: 0
                """
                    .trimIndent())
        return root
    }

    private fun defaultResourcesRoot(): Path =
        resourcesRoot(
            "slash" to
                """
                damageType: PHYSICAL
                enabled: true
                levels:
                  1:
                    power: 5
                    weaponDice: 1d8
                    cooldownMs: 800
                  2:
                    power: 8
                    weaponDice: 1d10
                    cooldownMs: 750
                """
                    .trimIndent(),
            "fireball" to
                """
                damageType: FIRE
                enabled: true
                levels:
                  1:
                    power: 8
                    weaponDice: 3d6
                    cooldownMs: 2000
                    manaCost: 15
                """
                    .trimIndent(),
        )

    @Test
    fun missingDataDir_returnsAllResourceDefaults() {
        val resources = defaultResourcesRoot()
        val dataRoot = Files.createTempDirectory("skills-data-missing").resolve("skills")
        val data = SkillsConfig(resources, dataRoot).data
        assertEquals(2, data.attacks.size)
        assertTrue(data.attacks.containsKey("slash"))
        assertTrue(data.attacks.containsKey("fireball"))
        assertEquals(1, data.spells.size)
        assertTrue(data.spells.containsKey("tokenRageConsume"))
    }

    @Test
    fun enabledFalse_excludesEntryFromMap() {
        val resources = defaultResourcesRoot()
        val dataRoot = Files.createTempDirectory("skills-data-disabled")
        dataRoot
            .resolve("attacks")
            .createDirectories()
            .resolve("slash.yaml")
            .writeText("enabled: false")
        dataRoot.resolve("spells").createDirectories()
        val attacks = SkillsConfig(resources, dataRoot).data.attacks
        assertFalse(attacks.containsKey("slash"))
        assertTrue(attacks.containsKey("fireball"))
    }

    @Test
    fun dataFileOverridesField_mergedValueWins() {
        val resources = defaultResourcesRoot()
        val dataRoot = Files.createTempDirectory("skills-data-override")
        dataRoot
            .resolve("attacks")
            .createDirectories()
            .resolve("slash.yaml")
            .writeText(
                """
            levels:
              1:
                power: 99
            """
                    .trimIndent())
        dataRoot.resolve("spells").createDirectories()
        val slash = SkillsConfig(resources, dataRoot).data.attacks["slash"]
        assertNotNull(slash)
        assertEquals(99, slash.levels[1]?.power)
        assertEquals(
            1000,
            slash.levels[1]
                ?.cooldownMs) // Kotlin default; mergeConfig doesn't recurse into map values
    }

    @Test
    fun invalidDataYaml_fallsBackToResourceDefault() {
        val resources = defaultResourcesRoot()
        val dataRoot = Files.createTempDirectory("skills-data-invalid")
        dataRoot
            .resolve("attacks")
            .createDirectories()
            .resolve("slash.yaml")
            .writeText("this: [is: broken}")
        dataRoot.resolve("spells").createDirectories()
        val slash = SkillsConfig(resources, dataRoot).data.attacks["slash"]
        assertNotNull(slash)
        assertEquals(5, slash.levels[1]?.power)
    }

    @Test
    fun multipleLevels_allLoaded() {
        val resources = defaultResourcesRoot()
        val dataRoot = Files.createTempDirectory("skills-data-levels")
        dataRoot.resolve("attacks").createDirectories()
        dataRoot.resolve("spells").createDirectories()
        val slash = SkillsConfig(resources, dataRoot).data.attacks["slash"]
        assertNotNull(slash)
        assertEquals(2, slash.levels.size)
        assertEquals(5, slash.levels[1]?.power)
        assertEquals(8, slash.levels[2]?.power)
        assertEquals(750, slash.levels[2]?.cooldownMs)
    }

    @Test
    fun spellEnabledFalse_excludedFromMap() {
        val resources = defaultResourcesRoot()
        val dataRoot = Files.createTempDirectory("skills-data-spell-disabled")
        dataRoot.resolve("attacks").createDirectories()
        dataRoot
            .resolve("spells")
            .createDirectories()
            .resolve("tokenRageConsume.yaml")
            .writeText("enabled: false")
        val spells = SkillsConfig(resources, dataRoot).data.spells
        assertFalse(spells.containsKey("tokenRageConsume"))
    }

    @Test
    fun spellDataOverridesField_mergedValueWins() {
        val resources = defaultResourcesRoot()
        val dataRoot = Files.createTempDirectory("skills-data-spell-override")
        dataRoot.resolve("attacks").createDirectories()
        dataRoot
            .resolve("spells")
            .createDirectories()
            .resolve("tokenRageConsume.yaml")
            .writeText("rageGain: 50")
        val spell = SkillsConfig(resources, dataRoot).data.spells["tokenRageConsume"]
        assertNotNull(spell)
        assertEquals(50, spell.rageGain)
        assertEquals(1, spell.tokenCost)
    }
}
