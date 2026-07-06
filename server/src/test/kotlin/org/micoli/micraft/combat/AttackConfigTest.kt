package org.micoli.micraft.combat

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttackConfigTest {
    private fun resourcesDefault(): java.nio.file.Path {
        val dir = Files.createTempDirectory("attack-config-resources")
        val path = dir.resolve("attack.yaml")
        path.writeText("attacks:\n  slash:\n    power: 5\n    weaponDice: 1d8\n")
        return path
    }

    @Test
    fun missingFile_returnsResourceDefaults() {
        val dir = Files.createTempDirectory("attack-config-test")
        val path = dir.resolve("attack.yaml")
        val attacks = AttackConfig(path, resourcesDefault()).data.attacks
        assertEquals(1, attacks.size)
        assertEquals(5, attacks["slash"]?.power)
    }

    @Test
    fun validYaml_loadsAttacks() {
        val dir = Files.createTempDirectory("attack-config-test2")
        val path = dir.resolve("attack.yaml")
        path.writeText(
            "attacks:\n  fireball:\n    power: 8\n    weaponDice: 3d6\n    manaCost: 10\n")
        val attacks = AttackConfig(path, resourcesDefault()).data.attacks
        assertTrue(attacks.containsKey("fireball"))
        assertEquals(8, attacks["fireball"]?.power)
        assertEquals(10, attacks["fireball"]?.manaCost)
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
}
