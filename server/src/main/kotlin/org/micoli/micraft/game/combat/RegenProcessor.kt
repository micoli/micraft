package org.micoli.micraft.game.combat

import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.classes.ClassesConfigData
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.ClassResource
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
    private val tokenAccumulators = mutableMapOf<String, Float>()

    suspend fun tick(sessions: Collection<PlayerSession>) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTickMs
        if (elapsed < config.regen.regenIntervalMs) return
        val dt = elapsed / 1000f
        lastTickMs = now

        for (session in sessions) {
            val charData = session.characterData ?: continue
            if (session.isDowned) continue

            val armors = session.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
            val derived = DerivedStatsCalculator.compute(charData, armors)
            val classDef = config.classes[charData.characterClass.name]
            val hpFormula = classDef?.hpFormula ?: config.regen.default.hpFormula
            val manaFormula = classDef?.manaFormula ?: config.regen.default.manaFormula
            val rageFormula = classDef?.rageFormula ?: "0"

            val inCombat = session.combatState.targetId != null
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
                        "tokens" to charData.currentTokens,
                        "maxTokens" to derived.maxTokens,
                        "inCombat" to inCombat,
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
            val rageDelta = evalFormula(rageFormula, ctx).toInt()

            var newHp = (charData.currentHp + hpInt).coerceIn(0, derived.maxHp)
            var newMana = (charData.currentMana + manaInt).coerceIn(0, derived.maxMana)
            var newRage = (charData.currentRage + rageDelta).coerceIn(0, maxRage)

            // token regen: 1 token per 30s out of combat for RAGE classes
            var newTokens = charData.currentTokens
            if (charData.characterClass.classResource == ClassResource.RAGE &&
                !inCombat &&
                newTokens < derived.maxTokens) {
                val acc = (tokenAccumulators[session.id] ?: 0f) + dt / 30f
                val awarded = acc.toInt()
                tokenAccumulators[session.id] = acc - awarded
                newTokens = (newTokens + awarded).coerceAtMost(derived.maxTokens)
            } else if (inCombat || newTokens >= derived.maxTokens) {
                tokenAccumulators.remove(session.id)
            }

            if (newHp == charData.currentHp &&
                newMana == charData.currentMana &&
                newRage == charData.currentRage &&
                newTokens == charData.currentTokens)
                continue

            session.characterData =
                charData.copy(
                    currentHp = newHp,
                    currentMana = newMana,
                    currentRage = newRage,
                    currentTokens = newTokens,
                )
            session.send(
                combatProcessor.makeStatusUpdate(
                    session.characterData!!,
                    derived,
                    session.state.stance,
                    session.combatState.attackCooldownUntilMs,
                ))
        }
    }

    fun clearTokenAccumulator(sessionId: String) {
        tokenAccumulators.remove(sessionId)
    }

    private fun evalFormula(formula: String, ctx: MapContext): Double =
        runCatching { (jexl.createExpression(formula).evaluate(ctx) as? Number)?.toDouble() ?: 0.0 }
            .getOrElse { e ->
                log.warn("Regen formula '{}' failed: {}", formula, e.message)
                0.0
            }
}
