// mc_bindings.js — BabylonJS host functions called from Kotlin/Wasm via js()
// Must be loaded AFTER babylon.js and BEFORE webApp.js.

window.mcCreateEngine = function () {
  if (window.__mcEngine) {
    try { window.__mcEngine.dispose(); } catch (e) {}
    window.__mcEngine = null;
  }

  var canvas = document.getElementById('renderCanvas');
  if (!canvas) throw new Error('[MiCraft] Canvas #renderCanvas not found');

  var probe = document.createElement('canvas');
  var gl = probe.getContext('webgl2') || probe.getContext('webgl');
  if (!gl) {
    console.error('[MiCraft] WebGL unavailable. Open chrome://gpu and check that ' +
      '"WebGL" and "Hardware-accelerated" are enabled. ' +
      'You can also try: chrome://settings/system → enable hardware acceleration.');
    throw new Error('[MiCraft] WebGL not supported by this browser / GPU configuration');
  }
  gl = null;

  var engine;
  try {
    engine = new BABYLON.Engine(canvas, false, { disableWebGL2Support: false, preserveDrawingBuffer: false });
  } catch (e) {
    console.warn('[MiCraft] WebGL2 failed (' + e.message + '), retrying with WebGL1');
    engine = new BABYLON.Engine(canvas, false, { disableWebGL2Support: true });
  }

  window.__mcEngine = engine;
  window.addEventListener('beforeunload', function () { engine.dispose(); }, { once: true });
  console.log('[MiCraft] Engine created: ' + (engine.webGLVersion === 2 ? 'WebGL2' : 'WebGL1'));
  return engine;
};

window.mcCreateHemisphericLight = function (name, scene) {
  var l = new BABYLON.HemisphericLight(name, new BABYLON.Vector3(0, 1, 0), scene);
  l.groundColor = new BABYLON.Color3(0.4, 0.4, 0.4);
  return l;
};

// Multi-face box for GRASS (6 SubMeshes → MultiMaterial with per-face texture).
window.mcCreateBox = function (name, size, scene) {
  var uv = function () { return new BABYLON.Vector4(0, 1, 1, 0); };
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
};

// Simple box for uniform-material blocks (STONE, DIRT, BEDROCK): 1 draw call vs 6.
window.mcCreateSimpleBox = function (name, size, scene) {
  return BABYLON.MeshBuilder.CreateBox(name, { size: size }, scene);
};

// Freeze world matrix (static block never moves) and disable picking.
window.mcFreezeMesh = function (mesh) {
  mesh.freezeWorldMatrix();
  mesh.isPickable = false;
  mesh.doNotSyncBoundingInfo = true;
};

// One-time scene tweaks: skip per-frame pointer picking and material dirty checks.
window.mcOptimizeScene = function (scene) {
  scene.skipPointerMovePicking = true;
  scene.blockMaterialDirtyMechanism = true;
};

window.mcCreateTextureMaterial = function (name, url, scene) {
  var mat = new BABYLON.StandardMaterial(name, scene);
  mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
  mat.diffuseTexture.hasAlpha = false;
  mat.specularColor = new BABYLON.Color3(0, 0, 0);
  mat.backFaceCulling = false;
  return mat;
};

window.mcCreateGrassMaterial = function (scene) {
  var texMat = function (n, u, ang) {
    var m = new BABYLON.StandardMaterial(n, scene);
    m.diffuseTexture = new BABYLON.Texture(u, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
    if (ang !== undefined) m.diffuseTexture.wAng = ang;
    m.diffuseTexture.hasAlpha = false;
    m.specularColor = new BABYLON.Color3(0, 0, 0);
    m.backFaceCulling = false;
    return m;
  };
  var top    = texMat('grass_top',    '/textures/blocks/grass_top.png');
  top.diffuseColor = new BABYLON.Color3(0.47, 0.75, 0.35);
  var sideFr = texMat('grass_side_fr', '/textures/blocks/grass_side.png', Math.PI);
  var sideBk = texMat('grass_side_bk', '/textures/blocks/grass_side.png', Math.PI);
  var sideX  = texMat('grass_side_x',  '/textures/blocks/grass_side.png', Math.PI / 2);
  var sideX2  = texMat('grass_side_x',  '/textures/blocks/grass_side.png', Math.PI);
  var bottom = texMat('grass_bot',    '/textures/blocks/dirt.png');
  var multi = new BABYLON.MultiMaterial('grass', scene);
  // BabylonJS CreateBox:
  multi.subMaterials = [
  sideFr,  //0=front(+Z)
  sideBk,  //1=back(-Z)
  sideX2,   //2=right(+X)
  sideX,   //3=left(-X)
  top,     //4=top(+Y)
  bottom   //5=bottom(-Y)
  ];
  return multi;
};

var MC_DEFAULT_BINDINGS = {
  forward:      ['KeyW', 'ArrowUp'],
  backward:     ['KeyS', 'ArrowDown'],
  strafe_right: ['KeyD', 'ArrowRight'],
  strafe_left:  ['KeyA', 'ArrowLeft'],
  rotate_left:  ['KeyQ'],
  rotate_right: ['KeyE'],
  sneak:        ['ShiftLeft'],
  crawl:        ['ControlLeft'],
  fly_toggle:   ['Space'],
  ascend:       ['Space'],
  descend:      ['ShiftLeft'],
  speed_up:     ['KeyP'],
  speed_down:   ['KeyO'],
  view_toggle:  ['KeyF'],
  inventory:    ['KeyI'],
  undo:         ['Ctrl+KeyZ', 'Cmd+KeyZ'],
};

// Parse "Ctrl+Shift+KeyZ" → { mods: {ctrl,shift,alt,meta}, key: "KeyZ" }
function mcParseBoundKey(str) {
  var parts = str.split('+');
  var key = parts[parts.length - 1];
  var mods = { ctrl: false, shift: false, alt: false, meta: false };
  for (var i = 0; i < parts.length - 1; i++) {
    var m = parts[i].toLowerCase();
    if (m === 'ctrl' || m === 'control')              mods.ctrl  = true;
    else if (m === 'shift')                            mods.shift = true;
    else if (m === 'alt' || m === 'option')            mods.alt   = true;
    else if (m === 'cmd' || m === 'command' || m === 'meta') mods.meta = true;
  }
  return { mods: mods, key: key };
}

// One-shot check against a KeyboardEvent (for toggle/preventDefault logic).
// Bare-key bindings (no modifier prefix) match regardless of current modifiers.
// Modifier-qualified bindings require exact modifier state.
function mcMatchesEvent(str, e) {
  var parsed = mcParseBoundKey(str);
  if (e.code !== parsed.key) return false;
  var hasModPrefix = parsed.mods.ctrl || parsed.mods.shift || parsed.mods.alt || parsed.mods.meta;
  if (!hasModPrefix) return true;
  return parsed.mods.ctrl  === e.ctrlKey  &&
         parsed.mods.shift === e.shiftKey &&
         parsed.mods.alt   === e.altKey   &&
         parsed.mods.meta  === e.metaKey;
}

// Continuous check for held state (used in the game loop via mcIsActionDown).
function mcIsComboDown(str) {
  var mc = window.__mc;
  var parsed = mcParseBoundKey(str);
  if (!mc.keys[parsed.key]) return false;
  var mods = mc.modifiers;
  return parsed.mods.ctrl  === mods.ctrl  &&
         parsed.mods.shift === mods.shift &&
         parsed.mods.alt   === mods.alt   &&
         parsed.mods.meta  === mods.meta;
}

window.mcLoadBindings = function (host, port) {
  fetch('http://' + host + ':' + port + '/api/keybindings')
    .then(function (r) { return r.json(); })
    .then(function (data) {
      if (window.__mc) window.__mc.bindings = data;
    })
    .catch(function () { /* keep defaults */ });
};

window.mcIsActionDown = function (action) {
  if (!window.__mc) return false;
  var keys = window.__mc.bindings[action];
  if (!keys) return false;
  for (var i = 0; i < keys.length; i++) {
    if (mcIsComboDown(keys[i])) return true;
  }
  return false;
};

window.mcSetupKeyboard = function () {
  window.__mc = window.__mc || {
    keys: {}, modifiers: { ctrl: false, shift: false, alt: false, meta: false },
    flyToggle: false, viewToggle: false, inventoryToggle: false, undoToggle: false, lastSpaceTime: 0
  };
  window.__mc.bindings = MC_DEFAULT_BINDINGS;
  window.addEventListener('keydown', function (e) {
    var tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;
    window.__mc.modifiers = { ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey };
    window.__mc.keys[e.code] = true;
    if (e.repeat) return;
    var b = window.__mc.bindings;
    if (b.fly_toggle && b.fly_toggle.some(function (k) { return mcMatchesEvent(k, e); })) {
      var now = Date.now();
      if (now - window.__mc.lastSpaceTime < 300) window.__mc.flyToggle = true;
      window.__mc.lastSpaceTime = now;
    }
    if (b.view_toggle && b.view_toggle.some(function (k) { return mcMatchesEvent(k, e); })) window.__mc.viewToggle = true;
    if (b.inventory  && b.inventory.some(function (k)   { return mcMatchesEvent(k, e); })) window.__mc.inventoryToggle = true;
    if (b.undo       && b.undo.some(function (k)        { return mcMatchesEvent(k, e); })) window.__mc.undoToggle = true;
    var anyBound = Object.values(b).some(function (keys) {
      return keys.some(function (k) { return mcMatchesEvent(k, e); });
    });
    if (anyBound) e.preventDefault();
  });
  window.addEventListener('keyup', function (e) {
    // On Mac, releasing Cmd swallows keyup for all keys pressed while it was held.
    // Clear all keys unconditionally (before the INPUT guard) to prevent stuck movement.
    if (e.code === 'MetaLeft' || e.code === 'MetaRight') {
      window.__mc.keys = {};
      window.__mc.modifiers = { ctrl: false, shift: false, alt: false, meta: false };
      return;
    }
    var tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;
    window.__mc.modifiers = { ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey };
    window.__mc.keys[e.code] = false;
  });
  // Clear all keys when the window loses focus (Cmd+Tab, browser UI stealing focus, etc.)
  window.addEventListener('blur', function () {
    if (!window.__mc) return;
    window.__mc.keys = {};
    window.__mc.modifiers = { ctrl: false, shift: false, alt: false, meta: false };
  });
};

window.mcConsumeViewToggle = function () {
  if (!window.__mc) return false;
  var v = window.__mc.viewToggle;
  window.__mc.viewToggle = false;
  return v;
};

window.mcConsumeInventoryToggle = function () {
  if (!window.__mc) return false;
  var v = window.__mc.inventoryToggle;
  window.__mc.inventoryToggle = false;
  return v;
};

window.mcConsumeUndoAction = function () {
  if (!window.__mc) return false;
  var v = window.__mc.undoToggle;
  window.__mc.undoToggle = false;
  return v;
};

window.mcToggleHotbar = function () {
  var d = document.getElementById('mc-hotbar');
  if (!d) return;
  d.style.display = d.style.display === 'none' ? 'flex' : 'none';
};

window.mcGetCameraForwardX = function (camera) {
  var d = camera.getForwardRay(1).direction;
  var l = Math.sqrt(d.x * d.x + d.z * d.z) || 1;
  return d.x / l;
};

window.mcGetCameraForwardZ = function (camera) {
  var d = camera.getForwardRay(1).direction;
  var l = Math.sqrt(d.x * d.x + d.z * d.z) || 1;
  return d.z / l;
};

window.mcConsumeFlyToggle = function () {
  if (!window.__mc) return false;
  var v = window.__mc.flyToggle;
  window.__mc.flyToggle = false;
  return v;
};

window.mcSetupMouse = function () {
  window.__mc = window.__mc || { keys: {}, flyToggle: false, lastSpaceTime: 0 };
  window.__mc.mouseLeft = false;
  window.__mc.lastMouseMove = 0;
  window.addEventListener('pointerdown', function (e) {
    if (e.button === 0) window.__mc.mouseLeft = true;
  });
  window.addEventListener('pointerup', function (e) {
    if (e.button === 0) { window.__mc.mouseLeft = false; }
  });
  window.addEventListener('pointermove', function (e) {
    if (e.movementX !== 0 || e.movementY !== 0) window.__mc.lastMouseMove = Date.now();
  });
};

// True only when left button is held AND mouse hasn't moved for 120ms.
window.mcIsBreaking = function () {
  if (!window.__mc || !window.__mc.mouseLeft) return false;
  return (Date.now() - window.__mc.lastMouseMove) > 120;
};

window.mcGetCameraPositionX = function (camera) { return camera.position.x; };
window.mcGetCameraPositionY = function (camera) { return camera.position.y; };
window.mcGetCameraPositionZ = function (camera) { return camera.position.z; };
window.mcGetCameraDir3DX = function (camera) { return camera.getForwardRay(1).direction.x; };
window.mcGetCameraDir3DY = function (camera) { return camera.getForwardRay(1).direction.y; };
window.mcGetCameraDir3DZ = function (camera) { return camera.getForwardRay(1).direction.z; };

window.mcCreateCrosshair = function () {
  var s = document.createElement('div');
  s.id = 'mc-crosshair';
  s.style.cssText = 'position:fixed;top:50%;left:50%;width:20px;height:20px;' +
    'transform:translate(-50%,-50%);pointer-events:none;z-index:100';
  s.innerHTML =
    '<div style="position:absolute;left:50%;top:0;width:2px;height:100%;' +
    'background:#fff;opacity:0.8;transform:translateX(-50%)"></div>' +
    '<div style="position:absolute;top:50%;left:0;height:2px;width:100%;' +
    'background:#fff;opacity:0.8;transform:translateY(-50%)"></div>';
  document.body.appendChild(s);
};

// 12 edges of a unit cube centred at origin, expanded by `h` on each side.
function mcCubeLines(h) {
  var V = function(x,y,z){ return new BABYLON.Vector3(x,y,z); };
  return [
    [V(-h,-h,-h),V( h,-h,-h)], [V( h,-h,-h),V( h,-h, h)],
    [V( h,-h, h),V(-h,-h, h)], [V(-h,-h, h),V(-h,-h,-h)],
    [V(-h, h,-h),V( h, h,-h)], [V( h, h,-h),V( h, h, h)],
    [V( h, h, h),V(-h, h, h)], [V(-h, h, h),V(-h, h,-h)],
    [V(-h,-h,-h),V(-h, h,-h)], [V( h,-h,-h),V( h, h,-h)],
    [V( h,-h, h),V( h, h, h)], [V(-h,-h, h),V(-h, h, h)],
  ];
}

window.mcShowTargetOutline = function (scene, x, y, z, breakable) {
  if (window.__mcTargetMesh) { window.__mcTargetMesh.dispose(); window.__mcTargetMesh = null; }
  var ls = BABYLON.MeshBuilder.CreateLineSystem('targetOutline', { lines: mcCubeLines(0.502) }, scene);
  ls.position = new BABYLON.Vector3(x, y, z);
  ls.color = breakable ? new BABYLON.Color3(0, 0, 0) : new BABYLON.Color3(0.55, 0.55, 0.55);
  ls.isPickable = false;
  window.__mcTargetMesh = ls;
};

window.mcHideTargetOutline = function () {
  if (window.__mcTargetMesh) { window.__mcTargetMesh.dispose(); window.__mcTargetMesh = null; }
};

window.mcShowBreakOverlay = function (scene, x, y, z, alpha) {
  if (!window.__mcBreakMesh || window.__mcBreakMesh._bpos !== (x + ',' + y + ',' + z)) {
    if (window.__mcBreakMesh) { window.__mcBreakMesh.dispose(); }
    var ls = BABYLON.MeshBuilder.CreateLineSystem('breakOverlay', { lines: mcCubeLines(0.51) }, scene);
    ls.position = new BABYLON.Vector3(x, y, z);
    ls.color = new BABYLON.Color3(0, 0, 0);
    ls.isPickable = false;
    ls._bpos = x + ',' + y + ',' + z;
    window.__mcBreakMesh = ls;
  }
  window.__mcBreakMesh.alpha = alpha;
};

window.mcHideBreakOverlay = function () {
  if (window.__mcBreakMesh) { window.__mcBreakMesh.dispose(); window.__mcBreakMesh = null; }
};

window.mcShowDisconnectedOverlay = function (message) {
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
};

window.mcHideDisconnectedOverlay = function () {
  var d = document.getElementById('mc-disconnect');
  if (d) d.style.display = 'none';
};

// ── Login overlay ─────────────────────────────────────────────────────────────

window.__mcLoginResult = '';

window.mcShowLoginOverlay = function () {
  var existing = document.getElementById('mc-login');
  if (existing) { existing.style.display = 'flex'; return; }

  var overlay = document.createElement('div');
  overlay.id = 'mc-login';
  overlay.style.cssText = [
    'position:fixed;inset:0;display:flex;align-items:center;justify-content:center',
    'background:rgba(0,0,0,0.82);z-index:2000'
  ].join(';');

  var box = document.createElement('div');
  box.style.cssText = [
    'background:#1a1a1a;border:1px solid #444;border-radius:8px',
    'padding:32px 40px;min-width:320px;font-family:monospace;color:#eee'
  ].join(';');

  var title = document.createElement('div');
  title.textContent = 'MiCraft';
  title.style.cssText = 'font-size:28px;font-weight:bold;text-align:center;margin-bottom:24px;color:#6af';

  // Step 1: username
  var step1 = document.createElement('div');
  step1.id = 'mc-login-step1';

  var userLabel = document.createElement('label');
  userLabel.textContent = 'Username';
  userLabel.style.cssText = 'display:block;font-size:13px;color:#aaa;margin-bottom:6px';

  var userInput = document.createElement('input');
  userInput.id = 'mc-login-username';
  userInput.type = 'text';
  userInput.placeholder = 'Enter your username';
  userInput.style.cssText = [
    'width:100%;box-sizing:border-box;padding:8px 10px',
    'background:#111;border:1px solid #555;border-radius:4px',
    'color:#eee;font:15px monospace;outline:none'
  ].join(';');

  var continueBtn = document.createElement('button');
  continueBtn.textContent = 'Continue';
  continueBtn.style.cssText = [
    'margin-top:16px;width:100%;padding:10px',
    'background:#4a8fff;border:none;border-radius:4px',
    'color:#fff;font:bold 15px monospace;cursor:pointer'
  ].join(';');

  step1.appendChild(userLabel);
  step1.appendChild(userInput);
  step1.appendChild(continueBtn);

  // Step 2: character picker
  var step2 = document.createElement('div');
  step2.id = 'mc-login-step2';
  step2.style.display = 'none';

  var welcomeText = document.createElement('div');
  welcomeText.id = 'mc-login-welcome';
  welcomeText.style.cssText = 'font-size:14px;color:#aaa;margin-bottom:14px';

  var charList = document.createElement('div');
  charList.id = 'mc-login-charlist';
  charList.style.cssText = 'margin-bottom:12px';

  var newCharRow = document.createElement('div');
  newCharRow.style.cssText = 'display:flex;align-items:center;gap:8px;margin-top:8px';

  var newCharRadio = document.createElement('input');
  newCharRadio.type = 'radio';
  newCharRadio.name = 'mc-char';
  newCharRadio.value = '__new__';
  newCharRadio.id = 'mc-char-new';

  var newCharLabel = document.createElement('label');
  newCharLabel.htmlFor = 'mc-char-new';
  newCharLabel.textContent = '+ New character:';
  newCharLabel.style.cssText = 'font-size:13px;color:#aaa;cursor:pointer;white-space:nowrap';

  var newCharInput = document.createElement('input');
  newCharInput.id = 'mc-login-newchar';
  newCharInput.type = 'text';
  newCharInput.placeholder = 'Character name';
  newCharInput.style.cssText = [
    'flex:1;padding:5px 8px;background:#111;border:1px solid #555',
    'border-radius:4px;color:#eee;font:14px monospace;outline:none'
  ].join(';');

  newCharRow.appendChild(newCharRadio);
  newCharRow.appendChild(newCharLabel);
  newCharRow.appendChild(newCharInput);

  var playBtn = document.createElement('button');
  playBtn.textContent = 'Play';
  playBtn.style.cssText = continueBtn.style.cssText;
  playBtn.style.marginTop = '20px';

  var backBtn = document.createElement('button');
  backBtn.textContent = '← Back';
  backBtn.style.cssText = [
    'margin-top:8px;width:100%;padding:8px',
    'background:transparent;border:1px solid #555;border-radius:4px',
    'color:#aaa;font:14px monospace;cursor:pointer'
  ].join(';');

  step2.appendChild(welcomeText);
  step2.appendChild(charList);
  step2.appendChild(newCharRow);
  step2.appendChild(playBtn);
  step2.appendChild(backBtn);

  box.appendChild(title);
  box.appendChild(step1);
  box.appendChild(step2);
  overlay.appendChild(box);
  document.body.appendChild(overlay);

  function getUsers() {
    try { return JSON.parse(localStorage.getItem('micraft_users') || '{}'); } catch (e) { return {}; }
  }
  function saveUsers(u) {
    try { localStorage.setItem('micraft_users', JSON.stringify(u)); } catch (e) {}
  }

  function getLastPlayer(username) {
    try { return localStorage.getItem('micraft_last_player_' + username) || ''; } catch (e) { return ''; }
  }
  function saveLastPlayer(username, playerName) {
    try { localStorage.setItem('micraft_last_player_' + username, playerName); } catch (e) {}
  }

  function showStep2(username) {
    var users = getUsers();
    var chars = users[username] || [];
    var lastPlayer = getLastPlayer(username);
    step1.style.display = 'none';
    step2.style.display = 'block';
    welcomeText.textContent = 'Welcome, ' + username + '! Choose your character:';

    charList.innerHTML = '';
    var anyChecked = false;
    chars.forEach(function (name, i) {
      var row = document.createElement('div');
      row.style.cssText = 'display:flex;align-items:center;gap:8px;padding:4px 0';
      var radio = document.createElement('input');
      radio.type = 'radio';
      radio.name = 'mc-char';
      radio.value = name;
      radio.id = 'mc-char-' + i;
      if (name === lastPlayer || (!lastPlayer && i === 0)) { radio.checked = true; anyChecked = true; }
      var lbl = document.createElement('label');
      lbl.htmlFor = 'mc-char-' + i;
      lbl.textContent = name;
      lbl.style.cssText = 'font-size:14px;cursor:pointer';
      row.appendChild(radio);
      row.appendChild(lbl);
      charList.appendChild(row);
    });

    if (chars.length === 0) newCharRadio.checked = true;
    newCharInput.value = chars.length === 0 ? username : '';
    newCharInput.addEventListener('focus', function () { newCharRadio.checked = true; });
  }

  var lastUser = '';
  try { lastUser = localStorage.getItem('micraft_last_user') || ''; } catch (e) {}
  if (lastUser) userInput.value = lastUser;
  setTimeout(function () { userInput.focus(); }, 50);

  function doStep1() {
    var username = userInput.value.trim();
    if (!username) { userInput.focus(); return; }
    try { localStorage.setItem('micraft_last_user', username); } catch (e) {}
    showStep2(username);
    setTimeout(function () {
      var first = document.querySelector('input[name="mc-char"]:checked');
      if (first) first.focus(); else newCharInput.focus();
    }, 50);
  }

  continueBtn.addEventListener('click', doStep1);
  userInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') doStep1(); });

  newCharInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') doPlay(); });

  function doPlay() {
    var username = userInput.value.trim();
    var selected = document.querySelector('input[name="mc-char"]:checked');
    var playerName;
    if (!selected || selected.value === '__new__') {
      playerName = newCharInput.value.trim();
      if (!playerName) { newCharInput.focus(); return; }
      var users = getUsers();
      if (!users[username]) users[username] = [];
      if (!users[username].includes(playerName)) users[username].push(playerName);
      saveUsers(users);
    } else {
      playerName = selected.value;
    }
    saveLastPlayer(username, playerName);
    overlay.style.display = 'none';
    window.__mcLoginResult = username + '\t' + playerName;
  }

  playBtn.addEventListener('click', doPlay);
  step2.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      var sel = document.querySelector('input[name="mc-char"]:checked');
      if (sel && sel.value !== '__new__') { e.stopPropagation(); doPlay(); }
    }
  });
  backBtn.addEventListener('click', function () {
    step2.style.display = 'none';
    step1.style.display = 'block';
    setTimeout(function () { userInput.focus(); }, 50);
  });
};

window.mcHideLoginOverlay = function () {
  var d = document.getElementById('mc-login');
  if (d) d.style.display = 'none';
};

window.mcConsumeLoginResult = function () {
  var v = window.__mcLoginResult;
  window.__mcLoginResult = '';
  return v;
};

window.mcReload = function () { window.location.reload(); };

window.mcGetUrlParam = function (name) {
  var v = new URLSearchParams(window.location.search).get(name);
  return v === null ? '' : v;
};

/**
 * Binds keys 1-6 to camera positions facing each face of the block at (bx,by,bz).
 * Face mapping: 1=+Z, 2=-Z, 3=+X, 4=-X, 5=+Y, 6=-Y  (BabylonJS CreateBox order)
 */
window.mcSetupDebugCameraKeys = function (camera, scene, bx, by, bz) {
  var dist = 5;
  var faces = [
    [bx,       by,       bz + dist],
    [bx,       by,       bz - dist],
    [bx + dist, by,      bz       ],
    [bx - dist, by,      bz       ],
    [bx,       by + dist, bz      ],
    [bx,       by - dist, bz      ],
  ];
  var lock = function (px, py, pz) {
    if (window.__debugCamObserver) scene.onBeforeRenderObservable.remove(window.__debugCamObserver);
    window.__debugCamObserver = scene.onBeforeRenderObservable.add(function () {
      camera.position = new BABYLON.Vector3(px, py, pz);
      camera.setTarget(new BABYLON.Vector3(bx, by, bz));
    });
  };
  lock(faces[0][0], faces[0][1], faces[0][2]);
  document.addEventListener('keydown', function (e) {
    var idx = parseInt(e.key) - 1;
    if (idx >= 0 && idx < 6) {
      e.preventDefault();
      var f = faces[idx];
      lock(f[0], f[1], f[2]);
    }
    if (e.key === 'Escape') {
      if (window.__debugCamObserver) {
        scene.onBeforeRenderObservable.remove(window.__debugCamObserver);
        window.__debugCamObserver = null;
      }
    }
  });
};

// ── Chunk geometry builder ────────────────────────────────────────────────────
// Builds one mesh per material group per chunk instead of one mesh per block.
// Reduces draw calls from O(blocks) to O(chunks × materials) ≈ 200 vs 30 000.

window.__mcChunks = {};  // chunkKey → [mesh, ...]
var __mcBuf = null;      // accumulator between mcChunkBegin and mcChunkEnd

// Vertex offsets for each face direction (4 vertices per face, CCW winding)
var MC_VERTS = [
  // 0: Front +Z — normal (0,0,1)
  [[-0.5,-0.5, 0.5],[0.5,-0.5, 0.5],[0.5, 0.5, 0.5],[-0.5, 0.5, 0.5]],
  // 1: Back  -Z — normal (0,0,-1)
  [[ 0.5,-0.5,-0.5],[-0.5,-0.5,-0.5],[-0.5, 0.5,-0.5],[ 0.5, 0.5,-0.5]],
  // 2: Right +X — normal (1,0,0)
  [[ 0.5,-0.5, 0.5],[ 0.5,-0.5,-0.5],[ 0.5, 0.5,-0.5],[ 0.5, 0.5, 0.5]],
  // 3: Left  -X — normal (-1,0,0)
  [[-0.5,-0.5,-0.5],[-0.5,-0.5, 0.5],[-0.5, 0.5, 0.5],[-0.5, 0.5,-0.5]],
  // 4: Top   +Y — normal (0,1,0)
  [[-0.5, 0.5, 0.5],[ 0.5, 0.5, 0.5],[ 0.5, 0.5,-0.5],[-0.5, 0.5,-0.5]],
  // 5: Bottom-Y — normal (0,-1,0)
  [[-0.5,-0.5,-0.5],[ 0.5,-0.5,-0.5],[ 0.5,-0.5, 0.5],[-0.5,-0.5, 0.5]],
];
var MC_NORMS = [[0,0,1],[0,0,-1],[1,0,0],[-1,0,0],[0,1,0],[0,-1,0]];
var MC_UV    = [0,1, 1,1, 1,0, 0,0];  // full texture per face

// BlockType ordinals must match the Kotlin enum order:
// AIR=0, BEDROCK=1, STONE=2, DIRT=3, GRASS=4, SAND=5, SANDSTONE=6, GRAVEL=7, SNOW=8
// faceMat = blockOrdinal * 6 + faceDir (0=+Z,1=-Z,2=+X,3=-X,4=+Y,5=-Y)
function mcMatGroup(faceMat) {
  var faceDir = faceMat % 6;
  var typeOrd = (faceMat - faceDir) / 6;
  if (typeOrd === 4) {  // GRASS — per-face material
    return faceDir === 4 ? 'gt' : faceDir === 5 ? 'gb' : faceDir === 0 ? 'gf' : faceDir === 1 ? 'gbk' : 'gx';
  }
  if (typeOrd === 2) return 's';   // STONE
  if (typeOrd === 3) return 'd';   // DIRT
  if (typeOrd === 5) return 'sa';  // SAND
  if (typeOrd === 6) return 'ss';  // SANDSTONE
  if (typeOrd === 7) return 'gr';  // GRAVEL
  if (typeOrd === 8) return 'sn';  // SNOW
  return 'b';                      // BEDROCK (and fallback)
}

window.mcChunkBegin = function (cx, cz) {
  __mcBuf = { key: cx + ',' + cz, groups: {} };
};

window.mcChunkFace = function (wx, wy, wz, faceMat) {
  var mk  = mcMatGroup(faceMat);
  var fd  = faceMat % 6;
  var grp = __mcBuf.groups;
  if (!grp[mk]) grp[mk] = { p: [], n: [], u: [], i: [], v: 0 };
  var g   = grp[mk];
  var vt  = MC_VERTS[fd];
  var nm  = MC_NORMS[fd];
  for (var k = 0; k < 4; k++) {
    g.p.push(wx + vt[k][0], wy + vt[k][1], wz + vt[k][2]);
    g.n.push(nm[0], nm[1], nm[2]);
    g.u.push(MC_UV[k * 2], MC_UV[k * 2 + 1]);
  }
  var b = g.v; g.i.push(b, b+1, b+2, b, b+2, b+3); g.v += 4;
};

// grassMat is a BABYLON.MultiMaterial; subMaterials = [sideFr, sideBk, sideX, sideX, top, bottom]
window.mcChunkEnd = function (scene, grassMat, stoneMat, dirtMat, bedrockMat,
                               sandMat, sandstoneMat, gravelMat, snowMat) {
  var buf = __mcBuf; __mcBuf = null;
  var key = buf.key;
  mcDisposeChunk(key);
  var gsm = (grassMat && grassMat.subMaterials) ? grassMat.subMaterials : [];
  var matMap = { s: stoneMat, d: dirtMat, b: bedrockMat,
                 gt: gsm[4], gb: gsm[5], gf: gsm[0], gbk: gsm[1], gx: gsm[2],
                 sa: sandMat, ss: sandstoneMat, gr: gravelMat, sn: snowMat };
  var meshes = [];
  Object.keys(buf.groups).forEach(function (mk) {
    var g = buf.groups[mk];
    if (g.v === 0) return;
    var mesh = new BABYLON.Mesh('ck' + key + mk, scene);
    var vd   = new BABYLON.VertexData();
    vd.positions = g.p;  vd.normals = g.n;  vd.uvs = g.u;  vd.indices = g.i;
    vd.applyToMesh(mesh, false);
    mesh.material = matMap[mk] || null;
    mesh.freezeWorldMatrix();
    mesh.isPickable = false;
    mesh.doNotSyncBoundingInfo = true;
    meshes.push(mesh);
  });
  window.__mcChunks[key] = meshes;
};

window.mcDisposeChunk = function (key) {
  var meshes = window.__mcChunks[key];
  if (meshes) { meshes.forEach(function (m) { m.dispose(); }); delete window.__mcChunks[key]; }
};

// ── Console ───────────────────────────────────────────────────────────────────

// ── Command autocomplete registry ─────────────────────────────────────────────

window.__mcConnectedPlayers = [];
window.__mcCommandCompleters = {};
window.__mcKnownCommands = [];

window.mcRegisterCompleter = function (cmd, fn) {
  window.__mcCommandCompleters[cmd] = fn;
  if (!window.__mcKnownCommands.includes(cmd)) window.__mcKnownCommands.push(cmd);
};

window.mcSetConnectedPlayers = function (namesJson) {
  try { window.__mcConnectedPlayers = JSON.parse(namesJson); } catch (e) {}
};

// Built-in registrations (argument completers)
window.mcRegisterCompleter('/keyreload', function () { return []; });
window.mcRegisterCompleter('/kick',  function (p) { return (window.__mcConnectedPlayers || []).filter(function (n) { return n.startsWith(p); }); });
window.mcRegisterCompleter('/save',  function ()  { return []; });
window.mcRegisterCompleter('/who',   function ()  { return []; });
window.mcRegisterCompleter('/yield',      function ()  { return []; });
window.mcRegisterCompleter('/disconnect', function ()  { return []; });

// ── Console ───────────────────────────────────────────────────────────────────

window.__mcConsole = { open: false, submitted: null, history: [], histIdx: -1, playerName: '', tabIdx: -1, tabMatches: [] };

window.mcConsoleSetPlayer = function (name) {
  window.__mcConsole.playerName = name;
  try {
    var stored = localStorage.getItem('mc_history_' + name);
    window.__mcConsole.history = stored ? JSON.parse(stored) : [];
  } catch (e) {
    window.__mcConsole.history = [];
  }
};

window.mcCreateConsole = function () {
  var overlay = document.createElement('div');
  overlay.id = 'mc-console';
  overlay.style.cssText = [
    'display:none;position:fixed;bottom:60px;left:50%;transform:translateX(-50%)',
    'width:60%;background:rgba(0,0,0,0.72);border-radius:4px;padding:4px 8px',
    'z-index:1002;box-sizing:border-box'
  ].join(';');
  var input = document.createElement('input');
  input.id = 'mc-console-input';
  input.type = 'text';
  input.style.cssText = 'width:100%;background:transparent;border:none;color:#fff;font:15px monospace;outline:none;';
  overlay.appendChild(input);
  document.body.appendChild(overlay);

  input.addEventListener('keydown', function (e) {
    e.stopPropagation();
    var c = window.__mcConsole;
    var h = c.history;
    if (e.key === 'Enter') {
      var text = input.value.trim();
      if (text) {
        c.submitted = text;
        if (h.length === 0 || h[h.length - 1] !== text) h.push(text);
        try { localStorage.setItem('mc_history_' + c.playerName, JSON.stringify(h.slice(-50))); } catch (ex) {}
      }
      mcHideConsole();
    } else if (e.key === 'Escape') {
      mcHideConsole();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (h.length > 0) {
        c.histIdx = Math.min(c.histIdx + 1, h.length - 1);
        input.value = h[h.length - 1 - c.histIdx];
        setTimeout(function () { input.setSelectionRange(input.value.length, input.value.length); }, 0);
      }
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      c.histIdx = Math.max(c.histIdx - 1, -1);
      input.value = c.histIdx === -1 ? '/' : h[h.length - 1 - c.histIdx];
      setTimeout(function () { input.setSelectionRange(input.value.length, input.value.length); }, 0);
    } else if (e.key === 'Tab') {
      e.preventDefault();
      var val = input.value;
      if (!val.startsWith('/')) return;
      var spaceIdx = val.indexOf(' ');
      var matches, completed;
      if (spaceIdx === -1) {
        matches = window.__mcKnownCommands.filter(function (cmd) { return cmd.startsWith(val); });
        if (matches.length === 0) return;
        if (matches.length === 1) {
          input.value = matches[0] + ' ';
          c.tabIdx = -1; c.tabMatches = [];
        } else {
          if (c.tabMatches.join('|') !== matches.join('|')) { c.tabIdx = -1; c.tabMatches = matches; }
          c.tabIdx = (c.tabIdx + 1) % c.tabMatches.length;
          input.value = c.tabMatches[c.tabIdx];
        }
      } else {
        var cmd = val.slice(0, spaceIdx);
        var partial = val.slice(spaceIdx + 1);
        var completer = window.__mcCommandCompleters[cmd];
        matches = completer ? completer(partial) : [];
        if (matches.length === 0) return;
        if (matches.length === 1) {
          input.value = cmd + ' ' + matches[0];
          c.tabIdx = -1; c.tabMatches = [];
        } else {
          if (c.tabMatches.join('|') !== matches.join('|')) { c.tabIdx = -1; c.tabMatches = matches; }
          c.tabIdx = (c.tabIdx + 1) % c.tabMatches.length;
          input.value = cmd + ' ' + c.tabMatches[c.tabIdx];
        }
      }
    } else {
      c.tabIdx = -1; c.tabMatches = [];
    }
  });

  document.addEventListener('keydown', function (e) {
    var tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') return;
    var loginEl = document.getElementById('mc-login');
    if (loginEl && loginEl.style.display !== 'none') return;
    if (e.key === '/' && !window.__mcConsole.open) {
      e.preventDefault();
      mcShowConsole();
    } else if (e.key === 'Enter' && !window.__mcConsole.open) {
      e.preventDefault();
      mcShowConsole();
      document.getElementById('mc-console-input').value = '';
    }
  });
};

window.mcShowConsole = function () {
  var overlay = document.getElementById('mc-console');
  var input = document.getElementById('mc-console-input');
  if (!overlay || !input) return;
  window.__mcConsole.open = true;
  window.__mcConsole.submitted = null;
  window.__mcConsole.histIdx = -1;
  overlay.style.display = 'block';
  input.value = '/';
  if (document.pointerLockElement) document.exitPointerLock();
  setTimeout(function () { input.focus(); }, 10);
};

window.mcHideConsole = function () {
  var overlay = document.getElementById('mc-console');
  if (overlay) overlay.style.display = 'none';
  window.__mcConsole.open = false;
};

window.mcIsConsoleOpen = function () {
  return !!(window.__mcConsole && window.__mcConsole.open);
};

window.mcConsumeConsoleInput = function () {
  if (!window.__mcConsole || !window.__mcConsole.submitted) return '';
  var v = window.__mcConsole.submitted;
  window.__mcConsole.submitted = null;
  return v;
};

// ── Server log (history above slash input) ────────────────────────────────────

var __mcServerLog = [];
var MC_LOG_MAX = 10;

function mcEscapeHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

window.mcCreateServerLog = function () {
  var d = document.createElement('div');
  d.id = 'mc-server-log';
  d.style.cssText = [
    'display:none;position:fixed;bottom:94px;left:50%;transform:translateX(-50%)',
    'width:60%;background:rgba(0,0,0,0.55);border-radius:4px 4px 0 0',
    'padding:4px 8px;z-index:1002;box-sizing:border-box;pointer-events:none'
  ].join(';');
  document.body.appendChild(d);
};

window.mcAddServerLog = function (message) {
  var d = document.getElementById('mc-server-log');
  if (!d) return;
  var now = new Date();
  var ts = now.getHours().toString().padStart(2, '0') + ':' +
            now.getMinutes().toString().padStart(2, '0') + ':' +
            now.getSeconds().toString().padStart(2, '0');
  __mcServerLog.push({ time: ts, msg: message });
  if (__mcServerLog.length > MC_LOG_MAX) __mcServerLog.shift();
  d.innerHTML = __mcServerLog.map(function (e) {
    return '<div style="color:#ddd;font:12px/1.6 monospace">' +
           '<span style="color:#888">[' + e.time + ']</span> ' +
           mcEscapeHtml(e.msg) + '</div>';
  }).join('');
  d.style.display = 'block';
};

// ── Notification ──────────────────────────────────────────────────────────────

window.mcShowNotification = function (message) {
  var d = document.getElementById('mc-notification');
  if (!d) {
    d = document.createElement('div');
    d.id = 'mc-notification';
    d.style.cssText = [
      'position:fixed;bottom:100px;left:50%;transform:translateX(-50%)',
      'background:rgba(0,0,0,0.72);color:#fff;font:14px monospace',
      'padding:6px 14px;border-radius:4px;z-index:1001;pointer-events:none'
    ].join(';');
    document.body.appendChild(d);
  }
  d.textContent = message;
  d.style.display = 'block';
  clearTimeout(window.__mcNotifTimeout);
  window.__mcNotifTimeout = setTimeout(function () { d.style.display = 'none'; }, 3000);
};

// ── Hotbar ────────────────────────────────────────────────────────────────────

var HOTBAR_ITEM_META = {
  COBBLESTONE: { label: 'COB', bg: '#7A7A7A' },
  DIRT:        { label: 'DRT', bg: '#8B5A2B' },
  SAND:        { label: 'SND', bg: '#D5C89A' },
  GRAVEL:      { label: 'GRV', bg: '#9A9A9A' },
  SANDSTONE:   { label: 'SST', bg: '#C8B46C' },
  SNOWBALL:    { label: 'SNW', bg: '#DCE8F5' },
  FLINT:       { label: 'FLT', bg: '#4A4A52' },
};

window.mcCreateHotbar = function () {
  var d = document.createElement('div');
  d.id = 'mc-hotbar';
  d.style.cssText = [
    'position:fixed;bottom:20px;left:50%;transform:translateX(-50%)',
    'display:none;gap:4px;pointer-events:none;z-index:998;align-items:center',
    'background:rgba(0,0,0,0.6);border:1px solid rgba(255,255,255,0.2);border-radius:6px;padding:6px 10px;min-width:120px;min-height:68px'
  ].join(';');
  document.body.appendChild(d);
};

window.mcUpdateHotbar = function (inventoryJson) {
  var d = document.getElementById('mc-hotbar');
  if (!d) return;
  var inventory = JSON.parse(inventoryJson);
  d.innerHTML = '';
  var hasItems = false;
  Object.keys(HOTBAR_ITEM_META).forEach(function (type) {
    var count = inventory[type] || 0;
    if (count <= 0) return;
    hasItems = true;
    var meta = HOTBAR_ITEM_META[type];
    var slot = document.createElement('div');
    slot.style.cssText = [
      'width:52px;height:52px',
      'background:rgba(0,0,0,0.72)',
      'border:2px solid rgba(255,255,255,0.45)',
      'border-radius:4px',
      'display:flex;flex-direction:column;align-items:center;justify-content:center',
      'position:relative'
    ].join(';');

    var icon = document.createElement('div');
    icon.style.cssText = [
      'width:26px;height:26px;border-radius:3px',
      'background:' + meta.bg,
      'box-shadow:inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)'
    ].join(';');
    slot.appendChild(icon);

    var label = document.createElement('div');
    label.style.cssText = 'color:rgba(255,255,255,0.7);font:8px monospace;margin-top:3px;letter-spacing:0.5px';
    label.textContent = meta.label;
    slot.appendChild(label);

    var badge = document.createElement('div');
    badge.style.cssText = [
      'position:absolute;bottom:2px;right:4px',
      'color:#fff;font:bold 10px monospace;text-shadow:1px 1px 0 #000'
    ].join(';');
    badge.textContent = count;
    slot.appendChild(badge);

    d.appendChild(slot);
  });

  if (!hasItems) {
    var empty = document.createElement('div');
    empty.style.cssText = 'color:rgba(255,255,255,0.35);font:12px monospace;padding:8px 16px;text-align:center;width:100%';
    empty.textContent = 'Inventaire vide';
    d.appendChild(empty);
  }

  // Auto-show when first items are received
  if (hasItems && d.style.display === 'none') d.style.display = 'flex';
};

// ── HUD ───────────────────────────────────────────────────────────────────────

window.mcCreateHUD = function () {
  var d = document.createElement('div');
  d.id = 'hud';
  d.style.cssText = 'position:fixed;top:12px;right:12px;background:rgba(0,0,0,0.55);color:#fff;font:13px/1.6 monospace;padding:8px 12px;border-radius:6px;pointer-events:none;z-index:999;white-space:pre';
  document.body.appendChild(d);
};

window.mcUpdateHUD = function (x, y, z, yaw, pitch, stance, speed, fps, kbIn, kbOut, biome, targetBlock) {
  var d = document.getElementById('hud');
  if (d) d.textContent =
    'FPS   ' + fps + '\n' +
    'X  ' + x.toFixed(2) + '\n' +
    'Y  ' + y.toFixed(2) + '\n' +
    'Z  ' + z.toFixed(2) + '\n' +
    'Yaw   ' + yaw.toFixed(1) + '°\n' +
    'Pitch ' + pitch.toFixed(1) + '°\n' +
    stance + '\n' +
    'Speed ×' + speed.toFixed(1) + '\n' +
    '↓ ' + kbIn.toFixed(1) + ' KB/s  ↑ ' + kbOut.toFixed(1) + ' KB/s\n' +
    (biome ? 'Biome ' + biome + '\n' : '') +
    (targetBlock ? 'Block ' + targetBlock : '');
};

// ── Player model (bbmodel) ────────────────────────────────────────────────────

window.mcInitPlayerModel = function () {
  window.__mc = window.__mc || {};
  fetch('/models/player.bbmodel')
    .then(function (r) { return r.json(); })
    .then(function (data) {
      window.__mc.playerBbmodel = data;
      console.log('[MiCraft] Player model loaded');
    })
    .catch(function (e) {
      console.error('[MiCraft] Failed to load player model', e);
    });
};

window.mcIsPlayerBbmodelReady = function () {
  return !!(window.__mc && window.__mc.playerBbmodel);
};

window.mcCreatePlayerModelNow = function (scene) {
  return window.mcCreatePlayerModelFromBbmodel(window.__mc.playerBbmodel, scene);
};

// ── Shared skin UV helpers ────────────────────────────────────────────────────
// Pixel coords [x0,y0,x1,y1] → BabylonJS Vector4(uMin,vMin,uMax,vMax).
// BabylonJS loads textures with invertY so pixel y=0 maps to v=1.
window.__mcSkinUV = function (face, W, H) {
  if (!face || !face.uv) return new BABYLON.Vector4(0, 0, 0, 0);
  var x0 = face.uv[0], y0 = face.uv[1], x1 = face.uv[2], y1 = face.uv[3];
  return new BABYLON.Vector4(
    Math.min(x0, x1) / W, 1 - Math.max(y0, y1) / H,
    Math.max(x0, x1) / W, 1 - Math.min(y0, y1) / H
  );
};
// BabylonJS CreateBox face order: 0=front(+Z/south), 1=back(-Z/north),
// 2=right(+X/east), 3=left(-X/west), 4=top(+Y), 5=bottom(-Y)
window.__mcSkinFaceUV = function (faces, W, H) {
  var uv = window.__mcSkinUV;
  return [uv(faces.south,W,H), uv(faces.north,W,H), uv(faces.east,W,H),
          uv(faces.west,W,H),  uv(faces.up,W,H),    uv(faces.down,W,H)];
};

window.mcCreatePlayerModelFromBbmodel = function (bbmodel, scene) {
  // Shared material (one per page load, reused for every player instance)
  if (!window.__mcPlayerMat) {
    var src = bbmodel.textures[0].source;  // data:image/png;base64,...
    var tex = new BABYLON.Texture(src, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
    tex.hasAlpha = false;
    tex.wrapU = BABYLON.Texture.CLAMP_ADDRESSMODE;
    tex.wrapV = BABYLON.Texture.CLAMP_ADDRESSMODE;
    var mat = new BABYLON.StandardMaterial('playerSkinMat', scene);
    mat.diffuseTexture = tex;
    mat.specularColor = new BABYLON.Color3(0, 0, 0);
    window.__mcPlayerMat = mat;
  }
  var mat = window.__mcPlayerMat;

  var W = bbmodel.resolution.width;   // 64
  var H = bbmodel.resolution.height;  // 64
  var SCALE = 1 / 16;

  // Build uuid→group map
  var groupMap = {};
  bbmodel.groups.forEach(function (g) { groupMap[g.uuid] = g; });

  // Groups that need animated pivot nodes (head=pitch+bob, limbs=walk swing)
  var ANIM_GROUPS = ['head', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg'];

  // Walk the outliner to map each element UUID to its nearest animated ancestor group
  var elToGroup = {};
  function walkOutliner(nodes, animAncestor) {
    if (!nodes) return;
    nodes.forEach(function (node) {
      if (typeof node === 'string') {
        elToGroup[node] = animAncestor;
        return;
      }
      var g = groupMap[node.uuid];
      var gname = g ? g.name : null;
      var next = (gname && ANIM_GROUPS.indexOf(gname) >= 0) ? gname : animAncestor;
      walkOutliner(node.children, next);
    });
  }
  walkOutliner(bbmodel.outliner, null);

  var root = new BABYLON.TransformNode('playerRoot', scene);

  // Create one pivot TransformNode per animated group, parented to root
  var pivotNodes = {};
  ANIM_GROUPS.forEach(function (gname) {
    var g = bbmodel.groups.find(function (gr) { return gr.name === gname; });
    if (!g) return;
    var node = new BABYLON.TransformNode('player_' + gname, scene);
    node.parent = root;
    node.position = new BABYLON.Vector3(
      g.origin[0] * SCALE,
      g.origin[1] * SCALE,
      g.origin[2] * SCALE
    );
    pivotNodes[gname] = { node: node, origin: g.origin };
  });

  var getFaceUV = function (faces) { return window.__mcSkinFaceUV(faces, W, H); };

  bbmodel.elements.forEach(function (el) {
    var fx = el.from[0], fy = el.from[1], fz = el.from[2];
    var tx = el.to[0],   ty = el.to[1],   tz = el.to[2];

    var mesh = BABYLON.MeshBuilder.CreateBox(el.name, {
      width:  Math.abs(tx - fx) * SCALE,
      height: Math.abs(ty - fy) * SCALE,
      depth:  Math.abs(tz - fz) * SCALE,
      faceUV: getFaceUV(el.faces)
    }, scene);
    mesh.material = mat;
    mesh.isPickable = false;

    var cx = (fx + tx) / 2 * SCALE;
    var cy = (fy + ty) / 2 * SCALE;
    var cz = (fz + tz) / 2 * SCALE;

    var gname = elToGroup[el.uuid];
    var pg = gname ? pivotNodes[gname] : null;

    if (pg) {
      mesh.parent = pg.node;
      mesh.position = new BABYLON.Vector3(
        cx - pg.origin[0] * SCALE,
        cy - pg.origin[1] * SCALE,
        cz - pg.origin[2] * SCALE
      );
    } else {
      mesh.parent = root;
      mesh.position = new BABYLON.Vector3(cx, cy, cz);
    }
  });

  // Parse the walk animation keyframes from the bbmodel so mcSetPlayerTransform
  // interpolates the actual authored values rather than hard-coded constants.
  // Result: { boneName: { channel: [ {time, x, y, z}, … ] } }
  var walkAnim = {};
  // Prefer an animation whose name contains 'walk'; fall back to the first animation
  // that has leg or arm keyframes (the main locomotion animation regardless of name).
  var walkAnimDef = bbmodel.animations && (
    bbmodel.animations.find(function (a) { return a.name && a.name.toLowerCase().indexOf('walk') >= 0; }) ||
    bbmodel.animations.find(function (a) { return a.animators && Object.keys(a.animators).length > 1; })
  );
  if (walkAnimDef) {
    var animLength = walkAnimDef.length || 1;
    // Build a name→uuid map from bbmodel.groups
    var nameToUuid = {};
    bbmodel.groups.forEach(function (g) { nameToUuid[g.name] = g.uuid; });

    ANIM_GROUPS.concat(['head']).forEach(function (bname) {
      var uuid = nameToUuid[bname];
      if (!uuid) return;
      var animator = walkAnimDef.animators[uuid];
      if (!animator) return;
      // Collect rotation keyframes, sorted by time
      var kfs = (animator.keyframes || []).filter(function (k) { return k.channel === 'rotation'; });
      kfs.sort(function (a, b) { return a.time - b.time; });
      if (kfs.length === 0) return;
      walkAnim[bname] = { keyframes: kfs, length: animLength };
    });
  }

  return {
    root: root,
    headNode: pivotNodes['head'] ? pivotNodes['head'].node : null,
    pivotNodes: pivotNodes,
    walkAnim: walkAnim
  };
};

// Linear interpolation between two bbmodel keyframes at normalised time t ∈ [0,1].
function mcInterpAxis(keyframes, t, axis) {
  if (!keyframes || keyframes.length === 0) return 0;
  if (keyframes.length === 1) return parseFloat(keyframes[0].data_points[0][axis] || 0);
  // Find surrounding keyframes
  var prev = keyframes[0], next = keyframes[keyframes.length - 1];
  for (var i = 0; i < keyframes.length - 1; i++) {
    if (t >= keyframes[i].time && t <= keyframes[i + 1].time) {
      prev = keyframes[i]; next = keyframes[i + 1]; break;
    }
  }
  if (prev === next) return parseFloat(prev.data_points[0][axis] || 0);
  var span = next.time - prev.time;
  var f = span <= 0 ? 0 : (t - prev.time) / span;
  var v0 = parseFloat(prev.data_points[0][axis] || 0);
  var v1 = parseFloat(next.data_points[0][axis] || 0);
  return v0 + (v1 - v0) * f;
}

// x,y,z = player feet in world space; yaw from camera.rotation.y; headPitch from camera.rotation.x
// isWalking = true triggers the walk animation driven by the bbmodel keyframes.
window.mcSetPlayerTransform = function (model, x, y, z, yaw, headPitch, isWalking) {
  model.root.position.x = x;
  model.root.position.y = y;
  model.root.position.z = z;
  model.root.rotation.y = yaw + Math.PI;

  var pn = model.pivotNodes;
  if (!pn) return;

  var DEG = Math.PI / 180;
  var headPivot = pn['head'] ? pn['head'].node : null;
  var wa = model.walkAnim || {};

  if (isWalking) {
    var animLen = (wa['rightArm'] && wa['rightArm'].length) || 1;
    var t = (Date.now() % (animLen * 1000)) / (animLen * 1000);

    ['rightArm', 'leftArm', 'rightLeg', 'leftLeg'].forEach(function (bname) {
      if (!pn[bname]) return;
      var bone = wa[bname];
      var rx = bone ? mcInterpAxis(bone.keyframes, t, 'x') : 0;
      pn[bname].node.rotation.x = rx * DEG;
    });

    if (headPivot) {
      var headBone = wa['head'];
      var hrx = headBone ? mcInterpAxis(headBone.keyframes, t, 'x') : 0;
      var hry = headBone ? mcInterpAxis(headBone.keyframes, t, 'y') : 0;
      headPivot.rotation.x = headPitch + hrx * DEG;
      headPivot.rotation.y = hry * DEG;
    }
  } else {
    ['rightArm', 'leftArm', 'rightLeg', 'leftLeg'].forEach(function (bname) {
      if (pn[bname]) pn[bname].node.rotation.x = 0;
    });
    if (headPivot) {
      headPivot.rotation.x = headPitch;
      headPivot.rotation.y = 0;
    }
  }
};

window.mcSetPlayerVisible = function (model, visible) {
  model.root.setEnabled(visible);
};

window.mcSetPlayerAlpha = function (model, alpha) {
  model.root.getChildMeshes(true).forEach(function (m) { m.visibility = alpha; });
};

window.mcDisposePlayerModel = function (model) {
  model.root.getChildMeshes(true).forEach(function (m) { m.dispose(); });
  if (model.pivotNodes) {
    Object.keys(model.pivotNodes).forEach(function (k) { model.pivotNodes[k].node.dispose(); });
  } else if (model.headNode) {
    model.headNode.dispose();
  }
  model.root.dispose();
};

// ── First-person arm view model ───────────────────────────────────────────────
// Arm pivots are parented directly to the camera so BabylonJS handles the
// camera-local transform automatically. Local +Z = camera forward in BabylonJS.

window.mcCreateFPArms = function (scene, camera) {
  var bbmodel = window.__mc && window.__mc.playerBbmodel;
  var mat = window.__mcPlayerMat;
  if (!bbmodel || !mat) {
    console.warn('[MiCraft] mcCreateFPArms: bbmodel or material not ready');
    return null;
  }

  var W = bbmodel.resolution.width;
  var H = bbmodel.resolution.height;
  var SCALE = 1 / 16;

  // Build group map to derive arm pivot positions from the bbmodel skeleton
  var groupMap = {};
  bbmodel.groups.forEach(function (g) { groupMap[g.name] = g; });
  var headGroup = groupMap['head'];

  var armEls = {};
  bbmodel.elements.forEach(function (el) {
    if (el.name === 'rightArm' || el.name === 'leftArm') armEls[el.name] = el;
  });

  var pivots = [];
  var meshes = [];

  ['rightArm', 'leftArm'].forEach(function (name) {
    var el = armEls[name];
    if (!el) { console.warn('[MiCraft] FP arms: missing element', name); return; }

    // Pivot position in camera-local space (+X=right, +Y=up, +Z=forward).
    // X and Y are derived from the arm group origin relative to the head center
    // in the bbmodel, so changing the skeleton propagates here automatically.
    // Z (forward depth) stays a fixed constant — the bbmodel has no meaningful Z offset.
    var armGroup = groupMap[name];
    var px, py;
    if (armGroup && headGroup) {
      px = (armGroup.origin[0] - headGroup.origin[0]) * SCALE;
      py = (armGroup.origin[1] - headGroup.origin[1]) * SCALE;
    } else {
      var sign = (name === 'rightArm') ? 1 : -1;
      px = sign * 0.25;
      py = -0.125;
    }
    var pz = 0.45;

    var pivot = new BABYLON.TransformNode('fp_' + name, scene);
    pivot.parent = camera;
    pivot.position = new BABYLON.Vector3(px, py, pz);

    var fx = el.from[0], fy = el.from[1], fz = el.from[2];
    var tx = el.to[0],   ty = el.to[1],   tz = el.to[2];
    var mesh = BABYLON.MeshBuilder.CreateBox(name + '_fp', {
      width:  Math.abs(tx - fx) * SCALE,
      height: Math.abs(ty - fy) * SCALE,
      depth:  Math.abs(tz - fz) * SCALE,
      faceUV: window.__mcSkinFaceUV(el.faces, W, H)
    }, scene);
    mesh.material = mat;
    mesh.isPickable = false;
    mesh.alwaysSelectAsActiveMesh = true;  // bypass frustum culling for camera-parented mesh
    mesh.parent = pivot;
    // Arm hangs below the shoulder pivot (half arm height = 6 units = 0.375)
    mesh.position = new BABYLON.Vector3(0, -0.375, 0);

    pivots.push({ node: pivot, name: name });
    meshes.push(mesh);
  });

  // Extract walk animation keyframes for the arms from the bbmodel
  var walkAnim = {};
  var walkAnimDef = bbmodel.animations && (
    bbmodel.animations.find(function (a) { return a.name && a.name.toLowerCase().indexOf('walk') >= 0; }) ||
    bbmodel.animations.find(function (a) { return a.animators && Object.keys(a.animators).length > 1; })
  );
  if (walkAnimDef) {
    var animLength = walkAnimDef.length || 1;
    var nameToUuid = {};
    bbmodel.groups.forEach(function (g) { nameToUuid[g.name] = g.uuid; });
    ['rightArm', 'leftArm'].forEach(function (bname) {
      var uuid = nameToUuid[bname];
      if (!uuid) return;
      var animator = walkAnimDef.animators[uuid];
      if (!animator) return;
      var kfs = (animator.keyframes || []).filter(function (k) { return k.channel === 'rotation'; });
      kfs.sort(function (a, b) { return a.time - b.time; });
      if (kfs.length > 0) walkAnim[bname] = { keyframes: kfs, length: animLength };
    });
  }

  // Start visible so they appear immediately on first render
  meshes.forEach(function (m) { m.isVisible = true; });
  console.log('[MiCraft] FP arms created (' + pivots.length + ' arms)');
  var result = { pivots: pivots, meshes: meshes, walkAnim: walkAnim };
  window.__mcCurrentFPArms = result;
  return result;
};

window.mcUpdateFPArms = function (fpArms, isWalking) {
  if (!fpArms) return;
  var DEG = Math.PI / 180;
  var wa = fpArms.walkAnim || {};
  fpArms.pivots.forEach(function (p) {
    if (!isWalking) { p.node.rotation.x = 0; return; }
    var bone = wa[p.name];
    if (bone) {
      var animLen = bone.length || 1;
      var t = (Date.now() % (animLen * 1000)) / (animLen * 1000);
      p.node.rotation.x = mcInterpAxis(bone.keyframes, t, 'x') * DEG;
    } else {
      p.node.rotation.x = 0;
    }
  });
};

window.mcSetFPArmsVisible = function (fpArms, visible) {
  if (!fpArms) return;
  fpArms.meshes.forEach(function (m) { m.isVisible = visible; });
};

window.mcDisposeFPArms = function (fpArms) {
  if (!fpArms) return;
  fpArms.meshes.forEach(function (m) { m.dispose(); });
  fpArms.pivots.forEach(function (p) { p.node.dispose(); });
  if (window.__mcCurrentFPArms === fpArms) window.__mcCurrentFPArms = null;
};

// ── Debug helper (browser console) ───────────────────────────────────────────
// Adjust first-person arm pivot positions live:
//   mcDebugFPArms(x, y, z)
//     x  = horizontal offset from camera centre (right arm = +x, left = -x)
//     y  = vertical offset from camera centre   (positive = above centre)
//     z  = forward distance                      (positive = in front)
// Example: mcDebugFPArms(0.25, 0.0, 0.45)
window.mcDebugFPArms = function (x, y, z) {
  var fa = window.__mcCurrentFPArms;
  if (!fa) { console.warn('[MiCraft] No FP arms active'); return; }
  fa.pivots.forEach(function (p) {
    var sign = (p.name === 'rightArm') ? 1 : -1;
    if (x !== undefined) p.node.position.x = sign * Math.abs(x);
    if (y !== undefined) p.node.position.y = y;
    if (z !== undefined) p.node.position.z = z;
  });
  var p0 = fa.pivots[0] ? fa.pivots[0].node.position : null;
  if (p0) console.log('[MiCraft] FP arm pivot → x=±' + Math.abs(p0.x).toFixed(4) + '  y=' + p0.y.toFixed(4) + '  z=' + p0.z.toFixed(4));
};
