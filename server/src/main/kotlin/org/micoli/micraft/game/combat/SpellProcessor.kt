package org.micoli.micraft.game.combat

import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.ClassResource
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SpellProcessor")

class SpellProcessor(
    private val spellRegistry: Map<String, SpellDefinition>,
    private val classRegistry: Map<String, ClassDefinitionEntry>,
    private val armorRegistry: Map<String, ArmorDefinition>,
    private val combatConfig: CombatConfigData,
    private val combatProcessor: CombatProcessor,
) {
    private val cooldowns = mutableMapOf<String, Long>()

    suspend fun handleSpell(session: PlayerSession, msg: ClientMessage.UseSpell) {
        val charData =
            session.characterData
                ?: run {
                    session.send(ServerMessage.Notification("No character — use /createcharacter"))
                    return
                }
        val spell =
            spellRegistry[msg.spellId]
                ?: run {
                    log.warn("Unknown spellId '{}' from {}", msg.spellId, session.id.take(8))
                    session.send(ServerMessage.Notification("Unknown spell '${msg.spellId}'"))
                    return
                }

        val classDef = classRegistry[charData.characterClass.name]
        val unlockedSpells =
            classDef
                ?.levels
                ?.filter { (classLevel, _) -> classLevel <= charData.level }
                ?.values
                ?.flatMap { it.spells } ?: emptyList()
        if (classDef != null && unlockedSpells.isNotEmpty() && msg.spellId !in unlockedSpells) {
            session.send(ServerMessage.Notification("Your class cannot use spell '${msg.spellId}'"))
            return
        }

        val now = System.currentTimeMillis()
        val cdKey = "${session.id}:${msg.spellId}"
        val cdUntil = cooldowns[cdKey] ?: 0L
        if (now < cdUntil) {
            session.send(ServerMessage.Notification("Spell '${msg.spellId}' on cooldown"))
            return
        }

        val resource = charData.characterClass.classResource
        if (spell.manaCost > 0 &&
            resource == ClassResource.MANA &&
            charData.currentMana < spell.manaCost) {
            session.send(ServerMessage.Notification("Not enough mana"))
            return
        }
        if (spell.rageCost > 0 &&
            resource == ClassResource.RAGE &&
            charData.currentRage < spell.rageCost) {
            session.send(ServerMessage.Notification("Not enough rage"))
            return
        }

        var updated = charData
        if (spell.manaCost > 0 && resource == ClassResource.MANA)
            updated = updated.copy(currentMana = updated.currentMana - spell.manaCost)
        if (spell.rageCost > 0 && resource == ClassResource.RAGE)
            updated = updated.copy(currentRage = updated.currentRage - spell.rageCost)

        when (spell.type) {
            SpellType.TOKEN_RAGE_CONSUME -> {
                if (updated.currentTokens <= 0) {
                    session.send(ServerMessage.Notification("No rage tokens available"))
                    return
                }
                val newRage =
                    (updated.currentRage + spell.rageGain).coerceAtMost(combatConfig.maxRage)
                updated =
                    updated.copy(
                        currentTokens = updated.currentTokens - spell.tokenCost.coerceAtLeast(1),
                        currentRage = newRage,
                    )
            }
        }

        session.characterData = updated
        if (spell.cooldownMs > 0) cooldowns[cdKey] = now + spell.cooldownMs

        val armors = session.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
        val derived = DerivedStatsCalculator.compute(updated, armors)
        session.send(
            combatProcessor.makeStatusUpdate(
                updated,
                derived,
                session.state.stance,
                session.combatState.attackCooldownUntilMs,
                session.combatState.attackCooldownsUntilMs,
            ))
    }
}
