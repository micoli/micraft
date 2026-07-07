package org.micoli.micraft.combat

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClassesConfigTest {

    private fun resourcesYaml(): java.nio.file.Path {
        val dir = Files.createTempDirectory("classes-config-resources")
        val path = dir.resolve("classes.yaml")
        path.writeText(
            """
            regen:
              regenIntervalMs: 1000
              default:
                hpFormula: "hpRegenPerSec * dt"
                manaFormula: "manaRegenPerSec * dt"
            classes:
              WARRIOR:
                strBonus: 2
                conBonus: 1
                classResource: RAGE
                hpFormula: "hpRegenPerSec * dt"
                manaFormula: "0"
              MAGE:
                intelBonus: 2
                wisBonus: 1
                classResource: MANA
                hpFormula: "hpRegenPerSec * dt * 0.5"
                manaFormula: "manaRegenPerSec * dt * 1.5"
            """
                .trimIndent())
        return path
    }

    @Test
    fun missingFile_returnsDefaults() {
        val dir = Files.createTempDirectory("classes-config-test")
        val path = dir.resolve("classes.yaml")
        val config = ClassesConfig(path, resourcesYaml()).data
        assertEquals(1000L, config.regen.regenIntervalMs)
        assertEquals(2, config.classes.size)
        assertNotNull(config.classes["WARRIOR"])
        assertNotNull(config.classes["MAGE"])
    }

    @Test
    fun loadsClassStatBonuses() {
        val dir = Files.createTempDirectory("classes-config-test2")
        val path = dir.resolve("classes.yaml")
        val config = ClassesConfig(path, resourcesYaml()).data
        val warrior = config.classes["WARRIOR"]!!
        assertEquals(2, warrior.strBonus)
        assertEquals(1, warrior.conBonus)
        assertEquals(0, warrior.dexBonus)
    }

    @Test
    fun loadsClassResource() {
        val dir = Files.createTempDirectory("classes-config-test3")
        val path = dir.resolve("classes.yaml")
        val config = ClassesConfig(path, resourcesYaml()).data
        assertEquals(
            org.micoli.micraft.player.rpg.ClassResource.RAGE,
            config.classes["WARRIOR"]!!.classResource)
        assertEquals(
            org.micoli.micraft.player.rpg.ClassResource.MANA,
            config.classes["MAGE"]!!.classResource)
    }

    @Test
    fun loadsJexlFormulas() {
        val dir = Files.createTempDirectory("classes-config-test4")
        val path = dir.resolve("classes.yaml")
        val config = ClassesConfig(path, resourcesYaml()).data
        assertEquals("hpRegenPerSec * dt * 0.5", config.classes["MAGE"]!!.hpFormula)
        assertEquals("manaRegenPerSec * dt * 1.5", config.classes["MAGE"]!!.manaFormula)
        assertEquals("0", config.classes["WARRIOR"]!!.manaFormula)
    }

    @Test
    fun partialOverride_mergesWithDefaults() {
        val resourcesPath = resourcesYaml()
        val dir = Files.createTempDirectory("classes-config-test5")
        val path = dir.resolve("classes.yaml")
        path.writeText(
            """
            regen:
              regenIntervalMs: 2000
            classes:
              WARRIOR:
                hpFormula: "con * dt"
            """
                .trimIndent())
        val config = ClassesConfig(path, resourcesPath).data
        assertEquals(2000L, config.regen.regenIntervalMs)
        assertEquals("con * dt", config.classes["WARRIOR"]!!.hpFormula)
        // Other WARRIOR fields come from resources default
        assertEquals(2, config.classes["WARRIOR"]!!.strBonus)
        // MAGE class still present from resources
        assertNotNull(config.classes["MAGE"])
    }

    @Test
    fun customClass_addedToRegistry() {
        val resourcesPath = resourcesYaml()
        val dir = Files.createTempDirectory("classes-config-test6")
        val path = dir.resolve("classes.yaml")
        path.writeText(
            """
            classes:
              PALADIN:
                strBonus: 1
                wisBonus: 1
                classResource: MANA
                hpFormula: "hpRegenPerSec * dt * 2"
                manaFormula: "manaRegenPerSec * dt"
            """
                .trimIndent())
        val config = ClassesConfig(path, resourcesPath).data
        assertNotNull(config.classes["PALADIN"])
        assertEquals(1, config.classes["PALADIN"]!!.strBonus)
        assertNotNull(config.classes["WARRIOR"])
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val resourcesPath = resourcesYaml()
        val dir = Files.createTempDirectory("classes-config-test7")
        val path = dir.resolve("classes.yaml")
        path.writeText("this: [is: broken}")
        val config = ClassesConfig(path, resourcesPath).data
        assertEquals(1000L, config.regen.regenIntervalMs)
        assertNotNull(config.classes["WARRIOR"])
    }
}
