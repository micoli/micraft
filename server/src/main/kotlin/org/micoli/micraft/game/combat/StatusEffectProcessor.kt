package org.micoli.micraft.game.combat

import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("StatusEffectProcessor")

class StatusEffectProcessor(
    private val armorRegistry: Map<String, ArmorDefinition>,
    private val world: WorldState,
    private val broadcastHealthUpdate: suspend (String, Boolean, Int, Int) -> Unit,
    private val broadcastCombatLog: suspend (String) -> Unit,
    private val subscribeToChannel: suspend (PlayerSession, String) -> Unit,
    private val onPlayerDowned: suspend (PlayerSession) -> Unit = {},
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var lastTickMs = nowMs()
    private val pendingDotDamage = mutableMapOf<String, Float>()

    suspend fun tick(sessions: Collection<PlayerSession>) {
        val now = nowMs()
        val dtSec = (now - lastTickMs) / 1000f
        lastTickMs = now

        for (session in sessions) {
            val charData = session.characterData ?: continue
            val effects = session.combatState.activeEffects
            if (effects.isEmpty()) continue

            val expired = effects.filter { it.expiresAtMs <= now }
            var changed = expired.isNotEmpty()
            effects.removeAll(expired.toSet())

            var hpDelta = 0f

            for (active in effects) {
                when (active.effect) {
                    is StatusEffect.Poisoned -> hpDelta -= active.effect.damage * dtSec
                    is StatusEffect.Burning -> {
                        val pos = session.state.pos
                        if (world.getBlockBelow(pos) == BlockType.WATER) {
                            effects.remove(active)
                            changed = true
                        } else {
                            hpDelta -= active.effect.damage * dtSec
                        }
                    }
                    is StatusEffect.Pyre -> hpDelta -= active.effect.damage * dtSec
                    is StatusEffect.Withering -> hpDelta -= active.effect.damage * dtSec
                    else -> {}
                }
            }

            if (changed) {
                session.send(ServerMessage.StatusEffectUpdate(session.id, effects.toList()))
            }

            if (!isDamageApplicable(hpDelta, session)) {
                continue
            }
            val activeEffectNames = effects.map { it.effect::class.simpleName ?: "" }.toSet()
            val derived = session.computeDerived(armorRegistry, charData, activeEffectNames)
            val pending = (pendingDotDamage[session.id] ?: 0f) - hpDelta
            val intDamage = pending.toInt()
            pendingDotDamage[session.id] = pending - intDamage
            val newHp = (charData.currentHp - intDamage).coerceIn(0, derived.maxHp)
            session.characterData = charData.copy(currentHp = newHp)
            broadcastHealthUpdate(session.id, false, newHp, derived.maxHp)
            if (newHp <= 0 && !session.isDowned) onPlayerDowned(session)
            if (intDamage <= 0) {
                continue
            }
            val effectNames =
                effects.mapNotNull { it.effect.damageEffectName }.distinct().joinToString("+")
            subscribeToChannel(session, "combat")
            broadcastCombatLog("${charData.name} takes $intDamage damage from $effectNames")
        }
    }

    private fun isDamageApplicable(hpDelta: Float, session: PlayerSession): Boolean =
        hpDelta != 0f && !(hpDelta < 0 && session.state.godMode)
}
