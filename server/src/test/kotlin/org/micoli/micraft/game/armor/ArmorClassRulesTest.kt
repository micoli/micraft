package org.micoli.micraft.game.armor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.player.rpg.CharacterClass

class ArmorClassRulesTest {
    @Test
    fun mageCanOnlyWearCloth() {
        assertTrue(ArmorClassRules.canWear(CharacterClass.MAGE, ArmorType.CLOTH))
        assertFalse(ArmorClassRules.canWear(CharacterClass.MAGE, ArmorType.PLATE))
        assertFalse(ArmorClassRules.canWear(CharacterClass.MAGE, ArmorType.MAIL))
    }

    @Test
    fun warriorCanWearHeavyArmorOnly() {
        assertTrue(ArmorClassRules.canWear(CharacterClass.WARRIOR, ArmorType.PLATE))
        assertTrue(ArmorClassRules.canWear(CharacterClass.WARRIOR, ArmorType.MAIL))
        assertFalse(ArmorClassRules.canWear(CharacterClass.WARRIOR, ArmorType.CLOTH))
    }
}
