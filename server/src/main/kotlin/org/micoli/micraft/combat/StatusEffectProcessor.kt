package org.micoli.micraft.combat

import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.rpg.character.DerivedStatsCalculator
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.ArmorDefinition
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.WorldState
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("StatusEffectProcessor")

class StatusEffectProcessor(
    private val armorRegistry: Map<String, ArmorDefinition>,
    private val world: WorldState,
    private val broadcastHealthUpdate: suspend (String, Boolean, Int, Int) -> Unit,
    private val broadcastCombatLog: suspend (String) -> Unit,
    private val subscribeToChannel: suspend (PlayerSession, String) -> Unit,
) {
    private var lastTickMs = System.currentTimeMillis()

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
                    else -> {}
                }
            }

            if (changed) {
                session.send(ServerMessage.StatusEffectUpdate(session.id, effects.toList()))
            }

            if (hpDelta != 0f) {
                val armors = session.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
                val derived = DerivedStatsCalculator.compute(charData, armors)
                val newHp = (charData.currentHp + hpDelta.toInt()).coerceIn(0, derived.maxHp)
                session.characterData = charData.copy(currentHp = newHp)
                broadcastHealthUpdate(session.id, false, newHp, derived.maxHp)
                val effectNames = effects.mapNotNull {
                    when (it.effect) {
                        is StatusEffect.Poisoned -> "poison"
                        is StatusEffect.Burning -> "burn"
                        else -> null
                    }
                }.distinct().joinToString("+")
                val dmg = -hpDelta.toInt()
                if (dmg > 0) {
                    subscribeToChannel(session, "combat")
                    broadcastCombatLog("${charData.name} takes $dmg damage from $effectNames")
                }
            }
        }
    }
}
