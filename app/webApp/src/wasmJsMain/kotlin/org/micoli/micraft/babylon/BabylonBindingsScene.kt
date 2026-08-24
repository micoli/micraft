@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Engine / Scene ────────────────────────────────────────────────────────────

fun jsCreateEngine(): JsAny = js("mc.createEngine()")

fun jsCreateScene(engine: JsAny): JsAny = js("new BABYLON.Scene(engine)")

// Times each scene.render() call (the actual GPU draw submit) into window.mcState — read/reset
// by LocalPlayerController's spike ring buffer (see jsGetRenderMsAccum/jsResetRenderStats
// below), added after mesh/GPU-upload/physics/interaction timing left most of the observed
// frame-time budget unaccounted for: that missing time is spent here, between tick() calls,
// not inside any Kotlin logic.
fun jsEngineRunRenderLoop(engine: JsAny, scene: JsAny): Unit =
    js(
        "{ var _lr=0; engine.runRenderLoop(function(){ var now=Date.now(); if(now-_lr<14) return; _lr=now; var s=window.mcState.camState; if(s&&scene.activeCamera&&window.mcState.editMode!=='creative'){var a=Math.min(1,(now-s.t)/16);scene.activeCamera.position.x=s.x0+(s.x1-s.x0)*a;scene.activeCamera.position.y=s.y0+(s.y1-s.y0)*a;scene.activeCamera.position.z=s.z0+(s.z1-s.z0)*a;} var _rt0=performance.now(); scene.render(); var _rms=performance.now()-_rt0; window.mcState.renderMsAccum=(window.mcState.renderMsAccum||0)+_rms; window.mcState.renderFrameCount=(window.mcState.renderFrameCount||0)+1; window.mcState.renderMsMax=Math.max(window.mcState.renderMsMax||0,_rms); }); }")

fun jsGetRenderMsAccum(): Double = js("window.mcState.renderMsAccum || 0")

fun jsGetRenderFrameCount(): Int = js("window.mcState.renderFrameCount || 0")

fun jsGetRenderMsMax(): Double = js("window.mcState.renderMsMax || 0")

fun jsResetRenderStats(): Unit =
    js(
        "{ window.mcState.renderMsAccum=0; window.mcState.renderFrameCount=0; window.mcState.renderMsMax=0; }")

fun jsSetupResize(engine: JsAny): Unit =
    js("window.addEventListener('resize', function(){ engine.resize(); })")

// ── Lights / Camera ───────────────────────────────────────────────────────────

fun jsCreateHemisphericLight(name: String, scene: JsAny): JsAny =
    js("mc.createHemisphericLight(name, scene)")

fun jsCreateSunLight(scene: JsAny): Unit = js("mc.createSunLight(scene)")

fun jsSetShadowAngleDeg(deg: Int): Unit = js("window.mcState.shadowAngleDeg = deg")

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
): Unit = js("window.mcState.camState={x0:x0,y0:y0,z0:z0,x1:x1,y1:y1,z1:z1,t:tickMs}")

fun jsClearCameraInterpolation(): Unit = js("window.mcState.camState=null")

fun jsCameraSetFov(camera: JsAny, fovDegrees: Int): Unit =
    js("camera.fov = fovDegrees * Math.PI / 180")

fun jsConsoleLog(msg: String): Unit = js("console.log(msg)")

fun jsSetWasmBuildTimestamp(ts: String): Unit =
    js("window.mcBuildInfo && (window.mcBuildInfo.wasm = ts)")

fun jsSetupRenderPipeline(scene: JsAny, camera: JsAny): Unit =
    js("mc.setupRenderPipeline(scene, camera)")
