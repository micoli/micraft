@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Loading overlay ────────────────────────────────────────────────────────────

fun jsUpdateChunkLoading(loaded: Int, total: Int): Unit = js("mc.updateChunkLoading(loaded, total)")

fun jsHideChunkLoading(): Unit = js("mc.hideChunkLoading()")

// ── Disconnect overlay ────────────────────────────────────────────────────────

fun jsShowDisconnectedOverlay(message: String): Unit = js("mc.showDisconnectedOverlay(message)")

fun jsHideDisconnectedOverlay(): Unit = js("mc.hideDisconnectedOverlay()")

// ── Login overlay ─────────────────────────────────────────────────────────────

fun jsShowLoginOverlay(): Unit = js("mc.showLoginOverlay()")

fun jsHideLoginOverlay(): Unit = js("mc.hideLoginOverlay()")

fun jsConsumeLoginResult(): String = js("mc.consumeLoginResult()")

// ── Console / Server log ──────────────────────────────────────────────────────

fun jsCreateConsole(): Unit = js("mc.createConsole()")

fun jsCreateServerLog(): Unit = js("mc.createServerLog()")

fun jsConsoleSetPlayer(name: String): Unit = js("mc.consoleSetPlayer(name)")

fun jsIsConsoleOpen(): Boolean = js("mc.isConsoleOpen()")

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
): Unit =
    js(
        "mc.updateHUD(x, y, z, yaw, pitch, stance, speed, fps, kbIn, kbOut, biome, targetBlock, gameTime, reconcileXzStats, reconcileYStats, tickDtMs, tickJitterMs, tickDtMinMs, tickDtMaxMs, tickJitterMinMs, tickJitterMaxMs, chunkDownloading, chunkMeshing)")

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

// ── Preferences ───────────────────────────────────────────────────────────────

fun jsPreferencesSync(json: String): Unit = js("mc.preferencesSync(json)")

fun jsConsumePreferencesUpdate(): String = js("mc.consumePreferencesUpdate()")

fun jsShowPreferences(): Unit = js("mc.showPreferences()")

// ── Codex ─────────────────────────────────────────────────────────────────────

fun jsOpenCodex(): Unit = js("mc.openCodex()")
