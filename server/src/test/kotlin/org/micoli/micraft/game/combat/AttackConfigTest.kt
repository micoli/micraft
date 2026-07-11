package org.micoli.micraft.game.combat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttackConfigTest {
    private fun resourcesDefault(): Path {
        val dir = Files.createTempDirectory("attack-config-resources")
        val path = dir.resolve("attack.yaml")
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
            """
                .trimIndent())
        return path
    }

    @Test
    fun missingFile_returnsResourceDefaults() {
        val dir = Files.createTempDirectory("attack-config-test")
        val path = dir.resolve("attack.yaml")
        val attacks = AttackConfig(path, resourcesDefault()).data.attacks
        assertEquals(1, attacks.size)
        val slashLevel1 = attacks["slash"]?.levels?.get(1)
        assertNotNull(slashLevel1)
        assertEquals(5, slashLevel1.power)
    }

    @Test
    fun validYaml_loadsAttacks() {
        val dir = Files.createTempDirectory("attack-config-test2")
        val path = dir.resolve("attack.yaml")
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
            """
                .trimIndent())
        val attacks = AttackConfig(path, resourcesDefault()).data.attacks
        assertTrue(attacks.containsKey("fireball"))
        val level1 = attacks["fireball"]?.levels?.get(1)
        assertNotNull(level1)
        assertEquals(8, level1.power)
        assertEquals(10, level1.manaCost)
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val dir = Files.createTempDirectory("attack-config-test3")
        val path = dir.resolve("attack.yaml")
        path.writeText("this: [is: broken}")
        val attacks = AttackConfig(path, resourcesDefault()).data.attacks
        assertEquals(1, attacks.size)
        assertTrue(attacks.containsKey("slash"))
    }

    @Test
    fun multipleLevel_loadsAllLevels() {
        val dir = Files.createTempDirectory("attack-config-test4")
        val path = dir.resolve("attack.yaml")
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
            """
                .trimIndent())
        val attacks = AttackConfig(path, resourcesDefault()).data.attacks
        val slash = attacks["slash"]
        assertNotNull(slash)
        assertEquals(2, slash.levels.size)
        assertEquals(5, slash.levels[1]?.power)
        assertEquals(8, slash.levels[2]?.power)
        assertEquals(750, slash.levels[2]?.cooldownMs)
    }
}
