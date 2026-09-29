package org.micoli.micraft.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.micoli.micraft.game.classes.ClassesConfig

/**
 * Exercises the real shipped config (not hand-built test fixtures): every Warrior Level-1 Character
 * has Iron Skin available at every Rank 1-5 (spec: Protection Spells).
 */
class ProtectionSpellConfigTest {

    @Test
    fun `iron_skin is a PROTECTION Spell with Ranks 1 through 5`() {
        val spell = SkillsConfig().data.spells["iron_skin"]
        assertNotNull(spell)
        assertEquals(SpellType.PROTECTION, spell.type)
        assertEquals((1..5).toSet(), spell.ranks.keys)
    }

    @Test
    fun `Warrior grants iron_skin Ranks 1 through 5 at Level 1`() {
        val warrior = ClassesConfig().data.classes["WARRIOR"]
        assertNotNull(warrior)
        val level1ProtectionRanks =
            warrior.levels[1]?.spells?.filter { it.spell == "iron_skin" }?.map { it.rank }?.sorted()
        assertEquals((1..5).toList(), level1ProtectionRanks)
    }
}
