@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.game

import kotlin.js.JsAny
import kotlinx.serialization.json.Json
import org.micoli.micraft.ChunkManager
import org.micoli.micraft.HttpChunkFetcher
import org.micoli.micraft.LocalPlayerController
import org.micoli.micraft.babylon.jsCameraSetFov
import org.micoli.micraft.babylon.jsSetContinuousBreak
import org.micoli.micraft.babylon.jsSetShadowAngleDeg
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.ui.McUiState

// Compiled-in client defaults, restored when a graphics-preference override is cleared.
private const val DEFAULT_VIEW_RADIUS = 3
private const val DEFAULT_FORWARD_VIEW_RADIUS = 7
private const val DEFAULT_USE_IMPOSTOR = true
private const val DEFAULT_IMPOSTOR_RADIUS_CHUNKS = 5
private const val DEFAULT_IMPOSTOR_FOV_BONUS_CHUNKS = 2

/** Applies graphics/gameplay preference overrides pushed from the server preferences dialog. */
class PreferencesHandler(
    private val camera: JsAny,
    private val chunkManager: ChunkManager,
    private val localController: LocalPlayerController,
    private val uiState: McUiState,
    private val httpChunkFetcher: () -> HttpChunkFetcher?,
    private val currentPlayerPos: () -> Triple<Int, Int, Float>,
) : ServerMessageHandler {
    override fun handle(msg: ServerMessage) {
        if (msg !is ServerMessage.PreferencesSync) return
        jsCameraSetFov(camera, msg.fieldOfView)
        localController.autoTargetEnabled = msg.autoTargetEnabled
        localController.continuousBreak = msg.continuousBreak
        localController.disabledViewModes = msg.disabledViewModes
        localController.turnSpeedHorizontal = msg.turnSpeedHorizontal
        localController.turnSpeedVertical = msg.turnSpeedVertical
        jsSetContinuousBreak(msg.continuousBreak)
        jsSetShadowAngleDeg(msg.shadowAngleDeg)
        WorldConstants.VIEW_RADIUS = msg.overrideViewRadius ?: DEFAULT_VIEW_RADIUS
        WorldConstants.FORWARD_VIEW_RADIUS =
            msg.overrideForwardViewRadius ?: DEFAULT_FORWARD_VIEW_RADIUS
        chunkManager.useImpostor = msg.overrideUseImpostor ?: DEFAULT_USE_IMPOSTOR
        chunkManager.impostorRadiusChunks =
            msg.overrideImpostorRadiusChunks ?: DEFAULT_IMPOSTOR_RADIUS_CHUNKS
        chunkManager.impostorFovBonusChunks =
            msg.overrideImpostorFovBonusChunks ?: DEFAULT_IMPOSTOR_FOV_BONUS_CHUNKS
        // Apply the new radii/impostor settings right away instead of waiting for the player to
        // cross a chunk boundary.
        val (cx, cz, yaw) = currentPlayerPos()
        chunkManager.unloadDistantChunks(cx, cz)
        chunkManager.reevaluateImpostors(cx, cz, yaw.toDouble())
        httpChunkFetcher()?.trigger(cx, cz, yaw)
        uiState.setPreferencesSync(Json.encodeToString<ServerMessage.PreferencesSync>(msg))
    }
}
