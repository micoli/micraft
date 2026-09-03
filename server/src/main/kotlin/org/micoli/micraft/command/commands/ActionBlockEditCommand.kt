package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.hasPermission
import org.micoli.micraft.game.world.actionblock.toInfo
import org.micoli.micraft.protocol.ServerMessage

/**
 * `/actionblock:edit <name> <field> <value...>` — sets one field on an existing action block.
 * `<field>` is `name`, `onActivate`, `onTargetEvent`, `onRemoteEvent`, or `var:<key>`. Owner /
 * claim-editor / `actionblock:edit` permission required.
 */
class ActionBlockEditCommand : CommandHandler {
    override val id: UUID = UUID.fromString("3c2f7c8b-5d4e-4f6a-8b2c-2d3e4f5a6b7c")
    override val name = "actionblock:edit"
    override val description = "Edit a field of a named action block."
    override val usage =
        "$command <name> <name|onActivate|onTargetEvent|onRemoteEvent|var:key> <value>"

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        when (argIndex) {
            0 ->
                context.actionBlockRegistry
                    ?.all()
                    ?.map { it.name }
                    ?.filter { it.contains(partial, ignoreCase = true) } ?: emptyList()
            1 ->
                listOf("name", "onActivate", "onTargetEvent", "onRemoteEvent", "var:").filter {
                    it.contains(partial, ignoreCase = true)
                }
            else -> emptyList()
        }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val registry = context.actionBlockRegistry
        if (registry == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:unavailable")))
            return
        }
        val trimmed = args.trim()
        val name = trimmed.substringBefore(' ').trim()
        val rest = trimmed.substringAfter(' ', "").trim()
        val field = rest.substringBefore(' ').trim()
        val value = rest.substringAfter(' ', "").trim()
        val block = registry.byName(name)
        if (name.isEmpty() || field.isEmpty() || block == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:usage")))
            return
        }
        if (!canEdit(session, block.owner)) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "actionblock:server:no_permission")))
            return
        }

        if (field.startsWith("var:")) {
            registry.setVariable(name, field.removePrefix("var:"), value)
            session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:saved", name)))
            return
        }

        val result =
            when (field) {
                "name" ->
                    registry.upsert(
                        block.pos,
                        value,
                        block.onActivate,
                        block.onTargetEvent,
                        block.onRemoteEvent,
                        block.variables)
                "onActivate" ->
                    registry.upsert(
                        block.pos,
                        block.name,
                        value,
                        block.onTargetEvent,
                        block.onRemoteEvent,
                        block.variables)
                "onTargetEvent" ->
                    registry.upsert(
                        block.pos,
                        block.name,
                        block.onActivate,
                        value,
                        block.onRemoteEvent,
                        block.variables)
                "onRemoteEvent" ->
                    registry.upsert(
                        block.pos,
                        block.name,
                        block.onActivate,
                        block.onTargetEvent,
                        value,
                        block.variables)
                else -> {
                    session.send(
                        ServerMessage.Notification(i18n.t(lang, "actionblock:server:usage")))
                    return
                }
            }
        when (result) {
            org.micoli.micraft.game.world.actionblock.ActionBlockRegistry.UpsertResult.OK -> {
                val updated = registry.at(block.pos) ?: return
                context.broadcast(ServerMessage.ActionBlockUpsert(updated.toInfo()))
                session.send(
                    ServerMessage.Notification(
                        i18n.t(lang, "actionblock:server:saved", updated.name)))
            }
            org.micoli.micraft.game.world.actionblock.ActionBlockRegistry.UpsertResult.NAME_TAKEN ->
                session.send(
                    ServerMessage.Notification(
                        i18n.t(lang, "actionblock:server:name_taken", value)))
            else ->
                session.send(ServerMessage.Notification(i18n.t(lang, "actionblock:server:usage")))
        }
    }

    private fun canEdit(session: PlayerSession, owner: String): Boolean =
        owner == session.state.name || session.hasPermission("actionblock:edit")
}
