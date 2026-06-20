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

fun jsCreateHemisphericLight(name: String, scene: JsAny): JsAny = js("""
(function(){
  var l = new BABYLON.HemisphericLight(name, new BABYLON.Vector3(0,1,0), scene);
  l.groundColor = new BABYLON.Color3(0.4, 0.4, 0.4);
  return l;
})()
""")

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

fun jsCreateBox(name: String, size: Double, scene: JsAny): JsAny = js("""
(function(){
  var uv = () => new BABYLON.Vector4(0, 1, 1, 0);
  var box = BABYLON.MeshBuilder.CreateBox(name, {
    size: size,
    faceUV: [uv(), uv(), uv(), uv(), uv(), uv()]
  }, scene);
  box.subMeshes = [];
  var vc = box.getTotalVertices();
  for (var i = 0; i < 6; i++) {
    new BABYLON.SubMesh(i, 0, vc, i * 6, 6, box);
  }
  return box;
})()
""")

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

fun jsCreateTextureMaterial(name: String, url: String, scene: JsAny): JsAny = js("""
(function(){
  var mat = new BABYLON.StandardMaterial(name, scene);
  mat.diffuseTexture = new BABYLON.Texture(url, scene);
  mat.diffuseTexture.hasAlpha = false;
  mat.specularColor = new BABYLON.Color3(0, 0, 0);
  mat.backFaceCulling = false;
  return mat;
})()
""")

fun jsCreateGrassMaterial(scene: JsAny): JsAny = js("""
(function(){
  var texMat = (n, u, ang) => {
    var m = new BABYLON.StandardMaterial(n, scene);
    m.diffuseTexture = new BABYLON.Texture(u, scene);
    if (ang !== undefined) m.diffuseTexture.wAng = ang;
    m.diffuseTexture.hasAlpha = false;
    m.specularColor = new BABYLON.Color3(0, 0, 0);
    m.backFaceCulling = false;
    return m;
  };
  var top    = texMat('grass_top',    '/textures/blocks/grass_top.png');
  top.diffuseColor = new BABYLON.Color3(0.47, 0.75, 0.35);
  var sideFr = texMat('grass_side_fr', '/textures/blocks/grass_side.png');              // +Z: wAng=0
  var sideBk = texMat('grass_side_bk', '/textures/blocks/grass_side.png', Math.PI);    // -Z: wAng=π
  var sideX  = texMat('grass_side_x',  '/textures/blocks/grass_side.png', Math.PI/2);  // ±X: wAng=π/2
  var bottom = texMat('grass_bot',    '/textures/blocks/dirt.png');
  var multi = new BABYLON.MultiMaterial('grass', scene);
  // BabylonJS CreateBox: 0=front(+Z), 1=back(-Z), 2=right(+X), 3=left(-X), 4=top(+Y), 5=bottom(-Y)
  multi.subMaterials = [sideFr, sideBk, sideX, sideX, top, bottom];
  return multi;
})()
""")

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
        'ShiftLeft','ControlLeft','Space','KeyP','KeyO'].includes(e.code))
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
fun jsNow(): Double             = js("Date.now()")

// ── Disconnect overlay ────────────────────────────────────────────────────────

fun jsShowDisconnectedOverlay(message: String): Unit = js("""
(function(){
  var d = document.getElementById('mc-disconnect');
  if (!d) {
    d = document.createElement('div');
    d.id = 'mc-disconnect';
    d.style.cssText = [
      'position:fixed;inset:0;display:flex;flex-direction:column',
      'align-items:center;justify-content:center',
      'background:rgba(0,0,0,0.72);color:#fff',
      'font:bold 22px/2 monospace;z-index:1000;text-align:center'
    ].join(';');
    document.body.appendChild(d);
  }
  d.style.display = 'flex';
  d.innerHTML = '⚠️ DISCONNECTED<br><span style="font-size:15px;font-weight:normal">' + message + '</span>';
})()
""")

fun jsHideDisconnectedOverlay(): Unit = js("""
(function(){
  var d = document.getElementById('mc-disconnect');
  if (d) d.style.display = 'none';
})()
""")

// ── HUD ───────────────────────────────────────────────────────────────────────

fun jsCreateHUD(): Unit = js("""
(function(){
  var d = document.createElement('div');
  d.id = 'hud';
  d.style.cssText = 'position:fixed;top:12px;right:12px;background:rgba(0,0,0,0.55);color:#fff;font:13px/1.6 monospace;padding:8px 12px;border-radius:6px;pointer-events:none;z-index:999;white-space:pre';
  document.body.appendChild(d);
})()
""")

fun jsUpdateHUD(x: Double, y: Double, z: Double, yaw: Double, pitch: Double, stance: String, speed: Double, fps: Int, kbIn: Double, kbOut: Double): Unit = js("""
(function(){
  var d = document.getElementById('hud');
  if(d) d.textContent =
    'FPS   ' + fps + '\n' +
    'X  ' + x.toFixed(2) + '\n' +
    'Y  ' + y.toFixed(2) + '\n' +
    'Z  ' + z.toFixed(2) + '\n' +
    'Yaw   ' + yaw.toFixed(1) + '°\n' +
    'Pitch ' + pitch.toFixed(1) + '°\n' +
    stance + '\n' +
    'Speed ×' + speed.toFixed(1) + '\n' +
    '↓ ' + kbIn.toFixed(1) + ' KB/s  ↑ ' + kbOut.toFixed(1) + ' KB/s';
})()
""")
