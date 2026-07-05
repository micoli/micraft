package org.micoli.micraft.combat

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttackRegistryLoaderTest {
    @Test
    fun missingDir_returnsEmpty() {
        val tmp = Files.createTempDirectory("attacks-test")
        val dir = tmp.resolve("nonexistent")
        assertTrue(AttackRegistryLoader(dir).load().isEmpty())
    }

    @Test
    fun emptyDir_returnsEmpty() {
        val tmp = Files.createTempDirectory("attacks-empty")
        assertTrue(AttackRegistryLoader(tmp).load().isEmpty())
    }

    @Test
    fun validYaml_loadsAttack() {
        val tmp = Files.createTempDirectory("attacks-valid")
        tmp.resolve("slash.yaml").writeText("power: 5\nweaponDice: 2d6\nmanaCost: 0\n")
        val attacks = AttackRegistryLoader(tmp).load()
        assertEquals(1, attacks.size)
        val slash = attacks["slash"]
        assertEquals(5, slash?.power)
        assertEquals("2d6", slash?.weaponDice)
    }

    @Test
    fun invalidYaml_skipsEntry() {
        val tmp = Files.createTempDirectory("attacks-mixed")
        tmp.resolve("valid.yaml").writeText("power: 3\n")
        tmp.resolve("invalid.yaml").writeText("not: [valid: yaml}")
        val attacks = AttackRegistryLoader(tmp).load()
        assertEquals(1, attacks.size)
        assertTrue(attacks.containsKey("valid"))
        assertFalse(attacks.containsKey("invalid"))
    }

    @Test
    fun multipleValidFiles_loadedAll() {
        val tmp = Files.createTempDirectory("attacks-multi")
        tmp.resolve("fireball.yaml").writeText("power: 8\nweaponDice: 3d6\nmanaCost: 10\n")
        tmp.resolve("slash.yaml").writeText("power: 5\nweaponDice: 1d8\n")
        val attacks = AttackRegistryLoader(tmp).load()
        assertEquals(2, attacks.size)
        assertTrue(attacks.containsKey("fireball"))
        assertTrue(attacks.containsKey("slash"))
        assertEquals(8, attacks["fireball"]?.power)
        assertEquals(10, attacks["fireball"]?.manaCost)
    }

    @Test
    fun nonYamlFiles_ignored() {
        val tmp = Files.createTempDirectory("attacks-nonYaml")
        tmp.resolve("valid.yaml").writeText("power: 3\n")
        tmp.resolve("readme.txt").writeText("some text")
        tmp.resolve("data.json").writeText("{\"power\": 1}")
        val attacks = AttackRegistryLoader(tmp).load()
        assertEquals(1, attacks.size)
    }
}
