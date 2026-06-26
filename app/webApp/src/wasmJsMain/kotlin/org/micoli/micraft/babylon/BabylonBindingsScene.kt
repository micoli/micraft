@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Engine / Scene ────────────────────────────────────────────────────────────

fun jsCreateEngine(): JsAny = js("mcCreateEngine()")

fun jsCreateScene(engine: JsAny): JsAny = js("new BABYLON.Scene(engine)")

fun jsEngineRunRenderLoop(engine: JsAny, scene: JsAny): Unit =
    js("engine.runRenderLoop(function(){ scene.render(); })")

fun jsSetupResize(engine: JsAny): Unit =
    js("window.addEventListener('resize', function(){ engine.resize(); })")

// ── Lights / Camera ───────────────────────────────────────────────────────────

fun jsCreateHemisphericLight(name: String, scene: JsAny): JsAny =
    js("mcCreateHemisphericLight(name, scene)")

fun jsCreateCamera(name: String, x: Double, y: Double, z: Double, scene: JsAny): JsAny =
    js(
        "(function(){ var c = new BABYLON.UniversalCamera(name, new BABYLON.Vector3(x,y,z), scene); c.minZ = 0.05; return c; })()")

fun jsCameraSetTarget(camera: JsAny, x: Double, y: Double, z: Double): Unit =
    js("camera.setTarget(new BABYLON.Vector3(x,y,z))")

// Canvas retrieved internally — avoids the externref wrapping issue.
fun jsCameraAttachControl(camera: JsAny): Unit =
    js("camera.attachControl(document.getElementById('renderCanvas'), true)")

fun jsCameraSetPosition(camera: JsAny, x: Double, y: Double, z: Double): Unit =
    js("camera.position = new BABYLON.Vector3(x, y, z)")

fun jsSetupRenderPipeline(scene: JsAny, camera: JsAny): Unit =
    js("mcSetupRenderPipeline(scene, camera)")
