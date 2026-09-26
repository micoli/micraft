package org.micoli.micraft.game.combat

import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.rpg.CharacterStats
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage

class StatusEffectProcessor(
    private val world: WorldState,
    private val broadcastHealthUpdate: suspend (String, Boolean, Int, Int) -> Unit,
    private val broadcastCombatLog: suspend (String) -> Unit,
    private val subscribeToChannel: suspend (PlayerSession, String) -> Unit,
    private val onPlayerDowned: suspend (PlayerSession) -> Unit = {},
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val characterStats: CharacterStats = CharacterStats(),
) {
    private var lastTickMs = nowMs()
    private val pendingDotDamage = mutableMapOf<String, Float>()

    suspend fun tick(sessions: Collection<PlayerSession>) {
        val now = nowMs()
        val dtSec = (now - lastTickMs) / 1000f
        lastTickMs = now

        for (session in sessions) {
            if (session.characterData == null) continue
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
                    is StatusEffect.Drowning -> hpDelta -= active.effect.damage * dtSec
                    else -> {}
                }
            }

            if (changed) {
                session.send(ServerMessage.StatusEffectUpdate(session.id, effects.toList()))
            }
            if (expired.any { characterStats.affectsStats(it.effect) }) {
                characterStats.resync(session)
            }

            if (!isDamageApplicable(hpDelta, session)) {
                continue
            }
            val current = session.characterData ?: continue
            val derived = characterStats.derived(session, current)
            val pending = (pendingDotDamage[session.id] ?: 0f) - hpDelta
            val intDamage = pending.toInt()
            pendingDotDamage[session.id] = pending - intDamage
            val newHp = (current.currentHp - intDamage).coerceIn(0, derived.maxHp)
            session.characterData = current.copy(currentHp = newHp)
            broadcastHealthUpdate(session.id, false, newHp, derived.maxHp)
            if (newHp <= 0 && !session.isDowned) onPlayerDowned(session)
            if (intDamage <= 0) {
                continue
            }
            val effectNames =
                effects.mapNotNull { it.effect.damageEffectName }.distinct().joinToString("+")
            subscribeToChannel(session, "combat")
            broadcastCombatLog("${current.name} takes $intDamage damage from $effectNames")
        }
    }

    private fun isDamageApplicable(hpDelta: Float, session: PlayerSession): Boolean =
        hpDelta != 0f && !(hpDelta < 0 && session.state.godMode)
}
