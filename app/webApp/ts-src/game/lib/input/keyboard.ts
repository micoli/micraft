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

function hasModPrefix(str: string): boolean {
  const { mods } = parseBoundKey(str);
  return mods.ctrl || mods.shift || mods.alt || mods.meta;
}

// Resolves which bound actions fire for this keydown, generically arbitrating bare vs
// modifier-qualified bindings that share the same physical key — e.g. combat_attack (KeyR) and
// siege_weapon_power (Ctrl+KeyR): matchesEvent alone would fire both on Ctrl+R since bare
// bindings match regardless of held modifiers. Any action with a qualified match wins; actions
// that only match bare are dropped whenever some other action matched with modifiers. This is
// action-name-agnostic — no binding pair needs to be hardcoded here.
function resolveEventActions(b: Record<string, string[]>, e: KeyboardEvent): Set<string> {
  const qualifiedMatch: Record<string, boolean> = {};
  const matchedActions: string[] = [];
  let anyQualifiedMatch = false;
  for (const [action, keys] of Object.entries(b)) {
    let matched = false;
    let qualified = false;
    for (const k of keys) {
      if (isSequenceBinding(k) || !matchesEvent(k, e)) continue;
      matched = true;
      if (hasModPrefix(k)) qualified = true;
    }
    if (!matched) continue;
    matchedActions.push(action);
    qualifiedMatch[action] = qualified;
    if (qualified) anyQualifiedMatch = true;
  }
  return new Set(matchedActions.filter((action) => !anyQualifiedMatch || qualifiedMatch[action]));
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

        const matched = resolveEventActions(b, e);

        if (matched.has("view_toggle")) window.mcState.events.push("view_toggle");
        if (matched.has("console_toggle")) window.mc?.toggleConsole?.();
        if (matched.has("inventory")) window.mcState.events.push("inventory");
        if (matched.has("undo")) window.mcState.events.push("undo");
        if (matched.has("layout_editor")) window.mc?.showLayoutEditor?.();
        if (matched.has("character")) window.mc?.openCharacter?.();
        if (matched.has("craft")) window.mc?.openCraft?.();
        if (matched.has("dump_stats")) window.mc?.dumpStats?.();
        if (matched.has("health_bar")) window.mc?.toggleHealthBar?.();
        if (matched.has("statistics_toggle")) window.mc?.toggleStatistics?.();
        if (matched.has("chunk_debug_toggle")) window.mc?.toggleChunkDebug?.();
        if (matched.has("attack_panel_toggle")) window.mc?.toggleAttackPanel?.();
        if (matched.has("preferences")) window.mc?.showPreferences?.();
        if (matched.has("preferences_keybindings")) window.mc?.showPreferences?.("keybindings");
        if (matched.has("preferences_debug")) window.mc?.showPreferences?.("debug");
        if (matched.has("preferences_graphics")) window.mc?.showPreferences?.("graphics");
        if (matched.has("minimap_zoom_in")) window.mc?.minimapZoomIn?.();
        if (matched.has("minimap_zoom_out")) window.mc?.minimapZoomOut?.();
        if (matched.has("ingame_map")) window.mc?.IngameMap?.();
        if (matched.has("fly_toggle")) window.mcState.events.push("fly_toggle");
        if (matched.has("auto_forward")) window.mcState.events.push("auto_forward");
        if (matched.has("place_rotate")) {
          // While a scene ghost is active, R rotates the scene preview instead of the FPS
          // hotbar placement ghost — these two placement modes are mutually exclusive.
          if (window.mcState.sceneGhostActive) window.mc?.sceneRotate?.();
          else window.mcState.events.push("place_rotate");
        }
        if (matched.has("block_interact")) window.mcState.events.push("block_interact");
        if (matched.has("claim_mark_corner")) window.mc?.claimMarkCorner?.();
        if (matched.has("claim_cancel_selection")) window.mc?.claimCancelSelection?.();
        if (matched.has("claim_panel")) window.mc?.toggleClaimPanel?.();
        if (matched.has("scene_confirm") && window.mcState.sceneGhostActive) {
          window.mc?.sceneConfirm?.();
        }
        if (matched.has("scene_cancel") && window.mcState.sceneGhostActive) {
          window.mc?.sceneCancel?.();
        }
        if (matched.has("combat_target_cycle")) window.mcState.events.push("combat_target_cycle");
        if (matched.has("vehicle_mount")) window.mcState.events.push("vehicle_mount");
        if (matched.has("npc_interact")) window.mcState.events.push("npc_interact");
        if (matched.has("siege_weapon_pitch")) window.mcState.events.push("siege_weapon_pitch");
        if (matched.has("siege_weapon_power")) window.mcState.events.push("siege_weapon_power");
        if (matched.has("combat_attack")) window.mcState.events.push("combat_attack");
        if (matched.has("siege_weapon_rotate")) window.mcState.events.push("siege_weapon_rotate");
        if (matched.has("siege_weapon_fire")) window.mcState.events.push("siege_weapon_fire");
        if (matched.has("screenshot")) window.mcState.events.push("screenshot");
        if (matched.has("quest_journal")) window.mc?.openQuestJournal?.();
        if (matched.has("quest_tracking")) window.mc?.toggleQuestTracker?.();
        const pageActionMatched = Array.from({ length: 12 }, (_, i) =>
          i < 10 ? `shortcut_page_${i + 1}` : i === 10 ? "shortcut_page_prev" : "shortcut_page_next",
        ).some((action) => matched.has(action));
        if (!pageActionMatched) {
          for (let s = 1; s <= 10; s++) {
            const key = `slot_${s}`;
            if (matched.has(key)) window.mcState.events.push(key);
          }
        }
        if (matched.size > 0) e.preventDefault();

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
