package org.micoli.micraft.game.combat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkillsConfigTest {
    private fun resourcesDefault(): Path {
        val dir = Files.createTempDirectory("skills-config-resources")
        val path = dir.resolve("skills.yaml")
        path.writeText(
            """
            attacks:
              slash:
                damageType: PHYSICAL
                levels:
                  1:
                    power: 5
                    weaponDice: 1d8
                    cooldownMs: 800
            spells:
              tokenRageConsume:
                type: TOKEN_RAGE_CONSUME
                rageGain: 20
                tokenCost: 1
                cooldownMs: 0
            """
                .trimIndent())
        return path
    }

    @Test
    fun missingFile_returnsResourceDefaults() {
        val dir = Files.createTempDirectory("skills-config-test")
        val path = dir.resolve("skills.yaml")
        val data = SkillsConfig(path, resourcesDefault()).data
        assertEquals(1, data.attacks.size)
        val slashLevel1 = data.attacks["slash"]?.levels?.get(1)
        assertNotNull(slashLevel1)
        assertEquals(5, slashLevel1.power)
        assertEquals(1, data.spells.size)
        assertTrue(data.spells.containsKey("tokenRageConsume"))
    }

    @Test
    fun validYaml_loadsAttacks() {
        val dir = Files.createTempDirectory("skills-config-test2")
        val path = dir.resolve("skills.yaml")
        path.writeText(
            """
            attacks:
              fireball:
                damageType: FIRE
                levels:
                  1:
                    power: 8
                    weaponDice: 3d6
                    manaCost: 10
            spells: {}
            """
                .trimIndent())
        val attacks = SkillsConfig(path, resourcesDefault()).data.attacks
        assertTrue(attacks.containsKey("fireball"))
        val level1 = attacks["fireball"]?.levels?.get(1)
        assertNotNull(level1)
        assertEquals(8, level1.power)
        assertEquals(10, level1.manaCost)
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val dir = Files.createTempDirectory("skills-config-test3")
        val path = dir.resolve("skills.yaml")
        path.writeText("this: [is: broken}")
        val data = SkillsConfig(path, resourcesDefault()).data
        assertEquals(1, data.attacks.size)
        assertTrue(data.attacks.containsKey("slash"))
    }

    @Test
    fun multipleLevel_loadsAllLevels() {
        val dir = Files.createTempDirectory("skills-config-test4")
        val path = dir.resolve("skills.yaml")
        path.writeText(
            """
            attacks:
              slash:
                damageType: PHYSICAL
                levels:
                  1:
                    power: 5
                    weaponDice: 1d8
                    cooldownMs: 800
                  2:
                    power: 8
                    weaponDice: 1d10
                    cooldownMs: 750
            spells: {}
            """
                .trimIndent())
        val attacks = SkillsConfig(path, resourcesDefault()).data.attacks
        val slash = attacks["slash"]
        assertNotNull(slash)
        assertEquals(2, slash.levels.size)
        assertEquals(5, slash.levels[1]?.power)
        assertEquals(8, slash.levels[2]?.power)
        assertEquals(750, slash.levels[2]?.cooldownMs)
    }

    @Test
    fun validYaml_loadsSpells() {
        val dir = Files.createTempDirectory("skills-config-test5")
        val path = dir.resolve("skills.yaml")
        path.writeText(
            """
            attacks: {}
            spells:
              tokenRageConsume:
                type: TOKEN_RAGE_CONSUME
                rageGain: 30
                tokenCost: 2
                cooldownMs: 500
            """
                .trimIndent())
        val spells = SkillsConfig(path, resourcesDefault()).data.spells
        val spell = spells["tokenRageConsume"]
        assertNotNull(spell)
        assertEquals(30, spell.rageGain)
        assertEquals(2, spell.tokenCost)
        assertEquals(500L, spell.cooldownMs)
    }
}
