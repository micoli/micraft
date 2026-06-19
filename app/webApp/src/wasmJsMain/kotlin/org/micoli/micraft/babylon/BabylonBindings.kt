package org.micoli.micraft.babylon

fun jsLog(msg: String): Unit  = js("console.log('[MiCraft]', msg)")
fun jsWarn(msg: String): Unit  = js("console.warn('[MiCraft]', msg)")
fun jsError(msg: String): Unit = js("console.error('[MiCraft]', msg)")

// ── Engine / Scene ────────────────────────────────────────────────────────────
// The canvas is accessed directly inside js() to avoid Kotlin/Wasm externref
// wrapping issues when passing JsAny parameters through __externalAdapters.

fun jsCreateEngine(): JsAny =
    js("new BABYLON.Engine(document.getElementById('renderCanvas'), false)")

fun jsCreateScene(engine: JsAny): JsAny = js("new BABYLON.Scene(engine)")

fun jsEngineRunRenderLoop(engine: JsAny, scene: JsAny): Unit =
    js("engine.runRenderLoop(function(){ scene.render(); })")

fun jsSetupResize(engine: JsAny): Unit =
    js("window.addEventListener('resize', function(){ engine.resize(); })")

// ── Lights / Camera ───────────────────────────────────────────────────────────

fun jsCreateHemisphericLight(name: String, scene: JsAny): JsAny =
    js("new BABYLON.HemisphericLight(name, new BABYLON.Vector3(0,1,0), scene)")

fun jsCreateCamera(name: String, x: Double, y: Double, z: Double, scene: JsAny): JsAny =
    js("new BABYLON.UniversalCamera(name, new BABYLON.Vector3(x,y,z), scene)")

fun jsCameraSetTarget(camera: JsAny, x: Double, y: Double, z: Double): Unit =
    js("camera.setTarget(new BABYLON.Vector3(x,y,z))")

// Canvas retrieved internally — avoids the externref wrapping issue.
fun jsCameraAttachControl(camera: JsAny): Unit =
    js("camera.attachControl(document.getElementById('renderCanvas'), true)")

fun jsCameraSetPosition(camera: JsAny, x: Double, y: Double, z: Double): Unit =
    js("camera.position = new BABYLON.Vector3(x, y, z)")

// ── Meshes ────────────────────────────────────────────────────────────────────

fun jsCreateBox(name: String, size: Double, scene: JsAny): JsAny =
    js("BABYLON.MeshBuilder.CreateBox(name, { size: size }, scene)")

fun jsSetMeshPosition(mesh: JsAny, x: Double, y: Double, z: Double): Unit =
    js("mesh.position = new BABYLON.Vector3(x,y,z)")

fun jsDisposeMesh(mesh: JsAny): Unit = js("mesh.dispose()")

// ── Materials ─────────────────────────────────────────────────────────────────

fun jsCreateMaterial(name: String, scene: JsAny): JsAny =
    js("new BABYLON.StandardMaterial(name, scene)")

fun jsSetMaterialColor(mat: JsAny, r: Double, g: Double, b: Double): Unit =
    js("mat.diffuseColor = new BABYLON.Color3(r,g,b)")

fun jsSetMeshMaterial(mesh: JsAny, mat: JsAny): Unit =
    js("mesh.material = mat")

// ── HUD ───────────────────────────────────────────────────────────────────────

fun jsCreateHUD(): Unit = js("""
(function(){
  var d = document.createElement('div');
  d.id = 'hud';
  d.style.cssText = 'position:fixed;top:12px;right:12px;background:rgba(0,0,0,0.55);color:#fff;font:13px/1.6 monospace;padding:8px 12px;border-radius:6px;pointer-events:none;z-index:999;white-space:pre';
  document.body.appendChild(d);
})()
""")

fun jsUpdateHUD(x: Double, y: Double, z: Double, yaw: Double, pitch: Double): Unit = js("""
(function(){
  var d = document.getElementById('hud');
  if(d) d.textContent =
    'X  ' + x.toFixed(2) + '\n' +
    'Y  ' + y.toFixed(2) + '\n' +
    'Z  ' + z.toFixed(2) + '\n' +
    'Yaw   ' + yaw.toFixed(1) + '°\n' +
    'Pitch ' + pitch.toFixed(1) + '°';
})()
""")
