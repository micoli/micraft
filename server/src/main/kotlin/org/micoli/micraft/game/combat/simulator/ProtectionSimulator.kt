package org.micoli.micraft.game.combat.simulator

import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.DamageType
import org.micoli.micraft.combat.DiceSpec
import org.micoli.micraft.combat.isMagical
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.combat.SpellDefinition
import org.micoli.micraft.game.combat.protectionSpellGrants
import org.micoli.micraft.game.npc.NpcDefinition
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.ProtectionBonus
import org.micoli.micraft.game.rpg.StatBonus
import org.micoli.micraft.game.world.ZoneTier
import org.micoli.micraft.player.rpg.BaseStats

/**
 * One Character-vs-Danger-tier scenario for the admin survival simulator (spec: Protection Spells
 * §Admin API). [dangerTier] is 1–5 (matches [ZoneTier.tier]); [baseStats] default to 10 everywhere,
 * before the simulated Class's bonuses.
 */
data class ProtectionSimulationInput(
    val level: Int,
    val dangerTier: Int,
    val baseStats: BaseStats = BaseStats(10, 10, 10, 10, 10, 10),
    val equipmentAcBonus: Int = 0,
)

/** One of the tier's allowed NPC types' Attacks, at the Rank that tier uses. */
private data class SimulatedAbility(
    val damageType: DamageType,
    val power: Int,
    val weaponDice: String,
)

data class ClassSurvivalReport(
    val className: String,
    /** Null when the Class has no Protection Spell configured, or none unlocked yet at [level]. */
    val protectionSpellId: String?,
    val protectionRank: Int?,
    val hitChanceWithoutPct: Float,
    val hitChanceWithPct: Float,
    val meanDamageWithoutPerAttack: Float,
    val meanDamageWithPerAttack: Float,
    /**
     * Null when the mean damage per attack is 0: this Class never dies to this tier's Abilities.
     */
    val meanAttacksSurvivedWithout: Float?,
    val meanAttacksSurvivedWith: Float?,
)

data class ProtectionSimulationReport(
    val abilityCount: Int,
    val physicalSharePct: Float,
    val magicalSharePct: Float,
    val classes: List<ClassSurvivalReport>,
)

/**
 * Pure, analytic (no Monte-Carlo, no World, no session) survival report for every Class against a
 * Danger tier's real NPC Abilities, with and without that Class's Protection Spell. Reuses the
 * exact combat rules from [org.micoli.micraft.game.combat.CombatProcessor] and
 * [org.micoli.micraft.game.rpg.DerivedStatsCalculator] rather than duplicating them — see
 * [hitChancePct]'s doc.
 */
object ProtectionSimulator {

    /**
     * Exact P(hit) over a d20, mirroring [org.micoli.micraft.game.combat.CombatProcessor]'s to-hit
     * roll (`resolveAttack` / `handleNpcAttack`): a natural 20 always hits, otherwise `roll +
     * power >= targetAc`.
     */
    fun hitChancePct(power: Int, targetAc: Int): Float =
        // count * 5f rather than count / 20f * 100f: exact in float (100/20 has no remainder),
        // where the division order would round e.g. 12/20 to a value just above 60.
        (1..20).count { roll -> roll == 20 || roll + power >= targetAc } * 5f

    /**
     * Mirrors the malformed-spec fallback of `CombatProcessor`'s private `rollDice` (a fixed roll
     * of 1) for a spec that isn't "NdM"; otherwise the exact mean of the dice.
     */
    private fun diceMean(spec: String): Double {
        val dice = DiceSpec.parse(spec)
        return if (dice.isValid) dice.mean else 1.0
    }

    /**
     * Exact mean damage of a single attack attempt against [targetAc] (before avoidance), folding
     * in the crit-doubles-damage rule: the natural 20 always hits, so it is always among the [hits]
     * qualifying rolls, each of which deals `dice + power` damage except that one, which deals
     * double.
     */
    private fun meanDamagePerAttempt(power: Int, weaponDice: String, targetAc: Int): Double {
        val hits = (1..20).count { roll -> roll == 20 || roll + power >= targetAc }
        if (hits == 0) return 0.0
        return (diceMean(weaponDice) + power) * (hits + 1) / 20.0
    }

    /** The real Attacks of the NPC types allowed in [dangerTier], at that tier's Rank. */
    private fun tierAbilities(
        dangerTier: Int,
        attackRegistry: Map<String, AttackDefinition>,
        npcDefinitions: Map<String, NpcDefinition>,
    ): List<SimulatedAbility> {
        val tier =
            ZoneTier.entries.firstOrNull { it.tier == dangerTier }
                ?: throw IllegalArgumentException("Unknown Danger tier: $dangerTier")
        val npcLevel = tier.npcLevelRange.last
        return npcDefinitions.values
            .filter { it.minLevel <= npcLevel && it.maxLevel >= tier.npcLevelRange.first }
            .flatMap { def ->
                def.attacks.mapNotNull { slot ->
                    val atk =
                        attackRegistry[slot.attackId]?.takeIf { it.enabled }
                            ?: return@mapNotNull null
                    val rank = atk.usableRank(npcLevel) ?: return@mapNotNull null
                    val rankDef = atk.ranks.getValue(rank)
                    SimulatedAbility(atk.damageType, rankDef.power, rankDef.weaponDice)
                }
            }
    }

    fun simulate(
        input: ProtectionSimulationInput,
        classRegistry: Map<String, ClassDefinitionEntry>,
        spellRegistry: Map<String, SpellDefinition>,
        attackRegistry: Map<String, AttackDefinition>,
        npcDefinitions: Map<String, NpcDefinition>,
    ): ProtectionSimulationReport {
        val abilities = tierAbilities(input.dangerTier, attackRegistry, npcDefinitions)
        val physicalCount = abilities.count { !it.damageType.isMagical }
        val magicalCount = abilities.size - physicalCount
        val physicalSharePct =
            if (abilities.isEmpty()) 0f else physicalCount * 100f / abilities.size
        val magicalSharePct = if (abilities.isEmpty()) 0f else magicalCount * 100f / abilities.size

        val classes =
            classRegistry.entries
                .sortedBy { it.key }
                .map { (className, classDef) ->
                    buildClassReport(className, classDef, input, spellRegistry, abilities)
                }
        return ProtectionSimulationReport(
            abilities.size, physicalSharePct, magicalSharePct, classes)
    }

    private fun buildClassReport(
        className: String,
        classDef: ClassDefinitionEntry,
        input: ProtectionSimulationInput,
        spellRegistry: Map<String, SpellDefinition>,
        abilities: List<SimulatedAbility>,
    ): ClassSurvivalReport {
        val effectiveStats =
            DerivedStatsCalculator.effectiveBaseStats(
                input.baseStats,
                listOf(
                    StatBonus(
                        str = classDef.strBonus,
                        dex = classDef.dexBonus,
                        intel = classDef.intelBonus,
                        wis = classDef.wisBonus,
                        con = classDef.conBonus,
                        cha = classDef.chaBonus)))

        // Same resolution as SpellProcessor.ownProtectionSpell, minus the session: the Class's
        // Protection grant applicable at this Level, if any.
        val spellId =
            classDef
                .protectionSpellGrants(spellRegistry)
                .firstOrNull { (grantedAt, _) -> grantedAt <= input.level }
                ?.second
        val spell = spellId?.let { spellRegistry[it] }
        val rank = spell?.usableRank(input.level)
        val rankDef = rank?.let { spell.ranks[it] }
        val protectionBonus =
            rankDef?.let {
                ProtectionBonus(
                    acBonus = it.acBonus,
                    dodgeBonusPct = it.dodgeBonusPct,
                    magicResistBonusPct = it.magicResistBonusPct,
                    maxHpBonus = it.maxHpBonus,
                    hpRegenMultBonus = it.hpRegenMultBonus)
            } ?: ProtectionBonus()

        val withoutDerived =
            DerivedStatsCalculator.compute(effectiveStats, input.level, input.equipmentAcBonus)
        val withDerived =
            DerivedStatsCalculator.compute(
                effectiveStats,
                input.level,
                input.equipmentAcBonus,
                protectionBonus = protectionBonus)

        val (hitWithout, dmgWithout) = aggregate(abilities, withoutDerived)
        val (hitWith, dmgWith) = aggregate(abilities, withDerived)

        return ClassSurvivalReport(
            className = className,
            protectionSpellId = spellId,
            protectionRank = rank,
            hitChanceWithoutPct = hitWithout,
            hitChanceWithPct = hitWith,
            meanDamageWithoutPerAttack = dmgWithout,
            meanDamageWithPerAttack = dmgWith,
            meanAttacksSurvivedWithout = attacksSurvived(withoutDerived.maxHp, dmgWithout),
            meanAttacksSurvivedWith = attacksSurvived(withDerived.maxHp, dmgWith),
        )
    }

    /**
     * Equal-weight average, across [abilities], of the landed-hit chance and the mean damage per
     * attack after that ability's avoidance roll — Dodge for physical/poison, Magic resistance for
     * the rest (ADR-0013), same classification [org.micoli.micraft.game.combat.CombatProcessor]
     * uses via [isMagical].
     */
    private fun aggregate(
        abilities: List<SimulatedAbility>,
        defender: org.micoli.micraft.player.rpg.DerivedStats,
    ): Pair<Float, Float> {
        if (abilities.isEmpty()) return 0f to 0f
        var hitSum = 0.0
        var dmgSum = 0.0
        for (ability in abilities) {
            val avoidPct =
                if (ability.damageType.isMagical) defender.magicResistPct else defender.dodgePct
            // .toInt() mirrors AvoidanceRoll.kt's rollAvoided exactly: the real roll compares a
            // 1-100 int against chancePct.toInt(), so e.g. 4.5% truncates to a 4% chance in-game.
            val landedFraction = 1f - avoidPct.toInt() / 100f
            hitSum += hitChancePct(ability.power, defender.armorClass) * landedFraction
            dmgSum +=
                meanDamagePerAttempt(ability.power, ability.weaponDice, defender.armorClass) *
                    landedFraction
        }
        return (hitSum / abilities.size).toFloat() to (dmgSum / abilities.size).toFloat()
    }

    private fun attacksSurvived(maxHp: Int, meanDamagePerAttack: Float): Float? =
        if (meanDamagePerAttack <= 0f) null else maxHp / meanDamagePerAttack
}
