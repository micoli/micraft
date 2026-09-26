@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Interactive panels ───────────────────────────────────────────────────────
// A panel is a placeable that shows navigable HTML, projected as a real DOM layer (CSS3D matrix3d)
// above the canvas instead of a mesh texture — see panelSurface.ts. There's no model-fetch
// readiness gate like other placeables: the DOM layer is always ready after registration.

fun jsInitPanelSurface(scene: JsAny, camera: JsAny): Unit = js("mc.initPanelSurface(scene, camera)")

fun jsPanelSync(panelsJson: String): Unit = js("mc.panelSync(panelsJson)")

fun jsPanelUpsert(panelJson: String): Unit = js("mc.panelUpsert(panelJson)")

fun jsPanelRemove(placeableId: String): Unit = js("mc.panelRemove(placeableId)")

fun jsSetPanelTransform(
    placeableId: String,
    x: Double,
    y: Double,
    z: Double,
    rotationStep: Int
): Unit = js("mc.setPanelTransform(placeableId, x, y, z, rotationStep)")

/** Enters focus mode (pointer-events on, pointer lock released) if [placeableId] is in range. */
fun jsFocusPanel(placeableId: String): Boolean = js("mc.focusPanel(placeableId)")

fun jsUnfocusPanel(): Unit = js("mc.unfocusPanel()")

fun jsPanelFocusedId(): String? = js("window.mcState.panelFocusedId")
