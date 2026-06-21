// @ts-nocheck
// mc_bindings.js — BabylonJS host functions called from Kotlin/Wasm via js()
// Must be loaded AFTER babylon.js and BEFORE webApp.js.
import { registerUtils } from './utils/utils';
import { registerEngine } from './engine/engine';
import { registerMaterials } from './materials/materials';
import { registerKeyboard } from './input/keyboard';
import { registerMouse } from './input/mouse';
import { registerCamera } from './camera/camera';
import { registerTargeting } from './targeting/targeting';
import { registerChunks } from './chunks/chunkBuilder';
import { registerPlayerModel } from './player/playerModel';
import { registerFPArms } from './player/fpArms';

registerUtils();
registerEngine();
registerMaterials();
registerKeyboard();
registerMouse();
registerCamera();
registerTargeting();
registerChunks();
registerPlayerModel();
registerFPArms();

// ── UI overlays (Phase 3: will move to Kotlin/Compose) ────────────────────────

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
      window.mcHideConsole();
    } else if (e.key === 'Escape') {
      window.mcHideConsole();
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
      window.mcShowConsole();
    } else if (e.key === 'Enter' && !window.__mcConsole.open) {
      e.preventDefault();
      window.mcShowConsole();
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

// ── Server log ────────────────────────────────────────────────────────────────

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
