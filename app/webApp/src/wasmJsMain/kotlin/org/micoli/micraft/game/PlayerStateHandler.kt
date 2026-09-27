package org.micoli.micraft.game

import kotlinx.serialization.json.Json
import org.micoli.micraft.ChunkManager
import org.micoli.micraft.HttpChunkFetcher
import org.micoli.micraft.LocalPlayerController
import org.micoli.micraft.RemotePlayerManager
import org.micoli.micraft.babylon.jsCombatTargetUpdate
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.ServerMessage

/**
 * Player movement/prediction sync driving [localController] — position reconciliation, mount,
 * shortcut bar, game clock and combat target.
 */
class PlayerStateHandler(
    private val localController: LocalPlayerController,
    private val remotePlayerManager: RemotePlayerManager,
    private val chunkManager: ChunkManager,
    private val localPlayerId: () -> String?,
    private val httpChunkFetcher: () -> HttpChunkFetcher?,
    private val onPlayerPositionChanged: (cx: Int, cz: Int, yaw: Float) -> Unit,
) : ServerMessageHandler {
    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.PlayerUpdate -> {
                val s = msg.state
                if (s.id == localPlayerId()) {
                    val cx = s.pos.x.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
                    val cz = s.pos.z.toInt().floorDiv(WorldConstants.CHUNK_SIZE)
                    val yaw = s.orientation.yaw
                    onPlayerPositionChanged(cx, cz, yaw)
                    httpChunkFetcher()?.trigger(cx, cz, yaw)
                    localController.updateFromServer(s, msg.lastProcessedSeq) { ucx, ucz ->
                        chunkManager.unloadDistantChunks(ucx, ucz)
                        chunkManager.reevaluateImpostors(ucx, ucz, yaw.toDouble())
                    }
                } else {
                    remotePlayerManager.updateFromServer(s)
                }
            }
            is ServerMessage.PlayerLeft -> remotePlayerManager.remove(msg.playerId)
            is ServerMessage.GameConfigSync -> {
                localController.setReconcileTolerances(
                    msg.reconcileToleranceXz, msg.reconcileToleranceY)
                localController.maxInteractionDistance = msg.maxInteractionDistance.toFloat()
                localController.kinematicTuning = msg.kinematics
            }
            is ServerMessage.ShortcutBarUpdate -> {
                for (page in 0..9) for (i in 0..9) localController.shortcutBarPages[page][i] = null
                msg.pages.forEach { (page, slots) ->
                    if (page in 0..9)
                        slots.forEach { (i, item) ->
                            if (i in 0..9) localController.shortcutBarPages[page][i] = item
                        }
                }
                localController.syncShortcutBarToUi()
            }
            is ServerMessage.TimeUpdate -> localController.currentGameTicks = msg.gameTicks
            is ServerMessage.CombatTargetUpdate -> {
                localController.currentCombatTargetId = msg.targetId
                jsCombatTargetUpdate(Json.encodeToString(msg))
            }
            is ServerMessage.MountUpdate -> {
                localController.isMounted = msg.vehicleId != null
                localController.mountedVehicleId = msg.vehicleId
            }
            else -> Unit
        }
}
