interface ParsedKey {
  mods: { ctrl: boolean; shift: boolean; alt: boolean; meta: boolean };
  key: string;
}

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
export function matchesEvent(str: string, e: KeyboardEvent): boolean {
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
  const lkp = window.mcState.lastKeyPress;
  if (!lkp) return false;
  const prevMatch = /^[A-Z]/.test(prev) ? lkp.code === prev : lkp.key?.toLowerCase() === prev.toLowerCase();
  return prevMatch && Date.now() - lkp.time < 300;
}

// Continuous held-state check (called per-frame via isActionDown).
function isComboDown(str: string): boolean {
  if (isSequenceBinding(str)) return false;
  const mc = window.mcState;
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

export function registerKeyboard(): Pick<
  McBindings,
  "loadBindings" | "isActionDown" | "setupKeyboard" | "consumeEvents"
> {
  return {
    loadBindings: (host: string, port: number, player: string): void => {
      // Explicit host:port (passed in from Kotlin/Wasm) rather than same-origin — the generated
      // client has no per-call baseUrl override wired up, so this stays a manual fetch.
      const url = player
        ? `http://${host}:${port}/api/keybindings?player=${encodeURIComponent(player)}`
        : `http://${host}:${port}/api/keybindings`;
      fetch(url)
        .then((r) => r.json())
        .then((data) => {
          if (window.mcState) window.mcState.bindings = data;
        })
        .catch(() => {
          /* keep defaults */
        });
    },

    isActionDown: (action: string): boolean => {
      const keys = window.mcState.bindings[action];
      if (!keys) return false;
      return keys.some((k) => isComboDown(k));
    },

    setupKeyboard: (): void => {
      window.mcState.bindings = {};
      window.mcState.macros = {};

      window.mcRunMacro = (name: string): void => {
        if (window.mcState?.events !== undefined) {
          window.mcState.events.push("macro:" + name);
        }
      };

      window.addEventListener("keydown", (e: KeyboardEvent) => {
        const tag = document.activeElement?.tagName;
        if (
          tag === "INPUT" ||
          tag === "TEXTAREA" ||
          tag === "BUTTON" ||
          tag === "SELECT" ||
          (document.activeElement as HTMLElement | null)?.isContentEditable
        )
          return;
        if (window.mcState.modalOpen) return;
        // Prevent browser quickfind (Firefox) — GameUI document listener fires first and opens console
        if (e.key === "/" || e.code === "Slash") e.preventDefault();
        // Prevent Tab from cycling focus and releasing pointer lock
        if (e.code === "Tab") e.preventDefault();
        window.mcState.modifiers = { ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey };
        window.mcState.keys[e.code] = true;
        if (e.repeat) return;
        const b = window.mcState.bindings;

        // Sequence bindings: check before updating lastKeyPress so prevKey is the truly previous press.
        for (const [action, keys] of Object.entries(b)) {
          if (keys.some((k) => isSequenceBinding(k) && matchesSequence(k, e))) {
            window.mcState.events.push(action);
          }
        }

        window.mcState.lastKeyPress = { code: e.code, key: e.key, time: Date.now() };

        if (b.view_toggle?.some((k) => matchesEvent(k, e))) window.mcState.events.push("view_toggle");
        if (b.console_toggle?.some((k) => matchesEvent(k, e))) window.mc?.toggleConsole?.();
        if (b.inventory?.some((k) => matchesEvent(k, e))) window.mcState.events.push("inventory");
        if (b.undo?.some((k) => matchesEvent(k, e))) window.mcState.events.push("undo");
        if (b.layout_editor?.some((k) => matchesEvent(k, e))) window.mc?.showLayoutEditor?.();
        if (b.character?.some((k) => matchesEvent(k, e))) window.mc?.openCharacter?.();
        if (b.craft?.some((k) => matchesEvent(k, e))) window.mc?.openCraft?.();
        if (b.dump_stats?.some((k) => matchesEvent(k, e))) window.mc?.dumpStats?.();
        if (b.health_bar?.some((k) => matchesEvent(k, e))) window.mc?.toggleHealthBar?.();
        if (b.statistics_toggle?.some((k) => matchesEvent(k, e))) window.mc?.toggleStatistics?.();
        if (b.attack_panel_toggle?.some((k) => matchesEvent(k, e))) window.mc?.toggleAttackPanel?.();
        if (b.preferences?.some((k) => matchesEvent(k, e))) window.mc?.showPreferences?.();
        if (b.preferences_keybindings?.some((k) => matchesEvent(k, e))) window.mc?.showPreferences?.("keybindings");
        if (b.preferences_debug?.some((k) => matchesEvent(k, e))) window.mc?.showPreferences?.("debug");
        if (b.preferences_graphics?.some((k) => matchesEvent(k, e))) window.mc?.showPreferences?.("graphics");
        if (b.minimap_zoom_in?.some((k) => matchesEvent(k, e))) window.mc?.minimapZoomIn?.();
        if (b.minimap_zoom_out?.some((k) => matchesEvent(k, e))) window.mc?.minimapZoomOut?.();
        if (b.ingame_map?.some((k) => matchesEvent(k, e))) window.mc?.IngameMap?.();
        if (b.fly_toggle?.some((k) => matchesEvent(k, e))) window.mcState.events.push("fly_toggle");
        if (b.auto_forward?.some((k) => matchesEvent(k, e))) window.mcState.events.push("auto_forward");
        if (b.place_rotate?.some((k) => matchesEvent(k, e))) window.mcState.events.push("place_rotate");
        if (b.block_interact?.some((k) => matchesEvent(k, e))) window.mcState.events.push("block_interact");
        if (b.combat_target_cycle?.some((k) => matchesEvent(k, e))) window.mcState.events.push("combat_target_cycle");
        if (b.combat_attack?.some((k) => matchesEvent(k, e))) window.mcState.events.push("combat_attack");
        if (b.npc_interact?.some((k) => matchesEvent(k, e))) window.mcState.events.push("npc_interact");
        if (b.screenshot?.some((k) => matchesEvent(k, e))) window.mcState.events.push("screenshot");
        if (b.quest_journal?.some((k) => matchesEvent(k, e))) window.mc?.openQuestJournal?.();
        if (b.quest_tracking?.some((k) => matchesEvent(k, e))) window.mc?.toggleQuestTracker?.();
        const pageActionMatched = Array.from({ length: 12 }, (_, i) =>
          i < 10 ? `shortcut_page_${i + 1}` : i === 10 ? "shortcut_page_prev" : "shortcut_page_next",
        ).some((action) => b[action]?.some((k: string) => matchesEvent(k, e)));
        if (!pageActionMatched) {
          for (let s = 1; s <= 10; s++) {
            const key = `slot_${s}` as string;
            if (b[key]?.some((k: string) => matchesEvent(k, e))) window.mcState.events.push(key);
          }
        }
        if (Object.values(b).some((keys) => keys.some((k) => matchesEvent(k, e)))) e.preventDefault();

        for (const [cmdText, keys] of Object.entries(window.mcState.customCommands || {})) {
          if (keys.some((k) => (isSequenceBinding(k) ? matchesSequence(k, e) : matchesEvent(k, e)))) {
            window.mcState.events.push(cmdText.startsWith("macro:") ? cmdText : "cmd:" + cmdText);
            e.preventDefault();
          }
        }
      });

      window.addEventListener("keyup", (e: KeyboardEvent) => {
        // On Mac, releasing Cmd swallows keyup for all held keys — clear all to prevent stuck movement.
        if (e.code === "MetaLeft" || e.code === "MetaRight") {
          window.mcState.keys = {};
          window.mcState.modifiers = { ctrl: false, shift: false, alt: false, meta: false };
          return;
        }
        const tag = document.activeElement?.tagName;
        if (
          tag === "INPUT" ||
          tag === "TEXTAREA" ||
          tag === "BUTTON" ||
          tag === "SELECT" ||
          (document.activeElement as HTMLElement | null)?.isContentEditable
        )
          return;
        if (window.mcState.modalOpen) return;
        window.mcState.modifiers = { ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey, meta: e.metaKey };
        window.mcState.keys[e.code] = false;
      });

      // Clear keys when window loses focus (Cmd+Tab, browser UI, etc.)
      window.addEventListener("blur", () => {
        window.mcState.keys = {};
        window.mcState.modifiers = { ctrl: false, shift: false, alt: false, meta: false };
      });
    },

    consumeEvents: (): string[] => {
      const e = window.mcState.events;
      window.mcState.events = [];
      return e;
    },
  };
}
