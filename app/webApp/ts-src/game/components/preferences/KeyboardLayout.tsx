import { useEffect, useState } from "react";
import { cn } from "../../../primitives/cn";
import { CustomCmdEntry } from "../../hooks/usePreferences";

const MODIFIER_TOKENS = new Set(["Ctrl", "Control", "Alt", "AltGraph", "Meta", "Shift"]);

const KEY_UNIT = "1.9rem";
const STORAGE_KEY = "mc.keyboardLayout";

interface KeyDef {
  code: string;
  w?: number;
}

const MAIN_ROWS: KeyDef[][] = [
  [
    { code: "Escape", w: 1.4 },
    { code: "F1" },
    { code: "F2" },
    { code: "F3" },
    { code: "F4" },
    { code: "F5" },
    { code: "F6" },
    { code: "F7" },
    { code: "F8" },
    { code: "F9" },
    { code: "F10" },
    { code: "F11" },
    { code: "F12" },
  ],
  [
    { code: "Backquote" },
    { code: "Digit1" },
    { code: "Digit2" },
    { code: "Digit3" },
    { code: "Digit4" },
    { code: "Digit5" },
    { code: "Digit6" },
    { code: "Digit7" },
    { code: "Digit8" },
    { code: "Digit9" },
    { code: "Digit0" },
    { code: "Minus" },
    { code: "Equal" },
    { code: "Backspace", w: 2 },
  ],
  [
    { code: "Tab", w: 1.5 },
    { code: "KeyQ" },
    { code: "KeyW" },
    { code: "KeyE" },
    { code: "KeyR" },
    { code: "KeyT" },
    { code: "KeyY" },
    { code: "KeyU" },
    { code: "KeyI" },
    { code: "KeyO" },
    { code: "KeyP" },
    { code: "BracketLeft" },
    { code: "BracketRight" },
    { code: "Backslash", w: 1.5 },
  ],
  [
    { code: "CapsLock", w: 1.75 },
    { code: "KeyA" },
    { code: "KeyS" },
    { code: "KeyD" },
    { code: "KeyF" },
    { code: "KeyG" },
    { code: "KeyH" },
    { code: "KeyJ" },
    { code: "KeyK" },
    { code: "KeyL" },
    { code: "Semicolon" },
    { code: "Quote" },
    { code: "Enter", w: 2.25 },
  ],
  [
    { code: "ShiftLeft", w: 2.25 },
    { code: "KeyZ" },
    { code: "KeyX" },
    { code: "KeyC" },
    { code: "KeyV" },
    { code: "KeyB" },
    { code: "KeyN" },
    { code: "KeyM" },
    { code: "Comma" },
    { code: "Period" },
    { code: "Slash" },
    { code: "ShiftRight", w: 2.75 },
  ],
  [
    { code: "ControlLeft", w: 1.4 },
    { code: "MetaLeft", w: 1.2 },
    { code: "AltLeft", w: 1.2 },
    { code: "Space", w: 6.5 },
    { code: "AltRight", w: 1.2 },
    { code: "MetaRight", w: 1.2 },
    { code: "ContextMenu", w: 1.2 },
    { code: "ControlRight", w: 1.4 },
  ],
];

const NAV_ROWS: KeyDef[][] = [
  [{ code: "Insert" }, { code: "Home" }, { code: "PageUp" }],
  [{ code: "Delete" }, { code: "End" }, { code: "PageDown" }],
  [{ code: "" }, { code: "ArrowUp" }, { code: "" }],
  [{ code: "ArrowLeft" }, { code: "ArrowDown" }, { code: "ArrowRight" }],
];

const FALLBACK_LABELS: Record<string, string> = {
  Escape: "Esc",
  Backquote: "`",
  Minus: "-",
  Equal: "=",
  Backspace: "⌫",
  Tab: "Tab",
  BracketLeft: "[",
  BracketRight: "]",
  Backslash: "\\",
  CapsLock: "Caps",
  Semicolon: ";",
  Quote: "'",
  Enter: "⏎",
  ShiftLeft: "⇧",
  ShiftRight: "⇧",
  Comma: ",",
  Period: ".",
  Slash: "/",
  ControlLeft: "Ctrl",
  ControlRight: "Ctrl",
  MetaLeft: "Meta",
  MetaRight: "Meta",
  AltLeft: "Alt",
  AltRight: "AltGr",
  ContextMenu: "☰",
  Space: "Space",
  ArrowUp: "↑",
  ArrowDown: "↓",
  ArrowLeft: "←",
  ArrowRight: "→",
  Insert: "Ins",
  Delete: "Del",
  Home: "Home",
  End: "End",
  PageUp: "PgUp",
  PageDown: "PgDn",
};

// Per-layout character overrides keyed by physical KeyboardEvent.code (unshifted glyph).
const LAYOUT_OVERRIDES: Record<string, Record<string, string>> = {
  qwerty: {},
  qwertz: {
    KeyY: "Z",
    KeyZ: "Y",
    Minus: "ß",
    Equal: "´",
    BracketLeft: "Ü",
    BracketRight: "+",
    Semicolon: "Ö",
    Quote: "Ä",
    Backquote: "^",
    Backslash: "#",
    Slash: "-",
  },
  azerty: {
    Backquote: "²",
    Digit1: "&",
    Digit2: "é",
    Digit3: '"',
    Digit4: "'",
    Digit5: "(",
    Digit6: "-",
    Digit7: "è",
    Digit8: "_",
    Digit9: "ç",
    Digit0: "à",
    Minus: ")",
    Equal: "=",
    KeyQ: "A",
    KeyW: "Z",
    BracketLeft: "^",
    BracketRight: "$",
    KeyA: "Q",
    Semicolon: "M",
    Quote: "ù",
    Backslash: "*",
    KeyZ: "W",
    KeyM: ",",
    Comma: ";",
    Period: ":",
    Slash: "!",
  },
  dvorak: {
    Minus: "[",
    Equal: "]",
    KeyQ: "'",
    KeyW: ",",
    KeyE: ".",
    KeyR: "P",
    KeyT: "Y",
    KeyY: "F",
    KeyU: "G",
    KeyI: "C",
    KeyO: "R",
    KeyP: "L",
    BracketLeft: "/",
    BracketRight: "=",
    KeyS: "O",
    KeyD: "E",
    KeyF: "U",
    KeyG: "I",
    KeyH: "D",
    KeyJ: "H",
    KeyK: "T",
    KeyL: "N",
    Semicolon: "S",
    Quote: "-",
    KeyZ: ";",
    KeyX: "Q",
    KeyC: "J",
    KeyV: "K",
    KeyB: "X",
    KeyN: "B",
    Comma: "W",
    Period: "V",
    Slash: "Z",
  },
  colemak: {
    KeyE: "F",
    KeyR: "P",
    KeyT: "G",
    KeyY: "J",
    KeyU: "L",
    KeyI: "U",
    KeyO: "Y",
    KeyP: ";",
    KeyS: "R",
    KeyD: "S",
    KeyF: "T",
    KeyG: "D",
    KeyJ: "N",
    KeyK: "E",
    KeyL: "I",
    Semicolon: "O",
    KeyN: "K",
  },
};

const LAYOUT_OPTIONS: { value: string; label: string }[] = [
  { value: "auto", label: "Auto (browser)" },
  { value: "qwerty", label: "QWERTY (US)" },
  { value: "qwertz", label: "QWERTZ (DE)" },
  { value: "azerty", label: "AZERTY (FR)" },
  { value: "dvorak", label: "Dvorak" },
  { value: "colemak", label: "Colemak" },
];

function defaultLabel(code: string): string {
  if (FALLBACK_LABELS[code]) return FALLBACK_LABELS[code];
  if (code.startsWith("Key")) return code.slice(3);
  if (code.startsWith("Digit")) return code.slice(5);
  return code;
}

function codesOf(binding: string): string[] {
  return binding.split("+").filter((t) => t && !MODIFIER_TOKENS.has(t));
}

function readStoredLayout(): string {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    if (v && (v === "auto" || v in LAYOUT_OVERRIDES)) return v;
  } catch {
    /* private mode / blocked storage */
  }
  return "auto";
}

type LayoutMap = Map<string, string>;

interface NavigatorKeyboard {
  keyboard?: { getLayoutMap(): Promise<LayoutMap> };
}

export function KeyboardLayout({
  bindings,
  customCmds,
  className,
}: {
  bindings: Record<string, string[]>;
  customCmds: CustomCmdEntry[];
  className?: string;
}) {
  const [layout, setLayout] = useState<string>(readStoredLayout);
  const [layoutMap, setLayoutMap] = useState<LayoutMap | null>(null);
  const [detected, setDetected] = useState(false);

  useEffect(() => {
    const kb = (navigator as Navigator & NavigatorKeyboard).keyboard;
    if (!kb?.getLayoutMap) return;
    let cancelled = false;
    kb.getLayoutMap()
      .then((map) => {
        if (cancelled) return;
        setLayoutMap(map);
        setDetected(true);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  const changeLayout = (value: string) => {
    setLayout(value);
    try {
      localStorage.setItem(STORAGE_KEY, value);
    } catch {
      /* ignore */
    }
  };

  const assigned = new Map<string, string[]>();
  const register = (label: string, keys: string[]) => {
    for (const binding of keys) {
      for (const code of codesOf(binding)) {
        if (!assigned.has(code)) assigned.set(code, []);
        const list = assigned.get(code)!;
        if (!list.includes(label)) list.push(label);
      }
    }
  };
  for (const [action, keys] of Object.entries(bindings)) register(action.replace(/_/g, " "), keys);
  for (const entry of customCmds) if (entry.text.trim()) register(entry.text.trim(), entry.keys);

  const useBrowserMap = layout === "auto" && layoutMap;
  const overrides = layout === "auto" ? {} : (LAYOUT_OVERRIDES[layout] ?? {});
  const label = (code: string) => {
    if (useBrowserMap) return layoutMap!.get(code)?.toUpperCase() || defaultLabel(code);
    return overrides[code]?.toUpperCase() || defaultLabel(code);
  };

  const caption =
    layout === "auto"
      ? detected
        ? "browser-detected"
        : "US fallback"
      : LAYOUT_OPTIONS.find((o) => o.value === layout)?.label;

  const renderRow = (row: KeyDef[], rowIdx: number) => (
    <div key={rowIdx} className="flex gap-0.5">
      {row.map((k, i) => {
        if (!k.code) {
          return <div key={i} style={{ width: KEY_UNIT }} />;
        }
        const actions = assigned.get(k.code);
        const isAssigned = !!actions?.length;
        return (
          <div
            key={i}
            title={isAssigned ? `${k.code}\n${actions!.join("\n")}` : `${k.code} — available`}
            style={{ width: `calc(${KEY_UNIT} * ${k.w ?? 1})`, height: KEY_UNIT }}
            className={cn(
              "flex items-center justify-center rounded-sm border text-[10px] leading-none select-none overflow-hidden",
              isAssigned ? "bg-sky-900/70 border-sky-500 text-sky-200" : "bg-[#2a2a2a] border-[#3d3d3d] text-[#666]",
            )}
          >
            {label(k.code)}
          </div>
        );
      })}
    </div>
  );

  return (
    <div className={cn("shrink-0 font-mono", className)}>
      <div className="flex items-center gap-2 py-1.5">
        <span className="text-[#888] text-[11px] uppercase tracking-wide">Layout</span>
        <select
          value={layout}
          onChange={(e) => changeLayout(e.target.value)}
          className="bg-[#2a2a2a] border border-[#555] rounded-sm text-xs text-[#eee] px-1.5 py-0.5 font-mono outline-none"
        >
          {LAYOUT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <span className="text-[10px] text-[#666]">{caption}</span>
      </div>
      <div className="overflow-x-auto">
        <div className="flex flex-col gap-0.5 w-max">{MAIN_ROWS.map(renderRow)}</div>
        <div className="flex flex-col gap-0.5 w-max mt-2">{NAV_ROWS.map(renderRow)}</div>
      </div>
      <div className="flex items-center gap-3 mt-2 text-[10px] text-[#888]">
        <span className="flex items-center gap-1">
          <span className="inline-block w-3 h-3 rounded-sm bg-sky-900/70 border border-sky-500" /> assigned
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block w-3 h-3 rounded-sm bg-[#2a2a2a] border border-[#3d3d3d]" /> available
        </span>
      </div>
    </div>
  );
}
