package org.micoli.micraft.plugins.npc

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.WorldConstants
import java.util.UUID

class NpcCommand : CommandHandler {
    override val id = UUID.fromString("432ab72b-0cb2-4609-a75d-2093798d5869")
    override val command = "/npc"
    override val description = "Manage NPCs in the world."
    override val usage = "/npc <spawn|list|remove|tp> [args]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val npcManager = context.npcManager
        if (npcManager == null) {
            session.send(ServerMessage.Notification("NPC system not available."))
            return
        }

        val sub = args.substringBefore(' ').lowercase()
        val rest = args.substringAfter(' ', "").trim()

        when (sub) {
            "spawn" -> handleSpawn(session, rest, context, lang, i18n, npcManager)
            "list"  -> handleList(session, context, lang, i18n, npcManager)
            "remove", "rm" -> handleRemove(session, rest, context, lang, i18n, npcManager)
            "tp"    -> handleTp(session, rest, context, lang, i18n, npcManager)
            else    -> session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:usage")))
        }
    }

    private suspend fun handleSpawn(
        session: PlayerSession, args: String,
        context: CommandContext, lang: String, i18n: org.micoli.micraft.world.I18nConfig,
        npcManager: org.micoli.micraft.npc.NpcManager,
    ) {
        val parts = args.split(" ", limit = 2)
        if (parts.isEmpty() || parts[0].isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:usage")))
            return
        }
        val type = parts[0].uppercase()
        val name = if (parts.size >= 2) parts[1].trim() else type
        val defs = npcManager.getDefinitions()
        if (type !in defs) {
            session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:unknown_type", type, defs.keys.joinToString(", "))))
            return
        }
        val pos = session.state.pos
        npcManager.spawnNpc(name, type, pos)
        session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:spawned", name, type, "(${pos.x.toInt()},${pos.y.toInt()},${pos.z.toInt()})")))
    }

    private suspend fun handleList(
        session: PlayerSession,
        @Suppress("UNUSED_PARAMETER") context: CommandContext, lang: String, i18n: org.micoli.micraft.world.I18nConfig,
        npcManager: org.micoli.micraft.npc.NpcManager,
    ) {
        val all = npcManager.getAll()
        session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:listed", all.size)))
        for (instance in all) {
            val s = instance.state
            val pos = "(${s.pos.x.toInt()},${s.pos.y.toInt()},${s.pos.z.toInt()})"
            session.send(ServerMessage.Notification("  ${s.id.take(8)} | ${s.name} | ${s.type} | $pos"))
        }
    }

    private suspend fun handleRemove(
        session: PlayerSession, args: String,
        @Suppress("UNUSED_PARAMETER") context: CommandContext, lang: String, i18n: org.micoli.micraft.world.I18nConfig,
        npcManager: org.micoli.micraft.npc.NpcManager,
    ) {
        if (args.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:usage")))
            return
        }
        val instance = npcManager.findByNameOrId(args)
        if (instance == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:not_found", args)))
            return
        }
        npcManager.despawnNpc(instance.state.id)
        session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:removed", instance.state.name)))
    }

    private suspend fun handleTp(
        session: PlayerSession, args: String,
        @Suppress("UNUSED_PARAMETER") context: CommandContext, lang: String, i18n: org.micoli.micraft.world.I18nConfig,
        npcManager: org.micoli.micraft.npc.NpcManager,
    ) {
        if (args.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:usage")))
            return
        }
        val instance = npcManager.findByNameOrId(args)
        if (instance == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:not_found", args)))
            return
        }
        val playerPos = session.state.pos
        instance.state = instance.state.copy(pos = playerPos)
        instance.vy = 0f
        context.broadcast(ServerMessage.NpcUpdate(instance.state))
        session.send(ServerMessage.Notification(i18n.t(lang, "npc:server:tp_done", instance.state.name)))
    }
}
