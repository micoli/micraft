package org.micoli.micraft.game.armor

import org.micoli.micraft.player.rpg.CharacterClass

/** Which [ArmorType]s each class may equip. Classes not listed may wear everything. */
object ArmorClassRules {
    private val allowedTypes: Map<CharacterClass, Set<ArmorType>> =
        mapOf(
            CharacterClass.MAGE to setOf(ArmorType.CLOTH),
            CharacterClass.CLERIC to setOf(ArmorType.CLOTH, ArmorType.MAIL),
            CharacterClass.ROGUE to setOf(ArmorType.LEATHER, ArmorType.MAIL),
            CharacterClass.RANGER to setOf(ArmorType.LEATHER, ArmorType.MAIL),
            CharacterClass.WARRIOR to setOf(ArmorType.MAIL, ArmorType.PLATE),
        )

    fun canWear(characterClass: CharacterClass, armorType: ArmorType): Boolean =
        armorType in (allowedTypes[characterClass] ?: ArmorType.entries.toSet())
}
