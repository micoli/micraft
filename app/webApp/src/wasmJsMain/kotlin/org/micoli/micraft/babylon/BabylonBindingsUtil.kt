@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Logging ───────────────────────────────────────────────────────────────────

fun jsLog(msg: String): Unit = js("console.log('[MiCraft]', msg)")

fun jsWarn(msg: String): Unit = js("console.warn('[MiCraft]', msg)")

fun jsError(msg: String): Unit = js("console.error('[MiCraft]', msg)")

// ── Page / URL utils ──────────────────────────────────────────────────────────

fun jsGetPageHost(): String = js("window.location.hostname")

fun jsGetPagePort(): Int =
    js("parseInt(window.location.port) || (window.location.protocol === 'https:' ? 443 : 80)")

fun jsNow(): Double = js("Date.now()")

fun jsReload(): Unit = js("mc.reload()")

fun jsHasUrlParam(name: String): Boolean =
    js("(new URLSearchParams(window.location.search).has(name))")

fun jsGetUrlParam(name: String): String = js("mc.getUrlParam(name)")

// admin.html has no #renderCanvas (it hosts its own React-managed canvas) — used to skip the
// full game bootstrap (engine/scene/login overlay/GameClient) when webApp.js loads there instead
// of on the real game page, so only the admin chunk-preview exports remain callable.
fun jsHasRenderCanvas(): Boolean = js("!!document.getElementById('renderCanvas')")

// ── i18n ─────────────────────────────────────────────────────────────────────

fun jsFetchI18n(locale: String): Unit = js("mc.fetchI18n(locale)")

// ── Biome colors ──────────────────────────────────────────────────────────────

fun jsFetchBiomeColors(): Unit = js("mc.fetchBiomeColors()")

fun jsApplyBiomeGrassTint(biome: String): Unit = js("mc.applyBiomeGrassTint(biome)")

// ── Block/Item registry ───────────────────────────────────────────────────────

fun jsSetBlockRegistry(json: String): Unit = js("mc.setBlockRegistry(json)")

fun jsSetItemRegistry(json: String): Unit = js("mc.setItemRegistry(json)")

fun jsSetPlainColors(json: String): Unit = js("mc.setPlainColors(json)")

fun jsSetNpcDefinitions(json: String): Unit = js("mc.setNpcDefinitions(json)")

fun jsSetVehicleDefinitions(json: String): Unit = js("mc.setVehicleDefinitions(json)")

fun jsReloadAttackMeta(): Unit = js("mc.reloadAttackMeta()")

// ── LocalStorage ─────────────────────────────────────────────────────────────

fun jsLocalStorageGet(key: String): String = js("localStorage.getItem(key) || ''")

fun jsLocalStorageSet(key: String, value: String): Unit = js("localStorage.setItem(key, value)")

// ── Debug camera ─────────────────────────────────────────────────────────────

/**
 * Binds keys 1-6 to camera positions facing each face of the block at (bx,by,bz). Uses
 * onBeforeRenderObservable to override client-side prediction camera updates. Escape releases the
 * lock and restores free camera. Face mapping: 1=+Z, 2=-Z, 3=+X, 4=-X, 5=+Y, 6=-Y (BabylonJS
 * CreateBox order)
 */
fun jsSetupDebugCameraKeys(camera: JsAny, scene: JsAny, bx: Double, by: Double, bz: Double): Unit =
    js("mc.setupDebugCameraKeys(camera, scene, bx, by, bz)")
