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

fun jsReload(): Unit = js("mcReload()")

fun jsHasUrlParam(name: String): Boolean =
    js("(new URLSearchParams(window.location.search).has(name))")

fun jsGetUrlParam(name: String): String = js("mcGetUrlParam(name)")

// ── i18n ─────────────────────────────────────────────────────────────────────

fun jsFetchI18n(locale: String): Unit = js("mcFetchI18n(locale)")

// ── Biome colors ──────────────────────────────────────────────────────────────

fun jsFetchBiomeColors(): Unit = js("mcFetchBiomeColors()")

fun jsApplyBiomeGrassTint(biome: String): Unit = js("mcApplyBiomeGrassTint(biome)")

// ── Block/Item registry ───────────────────────────────────────────────────────

fun jsSetBlockRegistry(json: String): Unit = js("mcSetBlockRegistry(json)")

fun jsSetItemRegistry(json: String): Unit = js("mcSetItemRegistry(json)")

fun jsSetNpcDefinitions(json: String): Unit = js("mcSetNpcDefinitions(json)")

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
    js("mcSetupDebugCameraKeys(camera, scene, bx, by, bz)")
