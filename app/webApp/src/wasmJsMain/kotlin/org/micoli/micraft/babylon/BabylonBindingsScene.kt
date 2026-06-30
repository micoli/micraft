@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Engine / Scene ────────────────────────────────────────────────────────────

fun jsCreateEngine(): JsAny = js("mcCreateEngine()")

fun jsCreateScene(engine: JsAny): JsAny = js("new BABYLON.Scene(engine)")

fun jsEngineRunRenderLoop(engine: JsAny, scene: JsAny): Unit =
    js(
        "engine.runRenderLoop(function(){ var s=window.__mcCamState; if(s&&scene.activeCamera){var a=Math.min(1,(Date.now()-s.t)/16);scene.activeCamera.position.x=s.x0+(s.x1-s.x0)*a;scene.activeCamera.position.y=s.y0+(s.y1-s.y0)*a;scene.activeCamera.position.z=s.z0+(s.z1-s.z0)*a;} scene.render(); })")

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

fun jsSetCameraInterpolationState(
    x0: Double,
    y0: Double,
    z0: Double,
    x1: Double,
    y1: Double,
    z1: Double,
    tickMs: Double
): Unit = js("window.__mcCamState={x0:x0,y0:y0,z0:z0,x1:x1,y1:y1,z1:z1,t:tickMs}")

fun jsClearCameraInterpolation(): Unit = js("window.__mcCamState=null")

fun jsSetupRenderPipeline(scene: JsAny, camera: JsAny): Unit =
    js("mcSetupRenderPipeline(scene, camera)")
