package org.micoli.micraft.game.combat

import kotlin.math.sqrt
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.classes.ClassDefinitionEntry
import org.micoli.micraft.game.npc.NpcInstance
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.rpg.ClassResource
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SpellProcessor")

class SpellProcessor(
    @Volatile private var spellRegistry: Map<String, SpellDefinition>,
    @Volatile private var classRegistry: Map<String, ClassDefinitionEntry>,
    @Volatile private var armorRegistry: Map<String, ArmorDefinition>,
    @Volatile private var combatConfig: CombatConfigData,
    private val combatProcessor: CombatProcessor,
    private val getSessions: () -> Collection<PlayerSession> = { emptyList() },
    private val getNpcs: () -> Collection<NpcInstance> = { emptyList() },
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
            SpellType.NECROTIC_AOE -> {}
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

    suspend fun handleCastAoeSpell(session: PlayerSession, msg: ClientMessage.CastAoeSpell) {
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

        val pos = session.state.pos
        val dx = msg.targetX - pos.x
        val dy = msg.targetY - pos.y
        val dz = msg.targetZ - pos.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > spell.maxRange) {
            session.send(
                ServerMessage.Notification("Target out of range (max ${spell.maxRange.toInt()} m)"))
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
            SpellType.NECROTIC_AOE -> {
                val radiusSq = spell.aoeRadius * spell.aoeRadius
                val effect = StatusEffect.Withering
                val durationSec = effect.durationSec
                val hitPlayers = mutableListOf<String>()
                val hitNpcs = mutableListOf<String>()

                for (target in getSessions()) {
                    if (target.characterData == null) continue
                    val tp = target.state.pos
                    val ex = msg.targetX - tp.x
                    val ey = msg.targetY - tp.y
                    val ez = msg.targetZ - tp.z
                    if (ex * ex + ey * ey + ez * ez <= radiusSq) {
                        combatProcessor.applyStatusEffectTo(target, effect, durationSec, now)
                        hitPlayers += target.state.name
                    }
                }

                for (npc in getNpcs()) {
                    if (npc.isDead) continue
                    val np = npc.state.pos
                    val ex = msg.targetX - np.x
                    val ey = msg.targetY - np.y
                    val ez = msg.targetZ - np.z
                    if (ex * ex + ey * ey + ez * ez <= radiusSq) {
                        npc.activeEffects.removeAll { it.effect::class == effect::class }
                        npc.activeEffects.add(
                            org.micoli.micraft.combat.ActiveStatusEffect(
                                effect, now + (durationSec * 1000).toLong()))
                        hitNpcs += "${npc.state.id.take(8)}(${npc.state.name})"
                    }
                }

                log.debug("AoE hit players={} npcs={}", hitPlayers, hitNpcs)
                val aoeMsg =
                    ServerMessage.AoEEffect(msg.targetX, msg.targetY, msg.targetZ, spell.aoeRadius)
                for (s in getSessions()) s.send(aoeMsg)
            }
            SpellType.TOKEN_RAGE_CONSUME -> {}
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

    fun reload(
        spellRegistry: Map<String, SpellDefinition>,
        classRegistry: Map<String, ClassDefinitionEntry>,
        armorRegistry: Map<String, ArmorDefinition>,
        combatConfig: CombatConfigData,
    ) {
        this.spellRegistry = spellRegistry
        this.classRegistry = classRegistry
        this.armorRegistry = armorRegistry
        this.combatConfig = combatConfig
    }
}
