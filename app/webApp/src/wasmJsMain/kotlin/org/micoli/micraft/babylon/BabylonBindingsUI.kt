@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Build info ────────────────────────────────────────────────────────────────

fun jsSetServerBuildTimestamp(timestamp: String): Unit = js("window.mcBuildInfo.server = timestamp")

// ── Loading overlay ────────────────────────────────────────────────────────────

fun jsUpdateChunkLoading(meshed: Int, downloaded: Int, total: Int): Unit =
    js("mc.updateChunkLoading(meshed, downloaded, total)")

fun jsHideChunkLoading(): Unit = js("mc.hideChunkLoading()")

// ── Disconnect overlay ────────────────────────────────────────────────────────

fun jsShowDisconnectedOverlay(message: String): Unit = js("mc.showDisconnectedOverlay(message)")

fun jsHideDisconnectedOverlay(): Unit = js("mc.hideDisconnectedOverlay()")

// ── Login overlay ─────────────────────────────────────────────────────────────

fun jsShowLoginOverlay(): Unit = js("mc.showLoginOverlay()")

fun jsHideLoginOverlay(): Unit = js("mc.hideLoginOverlay()")

fun jsConsumeLoginResult(): String = js("mc.consumeLoginResult()")

fun jsClearStoredToken(): Unit = js("mc.clearStoredToken()")

// ── Console / Server log ──────────────────────────────────────────────────────

fun jsCreateConsole(): Unit = js("mc.createConsole()")

fun jsCreateServerLog(): Unit = js("mc.createServerLog()")

fun jsConsoleSetPlayer(name: String): Unit = js("mc.consoleSetPlayer(name)")

fun jsSetPlayerId(id: String): Unit = js("mc.setPlayerId(id)")

fun jsIsConsoleOpen(): Boolean = js("mc.isConsoleOpen()")

fun jsIsConsoleInputFocused(): Boolean = js("mc.isConsoleInputFocused()")

fun jsConsumeConsoleInput(): String = js("mc.consumeConsoleInput()")

fun jsShowNotification(message: String): Unit = js("mc.showNotification(message)")

// ── Hotbar / ShortcutBar ──────────────────────────────────────────────────────

fun jsCreateHotbar(): Unit = js("mc.createHotbar()")

fun jsUpdateHotbar(inventoryJson: String): Unit = js("mc.updateHotbar(inventoryJson)")

fun jsToggleHotbar(): Unit =
    js("(mc.toggleHotbar(), document.pointerLockElement && document.exitPointerLock())")

fun jsUpdateShortcutBar(json: String): Unit = js("mc.updateShortcutBar(json)")

fun jsSetSelectedSlot(slot: Int): Unit = js("mc.setSelectedSlot(slot)")

fun jsConsumeSlotUpdate(): String = js("mc.consumeSlotUpdate()")

// ── HUD ───────────────────────────────────────────────────────────────────────

fun jsCreateHUD(): Unit = js("mc.createHUD()")

fun jsUpdateHUD(
    x: Double,
    y: Double,
    z: Double,
    yaw: Double,
    pitch: Double,
    stance: String,
    speed: Double,
    fps: Int,
    fpsMin: Int,
    fpsMax: Int,
    kbIn: Double,
    kbOut: Double,
    biome: String,
    targetBlock: String,
    gameTime: String,
    reconcileXzStats: String,
    reconcileYStats: String,
    tickDtMs: Double,
    tickJitterMs: Double,
    tickDtMinMs: Double,
    tickDtMaxMs: Double,
    tickJitterMinMs: Double,
    tickJitterMaxMs: Double,
    chunkDownloading: Int,
    chunkMeshing: Int,
    fullMeshedChunks: Int,
    impostorMeshedChunks: Int,
    weather: String,
    zoneLevel: Int,
    meshDrainMsAvg: Double,
    meshDrainMsMin: Double,
    meshDrainMsMax: Double,
    gpuUploadMsAvg: Double,
    gpuUploadMsMin: Double,
    gpuUploadMsMax: Double,
    wsDecodeMsAvg: Double,
): Unit =
    js(
        "mc.updateHUD(x, y, z, yaw, pitch, stance, speed, fps, fpsMin, fpsMax, kbIn, kbOut, biome, targetBlock, gameTime, reconcileXzStats, reconcileYStats, tickDtMs, tickJitterMs, tickDtMinMs, tickDtMaxMs, tickJitterMinMs, tickJitterMaxMs, chunkDownloading, chunkMeshing, fullMeshedChunks, impostorMeshedChunks, weather, zoneLevel, meshDrainMsAvg, meshDrainMsMin, meshDrainMsMax, gpuUploadMsAvg, gpuUploadMsMin, gpuUploadMsMax, wsDecodeMsAvg)")

// Logs a captured frame-spike ring-buffer snapshot to the browser console — only invoked when
// a frame exceeds the spike threshold (see jsGetSpikeThresholdMs below), so its cost is
// irrelevant.
fun jsLogSpike(json: String): Unit = js("console.warn('[fps-spike]', JSON.parse(json))")

// Spike threshold, live-tunable from the browser console via
// `window.mcState.spikeThresholdMs = 200` — falls back to the DEFAULT_SPIKE_THRESHOLD_MS
// constant (see LocalPlayerController) when unset. Read fresh every HUD tick-block, so changes
// take effect immediately without a reload.
fun jsGetSpikeThresholdMs(defaultMs: Double): Double =
    js("window.mcState.spikeThresholdMs ?? defaultMs")

// ── Layout ────────────────────────────────────────────────────────────────────

fun jsSyncLayouts(json: String): Unit = js("mc.syncLayouts(json)")

fun jsShowLayoutEditor(): Unit = js("mc.showLayoutEditor()")

fun jsConsumeLayoutUpdate(): String = js("mc.consumeLayoutUpdate()")

// ── Minimap ───────────────────────────────────────────────────────────────────

fun jsCreateMinimap(): Unit = js("mc.createMinimap()")

fun jsSetMinimapChunk(cx: Int, cz: Int, topYJson: String, topBlockJson: String): Unit =
    js("mc.setMinimapChunk(cx, cz, topYJson, topBlockJson)")

fun jsClearMinimapChunk(cx: Int, cz: Int): Unit = js("mc.clearMinimapChunk(cx, cz)")

fun jsDrawMinimap(playerX: Double, playerZ: Double, playerYaw: Double): Unit =
    js("mc.drawMinimap(playerX, playerZ, playerYaw)")

fun jsSetNpcOnMinimap(id: String, x: Float, z: Float): Unit = js("mc.setNpcOnMinimap(id, x, z)")

fun jsRemoveNpcFromMinimap(id: String): Unit = js("mc.removeNpcFromMinimap(id)")

fun jsSetPlayerOnMinimap(id: String, x: Float, z: Float, yaw: Float): Unit =
    js("mc.setPlayerOnMinimap(id, x, z, yaw)")

fun jsRemovePlayerFromMinimap(id: String): Unit = js("mc.removePlayerFromMinimap(id)")

// ── Chunk debug ───────────────────────────────────────────────────────────────

fun jsUpdateChunkDebug(json: String): Unit = js("mc.updateChunkDebug(json)")

// ── Chat channels ─────────────────────────────────────────────────────────────

fun jsAddServerLog(channel: String, message: String): Unit = js("mc.addServerLog(channel, message)")

fun jsAddChatMessage(channel: String, sender: String, message: String): Unit =
    js("mc.addChatMessage(channel, sender, message)")

fun jsChannelsSync(subscribedJson: String, knownJson: String): Unit =
    js("mc.channelsSync(subscribedJson, knownJson)")

fun jsGetActiveChannel(): String = js("(window.mcState.activeChannel) || 'world'")

// ── Autocomplete ──────────────────────────────────────────────────────────────

fun jsSetConnectedPlayers(namesJson: String): Unit = js("mc.setConnectedPlayers(namesJson)")

fun jsSetNpcNames(namesJson: String): Unit = js("mc.setNpcNames(namesJson)")

fun jsUpdateNpcProximity(json: String): Unit = js("mc.updateNpcProximity(json)")

// ── Preferences ───────────────────────────────────────────────────────────────

fun jsPreferencesSync(json: String): Unit = js("mc.preferencesSync(json)")

fun jsConsumePreferencesUpdate(): String = js("mc.consumePreferencesUpdate()")

fun jsConsumeRunMacroScript(): String = js("mc.consumeRunMacroScript()")

fun jsShowPreferences(): Unit = js("mc.showPreferences()")

// ── Codex ─────────────────────────────────────────────────────────────────────

fun jsOpenCodex(): Unit = js("mc.openCodex()")

fun jsToggleIngameMap(): Unit = js("mc.toggleIngameMap()")

// ── Craft ──────────────────────────────────────────────────────────────────────

fun jsOpenCraft(): Unit = js("mc.openCraft()")

fun jsRecipeSync(json: String): Unit = js("mc.recipeSync(json)")

// ── Trade ─────────────────────────────────────────────────────────────────────

fun jsOpenTrade(tradeId: String, otherPlayer: String, myRole: String): Unit =
    js("mc.openTrade(tradeId, otherPlayer, myRole)")

fun jsTradeUpdate(json: String): Unit = js("mc.tradeUpdate(json)")

fun jsTradeClosed(tradeId: String, reason: String): Unit = js("mc.tradeClosed(tradeId, reason)")

// ── RPG Character ──────────────────────────────────────────────────────────────

fun jsShowCharacterCreation(): Unit = js("mc.showCharacterCreation()")

fun jsCharacterSync(json: String): Unit = js("mc.characterSync(json)")

// ── Combat ────────────────────────────────────────────────────────────────────

fun jsCombatTargetUpdate(json: String): Unit = js("mc.combatTargetUpdate(json)")

fun jsHealthUpdate(json: String): Unit = js("mc.healthUpdate(json)")

fun jsPlayerStatusUpdate(json: String): Unit = js("mc.playerStatusUpdate(json)")

fun jsGodModeUpdate(enabled: Boolean): Unit = js("mc.godModeUpdate(enabled)")

fun jsEditModeUpdate(mode: String): Unit = js("mc.editModeUpdate(mode)")

fun jsStatusEffectUpdate(json: String): Unit = js("mc.statusEffectUpdate(json)")

fun jsPlayerDowned(playerId: String): Unit = js("mc.playerDowned(playerId)")

fun jsPlayerRespawned(json: String): Unit = js("mc.playerRespawned(json)")

fun jsXpGained(json: String): Unit = js("mc.xpGained(json)")

// ── Quest ─────────────────────────────────────────────────────────────────────

fun jsQuestSync(json: String): Unit = js("mc.questSync(json)")

fun jsQuestUpdate(json: String): Unit = js("mc.questUpdate(json)")

fun jsOpenQuestJournal(): Unit = js("mc.openQuestJournal()")

fun jsToggleQuestTracker(): Unit = js("mc.toggleQuestTracker()")

fun jsWalletUpdate(copper: Long): Unit = js("mc.walletUpdate(copper)")

// ── Mail ──────────────────────────────────────────────────────────────────────

fun jsMailSync(json: String): Unit = js("mc.mailSync(json)")

fun jsMailReceived(json: String): Unit = js("mc.mailReceived(json)")

fun jsMailUpdate(json: String): Unit = js("mc.mailUpdate(json)")

fun jsMailDeleted(mailId: String): Unit = js("mc.mailDeleted(mailId)")

fun jsOpenMailbox(): Unit = js("mc.openMailbox()")

// ── Auction house ─────────────────────────────────────────────────────────────

fun jsOpenAuctionHouse(): Unit = js("mc.openAuctionHouse()")

fun jsAuctionListingsUpdate(json: String): Unit = js("mc.auctionListingsUpdate(json)")

// ── Instance zones ────────────────────────────────────────────────────────────

fun jsAdminZoneWireframe(json: String): Unit = js("mc.adminZoneWireframe(json)")

fun jsInstanceZonesSync(json: String): Unit = js("mc.instanceZonesSync(json)")

// ── Scenes ─────────────────────────────────────────────────────────────────────

fun jsScenesSync(json: String): Unit = js("mc.scenesSync(json)")

fun jsScenePreviewData(json: String): Unit = js("mc.scenePreviewData(json)")
