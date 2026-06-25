@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Disconnect overlay ────────────────────────────────────────────────────────

fun jsShowDisconnectedOverlay(message: String): Unit = js("mcShowDisconnectedOverlay(message)")

fun jsHideDisconnectedOverlay(): Unit = js("mcHideDisconnectedOverlay()")

// ── Login overlay ─────────────────────────────────────────────────────────────

fun jsShowLoginOverlay(): Unit = js("mcShowLoginOverlay()")

fun jsHideLoginOverlay(): Unit = js("mcHideLoginOverlay()")

fun jsConsumeLoginResult(): String = js("mcConsumeLoginResult()")

// ── Console / Server log ──────────────────────────────────────────────────────

fun jsCreateConsole(): Unit = js("mcCreateConsole()")

fun jsCreateServerLog(): Unit = js("mcCreateServerLog()")

fun jsConsoleSetPlayer(name: String): Unit = js("mcConsoleSetPlayer(name)")

fun jsIsConsoleOpen(): Boolean = js("mcIsConsoleOpen()")

fun jsConsumeConsoleInput(): String = js("mcConsumeConsoleInput()")

fun jsShowNotification(message: String): Unit = js("mcShowNotification(message)")

// ── Hotbar / ShortcutBar ──────────────────────────────────────────────────────

fun jsCreateHotbar(): Unit = js("mcCreateHotbar()")

fun jsUpdateHotbar(inventoryJson: String): Unit = js("mcUpdateHotbar(inventoryJson)")

fun jsToggleHotbar(): Unit = js("mcToggleHotbar()")

fun jsUpdateShortcutBar(json: String): Unit = js("mcUpdateShortcutBar(json)")

fun jsSetSelectedSlot(slot: Int): Unit = js("mcSetSelectedSlot(slot)")

fun jsConsumeSlotUpdate(): String = js("mcConsumeSlotUpdate()")

// ── HUD ───────────────────────────────────────────────────────────────────────

fun jsCreateHUD(): Unit = js("mcCreateHUD()")

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
    gameTime: String
): Unit =
    js(
        "mcUpdateHUD(x, y, z, yaw, pitch, stance, speed, fps, kbIn, kbOut, biome, targetBlock, gameTime)")

// ── Layout ────────────────────────────────────────────────────────────────────

fun jsSyncLayouts(json: String): Unit = js("mcSyncLayouts(json)")

fun jsShowLayoutEditor(): Unit = js("mcShowLayoutEditor()")

fun jsConsumeLayoutUpdate(): String = js("mcConsumeLayoutUpdate()")

// ── Minimap ───────────────────────────────────────────────────────────────────

fun jsCreateMinimap(): Unit = js("mcCreateMinimap()")

fun jsSetMinimapChunk(cx: Int, cz: Int, topYJson: String, topBlockJson: String): Unit =
    js("mcSetMinimapChunk(cx, cz, topYJson, topBlockJson)")

fun jsClearMinimapChunk(cx: Int, cz: Int): Unit = js("mcClearMinimapChunk(cx, cz)")

fun jsDrawMinimap(playerX: Double, playerZ: Double, playerYaw: Double): Unit =
    js("mcDrawMinimap(playerX, playerZ, playerYaw)")

fun jsSetNpcOnMinimap(id: String, x: Float, z: Float): Unit = js("mcSetNpcOnMinimap(id, x, z)")

fun jsRemoveNpcFromMinimap(id: String): Unit = js("mcRemoveNpcFromMinimap(id)")

// ── Chat channels ─────────────────────────────────────────────────────────────

fun jsAddServerLog(channel: String, message: String): Unit = js("mcAddServerLog(channel, message)")

fun jsAddChatMessage(channel: String, sender: String, message: String): Unit =
    js("mcAddChatMessage(channel, sender, message)")

fun jsChannelsSync(subscribedJson: String, knownJson: String): Unit =
    js("mcChannelsSync(subscribedJson, knownJson)")

fun jsGetActiveChannel(): String = js("(window.__mcActiveChannel) || 'world'")

// ── Autocomplete ──────────────────────────────────────────────────────────────

fun jsSetConnectedPlayers(namesJson: String): Unit = js("mcSetConnectedPlayers(namesJson)")

fun jsSetNpcNames(namesJson: String): Unit = js("mcSetNpcNames(namesJson)")

// ── Preferences ───────────────────────────────────────────────────────────────

fun jsPreferencesSync(json: String): Unit = js("mcPreferencesSync(json)")

fun jsConsumePreferencesUpdate(): String = js("mcConsumePreferencesUpdate()")

fun jsShowPreferences(): Unit = js("mcShowPreferences()")
