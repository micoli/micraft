interface ParsedKey {
  mods: { ctrl: boolean; shift: boolean; alt: boolean; meta: boolean };
  key: string;
}

const MC_DEFAULT_BINDINGS: Record<string, string[]> = {
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
function parseBoundKey(str: string): ParsedKey {
  const parts = str.split('+');
  const key = parts[parts.length - 1];
  const mods = { ctrl: false, shift: false, alt: false, meta: false };
  for (let i = 0; i < parts.length - 1; i++) {
    const m = parts[i].toLowerCase();
    if (m === 'ctrl' || m === 'control')               mods.ctrl  = true;
    else if (m === 'shift')                             mods.shift = true;
    else if (m === 'alt' || m === 'option')             mods.alt   = true;
    else if (m === 'cmd' || m === 'command' || m === 'meta') mods.meta = true;
  }
  return { mods, key };
}

// One-shot check against a KeyboardEvent.
// Bare-key bindings match regardless of current modifiers;
// modifier-qualified bindings require exact modifier state.
function matchesEvent(str: string, e: KeyboardEvent): boolean {
  const parsed = parseBoundKey(str);
  if (e.code !== parsed.key) return false;
  const hasModPrefix = parsed.mods.ctrl || parsed.mods.shift || parsed.mods.alt || parsed.mods.meta;
  if (!hasModPrefix) return true;
  return parsed.mods.ctrl  === e.ctrlKey  &&
         parsed.mods.shift === e.shiftKey &&
         parsed.mods.alt   === e.altKey   &&
         parsed.mods.meta  === e.metaKey;
}

// Continuous held-state check (called per-frame via mcIsActionDown).
function isComboDown(str: string): boolean {
  const mc = window.__mc;
  const parsed = parseBoundKey(str);
  if (!mc.keys[parsed.key]) return false;
  const mods = mc.modifiers;
  return parsed.mods.ctrl  === mods.ctrl  &&
         parsed.mods.shift === mods.shift &&
         parsed.mods.alt   === mods.alt   &&
         parsed.mods.meta  === mods.meta;
}

export function registerKeyboard(): void {
  window.mcLoadBindings = (host: string, port: number): void => {
    fetch(`http://${host}:${port}/api/keybindings`)
      .then(r => r.json())
      .then(data => { if (window.__mc) window.__mc.bindings = data; })
      .catch(() => { /* keep defaults */ });
  };

  window.mcIsActionDown = (action: string): boolean => {
    if (!window.__mc) return false;
    const keys = window.__mc.bindings[action];
    if (!keys) return false;
    return keys.some(k => isComboDown(k));
  };

  window.mcSetupKeyboard = (): void => {
    window.__mc = window.__mc || {
      keys: {}, modifiers: { ctrl: false, shift: false, alt: false, meta: false },
      flyToggle: false, viewToggle: false, inventoryToggle: false, undoToggle: false,
      lastSpaceTime: 0, mouseLeft: false, lastMouseMove: 0, bindings: {}, playerBbmodel: null,
    };
    window.__mc.bindings = MC_DEFAULT_BINDINGS;

    window.addEventListener('keydown', (e: KeyboardEvent) => {
      const tag = document.activeElement?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA') return;
      window.__mc.modifiers = { ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey };
      window.__mc.keys[e.code] = true;
      if (e.repeat) return;
      const b = window.__mc.bindings;
      if (b.fly_toggle?.some(k => matchesEvent(k, e))) {
        const now = Date.now();
        if (now - window.__mc.lastSpaceTime < 300) window.__mc.flyToggle = true;
        window.__mc.lastSpaceTime = now;
      }
      if (b.view_toggle?.some(k => matchesEvent(k, e))) window.__mc.viewToggle = true;
      if (b.inventory?.some(k  => matchesEvent(k, e))) window.__mc.inventoryToggle = true;
      if (b.undo?.some(k       => matchesEvent(k, e))) window.__mc.undoToggle = true;
      if (Object.values(b).some(keys => keys.some(k => matchesEvent(k, e)))) e.preventDefault();
    });

    window.addEventListener('keyup', (e: KeyboardEvent) => {
      // On Mac, releasing Cmd swallows keyup for all held keys — clear all to prevent stuck movement.
      if (e.code === 'MetaLeft' || e.code === 'MetaRight') {
        window.__mc.keys = {};
        window.__mc.modifiers = { ctrl: false, shift: false, alt: false, meta: false };
        return;
      }
      const tag = document.activeElement?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA') return;
      window.__mc.modifiers = { ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey };
      window.__mc.keys[e.code] = false;
    });

    // Clear keys when window loses focus (Cmd+Tab, browser UI, etc.)
    window.addEventListener('blur', () => {
      if (!window.__mc) return;
      window.__mc.keys = {};
      window.__mc.modifiers = { ctrl: false, shift: false, alt: false, meta: false };
    });
  };

  window.mcConsumeViewToggle = (): boolean => {
    if (!window.__mc) return false;
    const v = window.__mc.viewToggle;
    window.__mc.viewToggle = false;
    return v;
  };

  window.mcConsumeInventoryToggle = (): boolean => {
    if (!window.__mc) return false;
    const v = window.__mc.inventoryToggle;
    window.__mc.inventoryToggle = false;
    return v;
  };

  window.mcConsumeUndoAction = (): boolean => {
    if (!window.__mc) return false;
    const v = window.__mc.undoToggle;
    window.__mc.undoToggle = false;
    return v;
  };

  window.mcConsumeFlyToggle = (): boolean => {
    if (!window.__mc) return false;
    const v = window.__mc.flyToggle;
    window.__mc.flyToggle = false;
    return v;
  };

  // mcToggleHotbar stays here until Phase 3 moves it to Compose
  window.mcToggleHotbar = (): void => {
    const d = document.getElementById('mc-hotbar');
    if (!d) return;
    d.style.display = d.style.display === 'none' ? 'flex' : 'none';
  };
}
