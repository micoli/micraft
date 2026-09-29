package org.micoli.micraft.game.combat.simulator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.AttackRankDefinition
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.classes.ClassLevelEntry
import org.micoli.micraft.game.classes.ClassSpellAccess
import org.micoli.micraft.game.classes.ClassesConfig
import org.micoli.micraft.game.combat.SkillsConfig
import org.micoli.micraft.game.combat.SpellDefinition
import org.micoli.micraft.game.combat.SpellRankDefinition
import org.micoli.micraft.game.combat.SpellType
import org.micoli.micraft.game.npc.NpcAttackSlot
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.npc.NpcRegistryLoader

class ProtectionSimulatorTest {

    // ── Pure formulas (spec: "known inputs" examples) ──────────────────────────

    @Test
    fun `hitChancePct AC 11 vs power 6 is 80 percent`() {
        assertEquals(80f, ProtectionSimulator.hitChancePct(power = 6, targetAc = 11))
    }

    @Test
    fun `hitChancePct a natural 20 always hits regardless of AC`() {
        // power so low that only a natural 20 saves it: 20 - 100 = -80, way under any real AC.
        assertEquals(5f, ProtectionSimulator.hitChancePct(power = -100, targetAc = 999))
    }

    @Test
    fun `hitChancePct Iron Skin Rank 1 drops a power-5 attack's hit chance to 60 percent`() {
        // Default Character AC (10 everywhere, no gear) is 10; Iron Skin Rank 1 is +4 AC (spec).
        val baseAc = 10
        val ironSkinRank1AcBonus = 4
        assertEquals(80f, ProtectionSimulator.hitChancePct(power = 5, targetAc = baseAc))
        assertEquals(
            60f,
            ProtectionSimulator.hitChancePct(power = 5, targetAc = baseAc + ironSkinRank1AcBonus))
    }

    // ── Full simulate() with hand-built fixtures ────────────────────────────────

    private val attackRegistry =
        mapOf(
            "bite" to
                AttackDefinition(
                    damageType = DamageType.PHYSICAL,
                    ranks = mapOf(1 to AttackRankDefinition(power = 6, weaponDice = "1d4"))),
            "curse" to
                AttackDefinition(
                    damageType = DamageType.MAGIC,
                    ranks = mapOf(1 to AttackRankDefinition(power = 2, weaponDice = "1d6"))),
        )

    private val npcDefinitions =
        mapOf(
            "test_wolf" to
                NpcDefinition(
                    type = "test_wolf",
                    behavior = org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior(),
                    bbmodelFile = "wolf",
                    width = 1f,
                    height = 1f,
                    wanderSpeed = 1f,
                    wanderRadius = 1f,
                    attacks = listOf(NpcAttackSlot("bite")),
                    minLevel = 1,
                    maxLevel = 5,
                ),
            "test_shaman" to
                NpcDefinition(
                    type = "test_shaman",
                    behavior = org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior(),
                    bbmodelFile = "shaman",
                    width = 1f,
                    height = 1f,
                    wanderSpeed = 1f,
                    wanderRadius = 1f,
                    attacks = listOf(NpcAttackSlot("curse")),
                    minLevel = 1,
                    maxLevel = 5,
                ),
            "test_dragon" to
                NpcDefinition(
                    type = "test_dragon",
                    behavior = org.micoli.micraft.game.npc.behaviors.StaticNpcBehavior(),
                    bbmodelFile = "dragon",
                    width = 1f,
                    height = 1f,
                    wanderSpeed = 1f,
                    wanderRadius = 1f,
                    attacks = listOf(NpcAttackSlot("bite")),
                    minLevel = 20,
                    maxLevel = 25,
                ),
        )

    private val classRegistry =
        mapOf(
            "WARRIOR" to
                ClassDefinitionEntry(
                    levels =
                        mapOf(
                            1 to
                                ClassLevelEntry(
                                    spells = listOf(ClassSpellAccess("iron_skin", 1))))),
            "MAGE" to ClassDefinitionEntry(),
        )

    private val spellRegistry =
        mapOf(
            "iron_skin" to
                SpellDefinition(
                    type = SpellType.PROTECTION,
                    ranks = mapOf(1 to SpellRankDefinition(durationSec = 60f, acBonus = 4))))

    @Test
    fun `simulate only counts abilities of NPC types allowed in the tier`() {
        val report =
            ProtectionSimulator.simulate(
                ProtectionSimulationInput(level = 1, dangerTier = 1),
                classRegistry,
                spellRegistry,
                attackRegistry,
                npcDefinitions)

        // test_dragon (minLevel 20) is out of Tier 1 (levels 1-5) — only bite + curse count.
        assertEquals(2, report.abilityCount)
        assertEquals(50f, report.physicalSharePct)
        assertEquals(50f, report.magicalSharePct)
    }

    @Test
    fun `simulate resolves each Class's own Protection at the given Level`() {
        val report =
            ProtectionSimulator.simulate(
                ProtectionSimulationInput(level = 1, dangerTier = 1),
                classRegistry,
                spellRegistry,
                attackRegistry,
                npcDefinitions)

        val warrior = report.classes.first { it.className == "WARRIOR" }
        assertEquals("iron_skin", warrior.protectionSpellId)
        assertEquals(1, warrior.protectionRank)
        // Iron Skin's +4 AC can only ever help: hit chance with <= hit chance without.
        assertTrue(warrior.hitChanceWithPct <= warrior.hitChanceWithoutPct)

        val mage = report.classes.first { it.className == "MAGE" }
        assertNull(mage.protectionSpellId)
        assertNull(mage.protectionRank)
        assertEquals(mage.hitChanceWithoutPct, mage.hitChanceWithPct)
    }

    @Test
    fun `simulate reports null attacks survived when the tier deals no damage`() {
        // Tier 3 (levels 11-15) falls between the fixtures' Tier 1 wolf/shaman (1-5) and dragon
        // (20-25) — no NPC type is allowed there, so no Ability lands.
        val report =
            ProtectionSimulator.simulate(
                ProtectionSimulationInput(level = 1, dangerTier = 3),
                classRegistry,
                spellRegistry,
                attackRegistry,
                npcDefinitions)

        assertEquals(0, report.abilityCount)
        val warrior = report.classes.first { it.className == "WARRIOR" }
        assertNull(warrior.meanAttacksSurvivedWithout)
        assertNull(warrior.meanAttacksSurvivedWith)
    }

    @Test
    fun `simulate rejects an unknown Danger tier`() {
        assertTrue(
            runCatching {
                    ProtectionSimulator.simulate(
                        ProtectionSimulationInput(level = 1, dangerTier = 42),
                        classRegistry,
                        spellRegistry,
                        attackRegistry,
                        npcDefinitions)
                }
                .isFailure)
    }

    // ── Real shipped config sanity check ────────────────────────────────────────

    @Test
    fun `simulate against the real shipped config returns a report for every Class`() {
        val report =
            ProtectionSimulator.simulate(
                ProtectionSimulationInput(level = 1, dangerTier = 1),
                ClassesConfig().data.classes,
                SkillsConfig().data.spells,
                SkillsConfig().data.attacks,
                NpcRegistryLoader().load())

        assertTrue(report.classes.isNotEmpty())
        val warrior = report.classes.firstOrNull { it.className == "WARRIOR" }
        assertNotNull(warrior)
        assertEquals("iron_skin", warrior.protectionSpellId)
        assertEquals(1, warrior.protectionRank)
    }
}
