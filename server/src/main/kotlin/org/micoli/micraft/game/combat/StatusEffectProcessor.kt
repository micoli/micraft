package org.micoli.micraft.game.combat

import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
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
) {
    private var lastTickMs = System.currentTimeMillis()
    private val pendingDotDamage = mutableMapOf<String, Float>()

    suspend fun tick(sessions: Collection<PlayerSession>) {
        val now = System.currentTimeMillis()
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
                    is StatusEffect.Poisoned -> hpDelta -= 2f * dtSec
                    is StatusEffect.Burning -> {
                        val pos = session.state.pos
                        val blockBelow =
                            world.getBlock(pos.x.toInt(), (pos.y - 0.1f).toInt(), pos.z.toInt())
                        if (blockBelow == BlockType.WATER) {
                            effects.remove(active)
                            changed = true
                        } else {
                            hpDelta -= 3f * dtSec
                        }
                    }
                    is StatusEffect.Pyre -> hpDelta -= 4f * dtSec
                    is StatusEffect.Withering -> hpDelta -= 3f * dtSec
                    else -> {}
                }
            }

            if (changed) {
                session.send(ServerMessage.StatusEffectUpdate(session.id, effects.toList()))
            }

            if (hpDelta != 0f && !(hpDelta < 0 && session.state.godMode)) {
                val armors = session.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
                val activeEffectNames = effects.map { it.effect::class.simpleName ?: "" }.toSet()
                val derived = DerivedStatsCalculator.compute(charData, armors, activeEffectNames)
                val pending = (pendingDotDamage[session.id] ?: 0f) - hpDelta
                val intDamage = pending.toInt()
                pendingDotDamage[session.id] = pending - intDamage
                val newHp = (charData.currentHp - intDamage).coerceIn(0, derived.maxHp)
                session.characterData = charData.copy(currentHp = newHp)
                broadcastHealthUpdate(session.id, false, newHp, derived.maxHp)
                if (newHp <= 0 && !session.isDowned) onPlayerDowned(session)
                val effectNames =
                    effects
                        .mapNotNull {
                            when (it.effect) {
                                is StatusEffect.Poisoned -> "poison"
                                is StatusEffect.Burning -> "burn"
                                is StatusEffect.Pyre -> "pyre"
                                is StatusEffect.Withering -> "wither"
                                else -> null
                            }
                        }
                        .distinct()
                        .joinToString("+")
                if (intDamage > 0) {
                    subscribeToChannel(session, "combat")
                    broadcastCombatLog("${charData.name} takes $intDamage damage from $effectNames")
                }
            }
        }
    }
}
