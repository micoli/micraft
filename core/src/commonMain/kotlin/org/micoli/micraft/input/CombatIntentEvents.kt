package org.micoli.micraft.input

import org.micoli.micraft.protocol.ClientMessage

/**
 * Combat-intent payloads (attack/spell casts) — grouped so `LocalPlayerController` can dispatch
 * them in one branch.
 */
sealed interface CombatIntentEvent

data class Attack(val attackId: String, val rank: Int) : ClientInputEvent(), CombatIntentEvent

data class Spell(val spellId: String, val rank: Int) : ClientInputEvent(), CombatIntentEvent

/** `"<attackId>[:<rank>]"` — rank defaults to 1 when omitted. */
internal fun parseAttack(payload: String): ClientInputEvent {
    val lastColon = payload.lastIndexOf(':')
    val attackId = if (lastColon > 0) payload.substring(0, lastColon) else payload
    val rank = if (lastColon > 0) payload.substring(lastColon + 1).toIntOrNull() ?: 1 else 1
    return Attack(attackId, rank)
}

/** `"<spellId>[:<rank>]"` — rank defaults to 1 when omitted. */
internal fun parseSpell(payload: String): ClientInputEvent {
    val lastColon = payload.lastIndexOf(':')
    val spellId = if (lastColon > 0) payload.substring(0, lastColon) else payload
    val rank = if (lastColon > 0) payload.substring(lastColon + 1).toIntOrNull() ?: 1 else 1
    return Spell(spellId, rank)
}

class CombatIntentEventHandler(private val ctx: ClientEventContext) {
    fun handle(event: CombatIntentEvent) {
        when (event) {
            is Attack -> {
                val targetId = ctx.currentCombatTargetId() ?: return
                ctx.outMessages.trySend(
                    ClientMessage.AttackTarget(
                        targetId = targetId,
                        isNpc = true,
                        attackId = event.attackId,
                        attackRank = event.rank))
            }
            is Spell ->
                ctx.outMessages.trySend(
                    ClientMessage.UseSpell(spellId = event.spellId, spellRank = event.rank))
        }
    }
}
