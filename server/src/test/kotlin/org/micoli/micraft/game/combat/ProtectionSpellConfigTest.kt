package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.micoli.micraft.game.classes.ClassesConfig

/**
 * Exercises the real shipped config (not hand-built test fixtures): every Class has a Protection
 * Spell available at every Rank 1-5 from Level 1 (spec: Protection Spells).
 */
class ProtectionSpellConfigTest {

    private val protectionByClass =
        mapOf(
            "WARRIOR" to "iron_skin",
            "ROGUE" to "shadowstep",
            "MAGE" to "arcane_ward",
            "RANGER" to "natures_veil",
            "CLERIC" to "fortitude",
        )

    @Test
    fun `every Class's Protection is a PROTECTION Spell with Ranks 1 through 5`() {
        val spells = SkillsConfig().data.spells
        for (protectionId in protectionByClass.values) {
            val spell = spells[protectionId]
            assertNotNull(spell, "missing spell config for $protectionId")
            assertEquals(
                SpellType.PROTECTION, spell.type, "$protectionId should be a PROTECTION Spell")
            assertEquals((1..5).toSet(), spell.ranks.keys, "$protectionId should define Ranks 1-5")
        }
    }

    @Test
    fun `every Class grants its Protection Ranks 1 through 5 at Level 1`() {
        val classes = ClassesConfig().data.classes
        for ((className, protectionId) in protectionByClass) {
            val classDef = classes[className]
            assertNotNull(classDef, "missing class config for $className")
            val level1ProtectionRanks =
                classDef.levels[1]
                    ?.spells
                    ?.filter { it.spell == protectionId }
                    ?.map { it.rank }
                    ?.sorted()
            assertEquals(
                (1..5).toList(),
                level1ProtectionRanks,
                "$className should grant $protectionId Ranks 1-5 at Level 1")
        }
    }
}
