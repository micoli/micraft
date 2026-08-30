package org.micoli.micraft.command.commands

import java.util.UUID
import kotlin.math.min
import kotlin.random.Random
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.npc.AggroMode
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.pet.PetManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

/**
 * `/tame` — attempt to tame the currently targeted wild NPC. Success is chance-based: better with a
 * wider level gap in the player's favour and a lower-HP target, worse against aggressive mobs. A
 * failed attempt on an aggressive mob turns it on the player.
 */
class TameCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b7e4c1a2-3d5f-4a6b-8c9d-0e1f2a3b4c5d")
    override val name = "tame"
    override val description = "Attempt to tame the wild creature you are targeting."

    /** Roll in [0,1); the attempt succeeds when it lands under the computed chance. Test seam. */
    internal var roll: () -> Float = { Random.nextFloat() }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val npcManager = context.npcManager ?: return
        val petManager = context.petManager ?: return

        fun notify(key: String, vararg a: Any) = ServerMessage.Notification(i18n.t(lang, key, *a))

        val targetId = session.combatState.targetId?.takeIf { session.combatState.targetIsNpc }
        val npc = targetId?.let { npcManager.getInstance(it) }
        if (npc == null || npc.isDead) {
            session.send(notify("tame:server:no_target"))
            return
        }
        if (npc.ownerId != null) {
            session.send(notify("tame:server:already_tamed"))
            return
        }
        if (!npc.definition.tameable) {
            session.send(notify("tame:server:not_tameable", npc.state.name))
            return
        }
        val playerLevel = session.characterData?.level ?: 1
        if (npc.instanceLevel > playerLevel) {
            session.send(notify("tame:server:too_high_level", npc.instanceLevel, playerLevel))
            return
        }
        if (session.state.pets.size >= PetManager.PET_ROSTER_CAP) {
            session.send(notify("tame:server:roster_full"))
            return
        }
        val dx = npc.state.pos.x - session.state.pos.x
        val dy = npc.state.pos.y - session.state.pos.y
        val dz = npc.state.pos.z - session.state.pos.z
        val range = NpcConstants.live.interactionRange
        if (dx * dx + dy * dy + dz * dz > range * range) {
            session.send(notify("tame:server:out_of_range"))
            return
        }

        val hpFraction = if (npc.maxHp > 0) npc.currentHp.toFloat() / npc.maxHp else 1f
        val base = npc.definition.tameBaseChance
        val levelBonus = min(0.30f, (playerLevel - npc.instanceLevel) * 0.04f)
        val hpBonus = (1f - hpFraction) * 0.5f
        val aggroPenalty = if (npc.definition.aggroMode == AggroMode.AGGRESSIVE) 0.15f else 0f
        val chance = (base + levelBonus + hpBonus - aggroPenalty).coerceIn(0.05f, 0.95f)

        if (roll() >= chance) {
            session.send(notify("tame:server:failed", npc.state.name))
            if (npc.definition.aggroMode == AggroMode.AGGRESSIVE) {
                npcManager.aggroOnto(npc.state.id, session.id)
                session.send(notify("tame:server:failed_hostile", npc.state.name))
            }
            return
        }

        val record =
            petManager.addTamed(
                session = session,
                npcType = npc.state.type,
                name = npc.state.name,
                level = npc.instanceLevel,
                xp = npc.xp,
            )
        npcManager.despawnNpc(npc.state.id)
        session.combatState = session.combatState.copy(targetId = null)
        session.send(ServerMessage.CombatTargetUpdate(null, null, 0, 0))
        session.send(notify("tame:server:success", record.name))
        context.broadcast(
            ServerMessage.ChatMessage(
                channel = "combat",
                sender = "",
                message = "[p:${session.state.name}] tamed [m:${record.name}]!"))

        if (session.state.activePetId == null) petManager.summon(session, record.name)
    }
}
