package org.micoli.micraft.combat

import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import org.micoli.micraft.rpg.character.DerivedStatsCalculator
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.ArmorDefinition
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("RegenProcessor")

class RegenProcessor(
    private val config: ClassesConfigData,
    private val maxRage: Int,
    private val armorRegistry: Map<String, ArmorDefinition>,
    private val combatProcessor: CombatProcessor,
) {
    private var lastTickMs = System.currentTimeMillis()
    private val jexl = JexlBuilder().silent(false).strict(true).create()

    suspend fun tick(sessions: Collection<PlayerSession>) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTickMs
        if (elapsed < config.regen.regenIntervalMs) return
        val dt = elapsed / 1000f
        lastTickMs = now

        for (session in sessions) {
            val charData = session.characterData ?: continue
            if (session.combatState.isDowned) continue

            val armors = session.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
            val derived = DerivedStatsCalculator.compute(charData, armors)
            val classDef = config.classes[charData.characterClass.name]
            val hpFormula = classDef?.hpFormula ?: config.regen.default.hpFormula
            val manaFormula = classDef?.manaFormula ?: config.regen.default.manaFormula

            val effects =
                session.combatState.activeEffects.map { it.effect::class.simpleName ?: "" }
            val ctx =
                MapContext(
                    mapOf(
                        "hp" to charData.currentHp,
                        "maxHp" to derived.maxHp,
                        "mana" to charData.currentMana,
                        "maxMana" to derived.maxMana,
                        "rage" to charData.currentRage,
                        "maxRage" to maxRage,
                        "con" to charData.baseStats.con,
                        "wis" to charData.baseStats.wis,
                        "str" to charData.baseStats.str,
                        "dex" to charData.baseStats.dex,
                        "intel" to charData.baseStats.intel,
                        "cha" to charData.baseStats.cha,
                        "level" to charData.level,
                        "hpRegenPerSec" to derived.hpRegenPerSec,
                        "manaRegenPerSec" to derived.manaRegenPerSec,
                        "activeEffects" to effects,
                        "dt" to dt,
                    ))

            val hpInt = evalFormula(hpFormula, ctx).toInt()
            val manaInt = evalFormula(manaFormula, ctx).toInt()
            if (hpInt == 0 && manaInt == 0) continue

            val newHp = (charData.currentHp + hpInt).coerceIn(0, derived.maxHp)
            val newMana = (charData.currentMana + manaInt).coerceIn(0, derived.maxMana)
            if (newHp == charData.currentHp && newMana == charData.currentMana) continue

            session.characterData = charData.copy(currentHp = newHp, currentMana = newMana)
            session.send(
                combatProcessor.makeStatusUpdate(
                    session.characterData!!,
                    derived,
                    session.state.stance,
                    session.combatState.attackCooldownUntilMs,
                ))
        }
    }

    private fun evalFormula(formula: String, ctx: MapContext): Double =
        runCatching { (jexl.createExpression(formula).evaluate(ctx) as? Number)?.toDouble() ?: 0.0 }
            .getOrElse { e ->
                log.warn("Regen formula '{}' failed: {}", formula, e.message)
                0.0
            }
}
