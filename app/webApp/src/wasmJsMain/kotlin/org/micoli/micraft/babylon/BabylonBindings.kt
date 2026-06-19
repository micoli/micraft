package org.micoli.micraft.babylon

fun jsLog(msg: String): Unit  = js("console.log('[MiCraft]', msg)")
fun jsWarn(msg: String): Unit  = js("console.warn('[MiCraft]', msg)")
fun jsError(msg: String): Unit = js("console.error('[MiCraft]', msg)")

// ── Engine / Scene ────────────────────────────────────────────────────────────
// The canvas is accessed directly inside js() to avoid Kotlin/Wasm externref
// wrapping issues when passing JsAny parameters through __externalAdapters.

fun jsCreateEngine(): JsAny = js("""
(function(){
  // 1. Dispose any previous engine (HMR / multiple reloads exhaust WebGL context limit)
  if (window.__mcEngine) {
    try { window.__mcEngine.dispose(); } catch(e) {}
    window.__mcEngine = null;
  }

  var canvas = document.getElementById('renderCanvas');
  if (!canvas) throw new Error('[MiCraft] Canvas #renderCanvas not found');

  // 2. Diagnose WebGL availability before letting BabylonJS throw an opaque error
  var probe = document.createElement('canvas');
  var gl = probe.getContext('webgl2') || probe.getContext('webgl');
  if (!gl) {
    console.error('[MiCraft] WebGL unavailable. Open chrome://gpu and check that ' +
      '"WebGL" and "Hardware-accelerated" are enabled. ' +
      'You can also try: chrome://settings/system → enable hardware acceleration.');
    throw new Error('[MiCraft] WebGL not supported by this browser / GPU configuration');
  }
  gl = null;

  // 3. Create engine — try WebGL2, fall back to WebGL1
  var engine;
  try {
    engine = new BABYLON.Engine(canvas, false, { disableWebGL2Support: false, preserveDrawingBuffer: false });
  } catch(e) {
    console.warn('[MiCraft] WebGL2 failed (' + e.message + '), retrying with WebGL1');
    engine = new BABYLON.Engine(canvas, false, { disableWebGL2Support: true });
  }

  window.__mcEngine = engine;
  window.addEventListener('beforeunload', function(){ engine.dispose(); }, { once: true });
  console.log('[MiCraft] Engine created: ' + (engine.webGLVersion === 2 ? 'WebGL2' : 'WebGL1'));
  return engine;
})()
""")

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

// ── Input ─────────────────────────────────────────────────────────────────────

fun jsSetupKeyboard(): Unit = js("""
(function(){
  window.__mc = window.__mc || { keys: {}, flyToggle: false, lastSpaceTime: 0 };
  window.addEventListener('keydown', function(e){
    if (e.repeat) return;
    window.__mc.keys[e.code] = true;
    if (e.code === 'Space') {
      var now = Date.now();
      if (now - window.__mc.lastSpaceTime < 300) {
        window.__mc.flyToggle = true;
      }
      window.__mc.lastSpaceTime = now;
    }
    if(['KeyW','KeyA','KeyS','KeyD','ArrowUp','ArrowDown','ArrowLeft','ArrowRight',
        'ShiftLeft','ControlLeft','Space'].includes(e.code))
      e.preventDefault();
  });
  window.addEventListener('keyup', function(e){
    window.__mc.keys[e.code] = false;
  });
})()
""")

fun jsIsKeyDown(code: String): Boolean = js("!!(window.__mc && window.__mc.keys[code])")

fun jsDisableCameraKeyboard(camera: JsAny): Unit =
    js("camera.inputs.removeByType('FreeCameraKeyboardMoveInput')")

fun jsGetCameraForwardX(camera: JsAny): Double = js("""
(function(){
  var d = camera.getForwardRay(1).direction;
  var l = Math.sqrt(d.x*d.x + d.z*d.z) || 1;
  return d.x / l;
})()
""")

fun jsGetCameraForwardZ(camera: JsAny): Double = js("""
(function(){
  var d = camera.getForwardRay(1).direction;
  var l = Math.sqrt(d.x*d.x + d.z*d.z) || 1;
  return d.z / l;
})()
""")

fun jsGetCameraRotationY(camera: JsAny): Double = js("camera.rotation.y")
fun jsGetCameraRotationX(camera: JsAny): Double = js("camera.rotation.x")

fun jsGetCameraForwardY(camera: JsAny): Double = js("camera.getForwardRay(1).direction.y")

fun jsConsumeFlyToggle(): Boolean = js("""
(function(){
  if (!window.__mc) return false;
  var v = window.__mc.flyToggle;
  window.__mc.flyToggle = false;
  return v;
})()
""")

fun jsGetPageHost(): String     = js("window.location.hostname")
fun jsGetPagePort(): Int        = js("parseInt(window.location.port) || (window.location.protocol === 'https:' ? 443 : 80)")

// ── HUD ───────────────────────────────────────────────────────────────────────

fun jsCreateHUD(): Unit = js("""
(function(){
  var d = document.createElement('div');
  d.id = 'hud';
  d.style.cssText = 'position:fixed;top:12px;right:12px;background:rgba(0,0,0,0.55);color:#fff;font:13px/1.6 monospace;padding:8px 12px;border-radius:6px;pointer-events:none;z-index:999;white-space:pre';
  document.body.appendChild(d);
})()
""")

fun jsUpdateHUD(x: Double, y: Double, z: Double, yaw: Double, pitch: Double, stance: String): Unit = js("""
(function(){
  var d = document.getElementById('hud');
  if(d) d.textContent =
    'X  ' + x.toFixed(2) + '\n' +
    'Y  ' + y.toFixed(2) + '\n' +
    'Z  ' + z.toFixed(2) + '\n' +
    'Yaw   ' + yaw.toFixed(1) + '°\n' +
    'Pitch ' + pitch.toFixed(1) + '°\n' +
    stance;
})()
""")
