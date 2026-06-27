interface ParsedKey {
  mods: { ctrl: boolean; shift: boolean; alt: boolean; meta: boolean };
  key: string;
}

const MC_DEFAULT_BINDINGS: Record<string, string[]> = {
  forward: ["KeyW", "ArrowUp"],
  backward: ["KeyS", "ArrowDown"],
  strafe_right: ["KeyD", "ArrowRight"],
  strafe_left: ["KeyA", "ArrowLeft"],
  rotate_left: ["KeyQ"],
  rotate_right: ["KeyE"],
  sneak: ["ShiftLeft"],
  crawl: ["ControlLeft"],
  fly_toggle: ["Space+Space"],
  ascend: ["Space"],
  descend: ["ShiftLeft"],
  speed_up: ["KeyP"],
  speed_down: ["KeyO"],
  view_toggle: ["KeyF"],
  hud_mode_cycle: ["KeyH"],
  inventory: ["KeyI"],
  undo: ["Ctrl+KeyZ", "Cmd+KeyZ"],
  auto_forward: ["KeyW+KeyW", "ArrowUp+ArrowUp"],
  minimap_zoom_in: ["l"],
  minimap_zoom_out: ["m"],
  layout_editor: ["KeyG"],
  slot_1: ["Digit1"],
  slot_2: ["Digit2"],
  slot_3: ["Digit3"],
  slot_4: ["Digit4"],
  slot_5: ["Digit5"],
  slot_6: ["Digit6"],
  slot_7: ["Digit7"],
  slot_8: ["Digit8"],
  slot_9: ["Digit9"],
  slot_10: ["Digit0"],
};

const MODIFIERS = new Set(["ctrl", "control", "shift", "alt", "option", "cmd", "command", "meta"]);

// Returns true if any non-last part is not a known modifier — i.e. it's a key sequence, not a combo.
// "Ctrl+KeyZ" → false (combo), "KeyW+KeyW" → true (sequence), "KeyA+KeyS" → true (sequence).
function isSequenceBinding(str: string): boolean {
  const parts = str.split("+");
  if (parts.length < 2) return false;
  return parts.slice(0, -1).some((p) => !MODIFIERS.has(p.toLowerCase()));
}

// Parse "Ctrl+Shift+KeyZ" → { mods: {ctrl,shift,alt,meta}, key: "KeyZ" }
function parseBoundKey(str: string): ParsedKey {
  const parts = str.split("+");
  const key = parts[parts.length - 1];
  const mods = { ctrl: false, shift: false, alt: false, meta: false };
  for (let i = 0; i < parts.length - 1; i++) {
    const m = parts[i].toLowerCase();
    if (m === "ctrl" || m === "control") mods.ctrl = true;
    else if (m === "shift") mods.shift = true;
    else if (m === "alt" || m === "option") mods.alt = true;
    else if (m === "cmd" || m === "command" || m === "meta") mods.meta = true;
  }
  return { mods, key };
}

// One-shot check against a KeyboardEvent.
// Bare-key bindings match regardless of current modifiers;
// modifier-qualified bindings require exact modifier state.
// Key names starting with an uppercase letter (KeyW, Space, ShiftLeft…) match e.code (physical
// position, layout-independent). Lowercase names (m, l…) match e.key (produced character,
// layout-aware — works correctly on AZERTY, Dvorak, etc.).
function matchesEvent(str: string, e: KeyboardEvent): boolean {
  if (isSequenceBinding(str)) return false;
  const parsed = parseBoundKey(str);
  const keyMatch = /^[A-Z]/.test(parsed.key) ? e.code === parsed.key : e.key.toLowerCase() === parsed.key.toLowerCase();
  if (!keyMatch) return false;
  const hasModPrefix = parsed.mods.ctrl || parsed.mods.shift || parsed.mods.alt || parsed.mods.meta;
  if (!hasModPrefix) return true;
  return (
    parsed.mods.ctrl === e.ctrlKey &&
    parsed.mods.shift === e.shiftKey &&
    parsed.mods.alt === e.altKey &&
    parsed.mods.meta === e.metaKey
  );
}

// Checks whether a 2-key sequence binding matches: last key = current event, previous key pressed
// within 300 ms. Only supports 2-key sequences.
function matchesSequence(str: string, e: KeyboardEvent): boolean {
  const parts = str.split("+");
  if (parts.length !== 2) return false;
  const [prev, last] = parts;
  const lastMatch = /^[A-Z]/.test(last) ? e.code === last : e.key.toLowerCase() === last.toLowerCase();
  if (!lastMatch) return false;
  const lkp = window.__mc.lastKeyPress;
  if (!lkp) return false;
  const prevMatch = /^[A-Z]/.test(prev) ? lkp.code === prev : lkp.key?.toLowerCase() === prev.toLowerCase();
  return prevMatch && Date.now() - lkp.time < 300;
}

// Continuous held-state check (called per-frame via mcIsActionDown).
function isComboDown(str: string): boolean {
  if (isSequenceBinding(str)) return false;
  const mc = window.__mc;
  const parsed = parseBoundKey(str);
  if (!mc.keys[parsed.key]) return false;
  const hasModPrefix = parsed.mods.ctrl || parsed.mods.shift || parsed.mods.alt || parsed.mods.meta;
  if (!hasModPrefix) return true;
  const mods = mc.modifiers;
  return (
    parsed.mods.ctrl === mods.ctrl &&
    parsed.mods.shift === mods.shift &&
    parsed.mods.alt === mods.alt &&
    parsed.mods.meta === mods.meta
  );
}

export function registerKeyboard(): void {
  window.mcLoadBindings = (host: string, port: number, player: string): void => {
    const url = player
      ? `http://${host}:${port}/api/keybindings?player=${encodeURIComponent(player)}`
      : `http://${host}:${port}/api/keybindings`;
    fetch(url)
      .then((r) => r.json())
      .then((data) => {
        if (window.__mc) window.__mc.bindings = data;
      })
      .catch(() => {
        /* keep defaults */
      });
  };

  window.mcIsActionDown = (action: string): boolean => {
    if (!window.__mc) return false;
    const keys = window.__mc.bindings[action];
    if (!keys) return false;
    return keys.some((k) => isComboDown(k));
  };

  window.mcSetupKeyboard = (): void => {
    window.__mc = window.__mc || {
      keys: {},
      modifiers: { ctrl: false, shift: false, alt: false, meta: false },
      events: [],
      lastKeyPress: null,
      mouseLeft: false,
      lastMouseMove: 0,
      bindings: {},
      customCommands: {},
      playerBbmodel: null,
    };
    window.__mc.bindings = MC_DEFAULT_BINDINGS;

    window.addEventListener("keydown", (e: KeyboardEvent) => {
      const tag = document.activeElement?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "BUTTON" || tag === "SELECT") return;
      if (window.__mc.modalOpen) return;
      window.__mc.modifiers = { ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey };
      window.__mc.keys[e.code] = true;
      if (e.repeat) return;
      const b = window.__mc.bindings;

      // Sequence bindings: check before updating lastKeyPress so prevKey is the truly previous press.
      for (const [action, keys] of Object.entries(b)) {
        if (keys.some((k) => isSequenceBinding(k) && matchesSequence(k, e))) {
          window.__mc.events.push(action);
        }
      }

      window.__mc.lastKeyPress = { code: e.code, key: e.key, time: Date.now() };

      if (b.view_toggle?.some((k) => matchesEvent(k, e))) window.__mc.events.push("view_toggle");
      if (b.hud_mode_cycle?.some((k) => matchesEvent(k, e))) (window as any).mcCycleHudMode?.();
      if (b.inventory?.some((k) => matchesEvent(k, e))) window.__mc.events.push("inventory");
      if (b.undo?.some((k) => matchesEvent(k, e))) window.__mc.events.push("undo");
      if (b.layout_editor?.some((k) => matchesEvent(k, e))) (window as any).mcShowLayoutEditor?.();
      if (b.preferences?.some((k) => matchesEvent(k, e))) (window as any).mcShowPreferences?.();
      if (b.minimap_zoom_in?.some((k) => matchesEvent(k, e))) (window as any).mcMinimapZoomIn?.();
      if (b.minimap_zoom_out?.some((k) => matchesEvent(k, e))) (window as any).mcMinimapZoomOut?.();
      for (let s = 1; s <= 10; s++) {
        const key = `slot_${s}` as string;
        if (b[key]?.some((k: string) => matchesEvent(k, e))) window.__mc.events.push(key);
      }
      if (Object.values(b).some((keys) => keys.some((k) => matchesEvent(k, e)))) e.preventDefault();

      for (const [cmdText, keys] of Object.entries(window.__mc.customCommands || {})) {
        if (keys.some((k) => (isSequenceBinding(k) ? matchesSequence(k, e) : matchesEvent(k, e)))) {
          window.__mc.events.push("cmd:" + cmdText);
          e.preventDefault();
        }
      }
    });

    window.addEventListener("keyup", (e: KeyboardEvent) => {
      // On Mac, releasing Cmd swallows keyup for all held keys — clear all to prevent stuck movement.
      if (e.code === "MetaLeft" || e.code === "MetaRight") {
        window.__mc.keys = {};
        window.__mc.modifiers = { ctrl: false, shift: false, alt: false, meta: false };
        return;
      }
      const tag = document.activeElement?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "BUTTON" || tag === "SELECT") return;
      if (window.__mc.modalOpen) return;
      window.__mc.modifiers = { ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey };
      window.__mc.keys[e.code] = false;
    });

    // Clear keys when window loses focus (Cmd+Tab, browser UI, etc.)
    window.addEventListener("blur", () => {
      if (!window.__mc) return;
      window.__mc.keys = {};
      window.__mc.modifiers = { ctrl: false, shift: false, alt: false, meta: false };
    });
  };

  window.mcConsumeEvents = (): string[] => {
    if (!window.__mc) return [];
    const e = window.__mc.events;
    window.__mc.events = [];
    return e;
  };
}
