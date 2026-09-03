package org.micoli.micraft.game.world.actionblock

import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.macro.MacroContext
import org.micoli.micraft.game.macro.MacroExecutor
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.hasPermission
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.claim.ClaimRegistry
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ActionBlockService::class.java)

/**
 * Per-world owner of action-block behaviour: the [registry], the [ActionBlockScriptEngine], and the
 * dispatch for the four entry points (activate, target, request-form, save-form). Built once per
 * [org.micoli.micraft.game.world.GameWorld].
 */
class ActionBlockService(
    val registry: ActionBlockRegistry,
    private val i18n: I18nConfig,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val runCommand: suspend (PlayerSession, String) -> Unit,
    private val blockAt: (BlockPos) -> BlockType,
    private val claimRegistry: ClaimRegistry? = null,
    macroExecutor: MacroExecutor = MacroExecutor(),
) {
    private val engine = ActionBlockScriptEngine(registry, macroExecutor)

    fun syncList(): List<ActionBlockInfo> = registry.all().map { it.toInfo() }

    suspend fun onActivate(session: PlayerSession, pos: BlockPos) {
        val block = registry.at(pos) ?: return
        runScript(session, block, block.onActivate, "onActivate")
    }

    suspend fun onTargetEvent(session: PlayerSession, pos: BlockPos?) {
        val block = pos?.let { registry.at(it) } ?: return
        runScript(session, block, block.onTargetEvent, "onTargetEvent")
    }

    suspend fun handleRequest(session: PlayerSession, pos: BlockPos) {
        if (!canEdit(session, pos)) {
            notify(session, i18n.t(session.state.language, "actionblock:server:no_permission"))
            return
        }
        val existing = registry.at(pos)
        val block =
            existing
                ?: registry.create(pos, session.state.name, blockAt)
                ?: return // air / nothing to name
        if (existing == null) broadcast(ServerMessage.ActionBlockUpsert(block.toInfo()))
        session.send(
            ServerMessage.ActionBlockPayload(
                pos = block.pos,
                name = block.name,
                onActivate = block.onActivate,
                onTargetEvent = block.onTargetEvent,
                onRemoteEvent = block.onRemoteEvent,
                variables = block.variables,
            ))
    }

    suspend fun handleSave(
        session: PlayerSession,
        pos: BlockPos,
        name: String,
        onActivate: String,
        onTargetEvent: String,
        onRemoteEvent: String,
        variables: Map<String, String>,
    ) {
        if (!canEdit(session, pos)) {
            notify(session, i18n.t(session.state.language, "actionblock:server:no_permission"))
            return
        }
        val result = registry.upsert(pos, name, onActivate, onTargetEvent, onRemoteEvent, variables)
        when (result) {
            ActionBlockRegistry.UpsertResult.OK -> {
                val block = registry.at(pos) ?: return
                broadcast(ServerMessage.ActionBlockUpsert(block.toInfo()))
                notify(
                    session, i18n.t(session.state.language, "actionblock:server:saved", block.name))
            }
            ActionBlockRegistry.UpsertResult.NAME_TAKEN ->
                session.send(
                    ServerMessage.ActionBlockPayload(
                        pos = pos,
                        name = name,
                        onActivate = onActivate,
                        onTargetEvent = onTargetEvent,
                        onRemoteEvent = onRemoteEvent,
                        variables = variables,
                        error =
                            i18n.t(session.state.language, "actionblock:server:name_taken", name),
                    ))
            else ->
                notify(
                    session, i18n.t(session.state.language, "actionblock:server:error", name, ""))
        }
    }

    suspend fun handleDelete(session: PlayerSession, pos: BlockPos) {
        if (!canEdit(session, pos)) {
            notify(session, i18n.t(session.state.language, "actionblock:server:no_permission"))
            return
        }
        val removed = registry.removeAt(pos) ?: return
        broadcast(ServerMessage.ActionBlockRemove(pos))
        notify(session, i18n.t(session.state.language, "actionblock:server:deleted", removed.name))
    }

    /** Drop registry entries whose block no longer exists (bulk edits), broadcasting removals. */
    suspend fun prune() {
        registry.pruneAgainst(blockAt).forEach { broadcast(ServerMessage.ActionBlockRemove(it)) }
    }

    private suspend fun runScript(
        session: PlayerSession,
        block: ActionBlock,
        script: String,
        label: String,
    ) {
        if (script.isBlank()) return
        val base =
            MacroContext(
                posX = session.state.pos.x,
                posY = session.state.pos.y,
                posZ = session.state.pos.z,
                biome = session.state.biome,
                yaw = session.state.orientation.yaw,
                pitch = session.state.orientation.pitch,
                currentHp = session.characterData?.currentHp ?: 0,
                currentMana = session.characterData?.currentMana ?: 0,
                playerName = session.state.name,
                playerId = session.id,
            )
        val result =
            runCatching { engine.run(block, script, base) }
                .getOrElse {
                    log.warn("action-block {} '{}' crashed: {}", label, block.name, it.message)
                    ActionBlockScriptEngine.Result(emptyList(), emptyList(), it.message ?: "error")
                }
        result.notifications.forEach { notify(session, it) }
        for (cmd in result.commands) {
            runCatching { runCommand(session, cmd) }
                .onFailure { log.warn("action-block cmd '{}' failed: {}", cmd, it.message) }
        }
        result.error?.let { err ->
            notify(
                session,
                i18n.t(session.state.language, "actionblock:server:error", block.name, err))
        }
    }

    private fun canEdit(session: PlayerSession, pos: BlockPos): Boolean {
        if (session.hasPermission("actionblock:edit")) return true
        registry.at(pos)?.let { if (it.owner == session.state.name) return true }
        val claim = claimRegistry?.claimAt(pos.x, pos.y, pos.z) ?: return true
        return claimRegistry.canEdit(claim, session)
    }

    private suspend fun notify(session: PlayerSession, message: String) {
        session.send(ServerMessage.Notification(message))
    }
}
