import { useState, useRef, useEffect, useLayoutEffect } from "react";
import { EditorState } from "@codemirror/state";
import { EditorView, keymap } from "@codemirror/view";
import { defaultKeymap } from "@codemirror/commands";
import { javascript } from "@codemirror/lang-javascript";
import { syntaxHighlighting, HighlightStyle } from "@codemirror/language";
import { autocompletion, completionKeymap } from "@codemirror/autocomplete";
import type { CompletionContext, CompletionResult } from "@codemirror/autocomplete";
import { tags } from "@lezer/highlight";
import { Dialog, DialogContent, DialogTitle } from "../../primitives/Dialog";
import { Button } from "../../primitives/Button";
import type { CommandInfo } from "../types";
import { useAttackDrag } from "../hooks/useAttackDrag";

const PINNED_KEY = "__pinned_macros__";
const DEFAULT_MACRO_ICON = "⚡";
const MACRO_ICON_PALETTE = [
  "⚡",
  "🗡️",
  "🛡️",
  "🔥",
  "❄️",
  "⚔️",
  "🏃",
  "💊",
  "🎯",
  "🔮",
  "💥",
  "⚗️",
  "🌀",
  "🦅",
  "🐉",
  "💀",
  "🌟",
  "⚙️",
  "🎆",
  "🩹",
];

const jexlHighlightStyle = HighlightStyle.define([
  { tag: tags.keyword, color: "#c792ea" },
  { tag: tags.string, color: "#c3e88d" },
  { tag: tags.number, color: "#f78c6c" },
  { tag: [tags.comment, tags.lineComment, tags.blockComment], color: "#546e7a", fontStyle: "italic" },
  { tag: tags.bool, color: "#ff5370" },
  { tag: tags.null, color: "#ff5370" },
  { tag: tags.operator, color: "#89ddff" },
  { tag: tags.punctuation, color: "#89ddff" },
  { tag: tags.function(tags.variableName), color: "#82aaff" },
  { tag: tags.variableName, color: "#86efac" },
  { tag: tags.propertyName, color: "#f07178" },
]);

const jexlTheme = EditorView.theme(
  {
    "&": { backgroundColor: "#0e0e0e", color: "#86efac", fontSize: "12px", fontFamily: "monospace", height: "100%" },
    ".cm-scroller": { overflow: "auto" },
    ".cm-content": { padding: "8px 12px", caretColor: "#86efac" },
    ".cm-gutters": { backgroundColor: "#0a0a0a", border: "none", color: "#555", paddingRight: "4px" },
    ".cm-activeLineGutter": { backgroundColor: "#161616" },
    ".cm-activeLine": { backgroundColor: "#161616" },
    ".cm-selectionBackground, ::selection": { backgroundColor: "#2d5a2d !important" },
    ".cm-cursor": { borderLeftColor: "#86efac" },
    ".cm-tooltip": { backgroundColor: "#1e1e1e", border: "1px solid #444", borderRadius: "4px" },
    ".cm-tooltip-autocomplete": { backgroundColor: "#1e1e1e" },
    ".cm-tooltip-autocomplete ul li": { padding: "3px 8px", color: "#ccc" },
    ".cm-tooltip-autocomplete ul li[aria-selected]": { backgroundColor: "#1d4a2d", color: "#86efac" },
    ".cm-completionLabel": { fontFamily: "monospace", fontSize: "11px" },
    ".cm-completionDetail": { color: "#888", fontSize: "10px", marginLeft: "8px" },
  },
  { dark: true },
);

interface JexlEditorProps {
  value: string;
  onChange: (v: string) => void;
  commands: CommandInfo[];
  attackKeys: string[];
}

type MacroContextVar = { name: string; type: string; children?: string[] };

function JexlEditor({ value, onChange, commands, attackKeys }: JexlEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const onChangeRef = useRef(onChange);
  const commandsRef = useRef(commands);
  const attackKeysRef = useRef(attackKeys);
  const contextVarsRef = useRef<MacroContextVar[]>([]);

  useEffect(() => {
    onChangeRef.current = onChange;
  });
  useEffect(() => {
    commandsRef.current = commands;
  }, [commands]);
  useEffect(() => {
    attackKeysRef.current = attackKeys;
  }, [attackKeys]);

  useEffect(() => {
    fetch("/api/macros/context")
      .then((r) => r.json())
      .then((data: MacroContextVar[]) => {
        contextVarsRef.current = data;
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!containerRef.current) return;

    const completionSource = (context: CompletionContext): CompletionResult | null => {
      const text = context.state.doc.sliceString(0, context.pos);
      const sendMatch = text.match(/send\(["']([^"']*)$/);
      if (sendMatch) {
        const partial = sendMatch[1];
        return {
          from: context.pos - partial.length,
          options: commandsRef.current
            .filter((c) => c.command.startsWith(partial))
            .map((c) => ({ label: c.command, detail: c.description, type: "function" })),
        };
      }
      const actionMatch = text.match(/action\(["']([^"']*)$/);
      if (actionMatch) {
        const partial = actionMatch[1];
        return {
          from: context.pos - partial.length,
          options: attackKeysRef.current
            .filter((k) => k.startsWith(partial))
            .map((k) => ({ label: k, type: "keyword" })),
        };
      }
      const positionPropMatch = text.match(/(\w+)\.(\w*)$/);
      if (positionPropMatch) {
        const [, parentName, partial] = positionPropMatch;
        const parent = contextVarsRef.current.find((v) => v.name === parentName);
        const children = parent?.children ?? [];
        if (children.length > 0) {
          return {
            from: context.pos - partial.length,
            options: children
              .filter((p) => p.startsWith(partial))
              .map((p) => ({ label: p, detail: "Float", type: "variable" })),
          };
        }
      }
      const varMatch = text.match(/(?:^|[^.\w])(\w*)$/);
      if (varMatch) {
        const partial = varMatch[1];
        if (partial.length === 0 && !context.explicit) return null;
        const options = contextVarsRef.current
          .filter((v) => v.name.startsWith(partial))
          .map((v) => ({ label: v.name, detail: v.type, type: "variable" }));
        if (options.length === 0) return null;
        return { from: context.pos - partial.length, options };
      }
      return null;
    };

    const state = EditorState.create({
      doc: value,
      extensions: [
        javascript(),
        syntaxHighlighting(jexlHighlightStyle),
        autocompletion({ override: [completionSource] }),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) onChangeRef.current(update.state.doc.toString());
        }),
        keymap.of([...completionKeymap, ...defaultKeymap]),
        jexlTheme,
        EditorView.lineWrapping,
      ],
    });

    const view = new EditorView({ state, parent: containerRef.current });
    viewRef.current = view;
    return () => {
      view.destroy();
      viewRef.current = null;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    const current = view.state.doc.toString();
    if (current !== value) {
      view.dispatch({ changes: { from: 0, to: current.length, insert: value } });
    }
  }, [value]);

  return (
    <div
      ref={containerRef}
      className="flex-1 min-h-0 overflow-hidden rounded border border-[#333] focus-within:border-[#555]"
    />
  );
}

interface MacroEditorProps {
  open: boolean;
  macros: Record<string, string>;
  macroIcons?: Record<string, string>;
  customCommands: Record<string, string[]>;
  commands: CommandInfo[];
  attackKeys: string[];
  onSave: (
    macros: Record<string, string>,
    customCommands: Record<string, string[]>,
    macroIcons: Record<string, string>,
  ) => void;
  onClose: () => void;
}

function captureKey(e: KeyboardEvent): string {
  const mods: string[] = [];
  if (e.ctrlKey) mods.push("Ctrl");
  if (e.altKey) mods.push("Alt");
  if (e.metaKey) mods.push("Meta");
  if (e.shiftKey && e.code !== "ShiftLeft" && e.code !== "ShiftRight") mods.push("Shift");
  return mods.length ? `${mods.join("+")}+${e.code}` : e.code;
}

export function MacroEditor({
  open,
  macros,
  macroIcons,
  customCommands,
  commands,
  attackKeys,
  onSave,
  onClose,
}: MacroEditorProps) {
  const [localMacros, setLocalMacros] = useState<Record<string, string>>({});
  const [localIcons, setLocalIcons] = useState<Record<string, string>>({});
  const [localBindings, setLocalBindings] = useState<Record<string, string[]>>({});
  const [pinned, setPinned] = useState<Set<string>>(new Set());
  const [selected, setSelected] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [recording, setRecording] = useState<{ macro: string; index: number } | null>(null);
  const recordingRef = useRef<{ macro: string; index: number } | null>(null);

  const { startDrag, moveDrag, endDrag, guardClick } = useAttackDrag(
    () => "#b45309",
    "macro",
    (id) => localIcons[id] ?? DEFAULT_MACRO_ICON,
  );

  useLayoutEffect(() => {
    if (open && window.mcState) window.mcState.modalOpen = true;
  }, [open]);

  useEffect(() => {
    recordingRef.current = recording;
  }, [recording]);

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLocalMacros({ ...macros });
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLocalIcons({ ...(macroIcons ?? {}) });
      const macroBindings: Record<string, string[]> = {};
      for (const [k, v] of Object.entries(customCommands)) {
        if (k.startsWith("macro:")) macroBindings[k] = v;
      }
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLocalBindings(macroBindings);
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setPinned(new Set(customCommands[PINNED_KEY] ?? []));
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSelected(Object.keys(macros)[0] ?? null);
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setNewName("");
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setRecording(null);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      const rec = recordingRef.current;
      if (!rec) return;
      e.preventDefault();
      e.stopPropagation();
      if (e.key === "Escape") {
        setRecording(null);
        return;
      }
      const key = captureKey(e);
      setLocalBindings((prev) => {
        const bindKey = "macro:" + rec.macro;
        const keys = [...(prev[bindKey] ?? [])];
        if (rec.index === keys.length) keys.push(key);
        else keys[rec.index] = key;
        return { ...prev, [bindKey]: keys };
      });
      setRecording(null);
    };
    window.addEventListener("keydown", handler, true);
    return () => window.removeEventListener("keydown", handler, true);
  }, [open]);

  const addMacro = () => {
    const name = newName.trim();
    if (!name || localMacros[name] !== undefined) return;
    setLocalMacros((prev) => ({ ...prev, [name]: "" }));
    setSelected(name);
    setNewName("");
  };

  const deleteMacro = (name: string) => {
    setLocalMacros((prev) => {
      const next = { ...prev };
      delete next[name];
      return next;
    });
    setLocalIcons((prev) => {
      const next = { ...prev };
      delete next[name];
      return next;
    });
    setLocalBindings((prev) => {
      const next = { ...prev };
      delete next["macro:" + name];
      return next;
    });
    setPinned((prev) => {
      const next = new Set(prev);
      next.delete(name);
      return next;
    });
    if (selected === name) {
      const remaining = Object.keys(localMacros).filter((k) => k !== name);
      setSelected(remaining[0] ?? null);
    }
  };

  const togglePin = (name: string) => {
    setPinned((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  };

  const updateCode = (code: string) => {
    if (!selected) return;
    setLocalMacros((prev) => ({ ...prev, [selected]: code }));
  };

  const renameMacro = (oldName: string, newName: string) => {
    const trimmed = newName.trim();
    if (!trimmed || trimmed === oldName || localMacros[trimmed] !== undefined) return;
    setLocalMacros((prev) => {
      const next: Record<string, string> = {};
      for (const [k, v] of Object.entries(prev)) next[k === oldName ? trimmed : k] = v;
      return next;
    });
    setLocalIcons((prev) => {
      if (prev[oldName] === undefined) return prev;
      const next = { ...prev, [trimmed]: prev[oldName] };
      delete next[oldName];
      return next;
    });
    setLocalBindings((prev) => {
      const next = { ...prev };
      const oldKey = "macro:" + oldName;
      const newKey = "macro:" + trimmed;
      if (next[oldKey] !== undefined) {
        next[newKey] = next[oldKey];
        delete next[oldKey];
      }
      return next;
    });
    setPinned((prev) => {
      if (!prev.has(oldName)) return prev;
      const next = new Set(prev);
      next.delete(oldName);
      next.add(trimmed);
      return next;
    });
    setSelected(trimmed);
  };

  const removeBinding = (macro: string, idx: number) => {
    setLocalBindings((prev) => {
      const bindKey = "macro:" + macro;
      return { ...prev, [bindKey]: (prev[bindKey] ?? []).filter((_, i) => i !== idx) };
    });
  };

  const handleRun = () => {
    if (!selected) return;
    window.mc?.setPendingRunMacroScript?.(localMacros[selected] ?? "");
  };

  const handleSave = () => {
    const nonMacroCustomCmds = Object.fromEntries(
      Object.entries(customCommands).filter(([k]) => !k.startsWith("macro:") && k !== PINNED_KEY),
    );
    const merged = {
      ...nonMacroCustomCmds,
      ...localBindings,
      ...(pinned.size > 0 ? { [PINNED_KEY]: [...pinned] } : {}),
    };
    onSave(localMacros, merged, localIcons);
  };

  const names = Object.keys(localMacros);
  const selectedCode = selected ? (localMacros[selected] ?? "") : "";
  const selectedBindKey = selected ? "macro:" + selected : null;
  const selectedKeys = selectedBindKey ? (localBindings[selectedBindKey] ?? []) : [];

  if (!open) return null;

  return (
    <Dialog modal={true} open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        movable
        className="min-w-[700px] max-w-[900px] h-[560px] flex flex-col font-mono p-5 gap-3"
        onEscapeKeyDown={(e) => e.preventDefault()}
        onKeyDown={(e) => {
          if (e.key === "Escape" && !recording) {
            e.stopPropagation();
            onClose();
          }
        }}
      >
        <DialogTitle className="text-center font-mono text-xl tracking-[0.25em] mb-1">MACROS</DialogTitle>

        {recording && (
          <div
            className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/75 rounded-lg gap-3 cursor-pointer"
            onClick={() => setRecording(null)}
          >
            <div className="text-[15px] text-amber-400 font-mono">Press a key to bind…</div>
            <div className="text-xs text-white/50 font-mono">Click or Escape to cancel</div>
          </div>
        )}

        <div className="flex gap-3 flex-1 overflow-hidden">
          {/* Left: macro list */}
          <div className="w-[200px] flex flex-col gap-2 shrink-0">
            <div className="flex gap-1">
              <input
                type="text"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && addMacro()}
                placeholder="macro name"
                className="flex-1 bg-[#1a1a1a] border border-[#444] rounded-sm text-white text-xs px-2 py-1 outline-none font-mono"
              />
              <button
                onClick={addMacro}
                disabled={!newName.trim()}
                className="bg-[#2a2a2a] border border-dashed border-[#555] rounded-sm text-[#aaa] text-xs px-2 py-1 hover:border-[#888] disabled:opacity-40"
              >
                +
              </button>
            </div>
            <div className="flex-1 overflow-y-auto flex flex-col gap-1">
              {names.length === 0 && <div className="text-white/30 text-[11px] py-2 text-center">No macros yet</div>}
              {names.map((name) => (
                <div
                  key={name}
                  onClick={() => setSelected(name)}
                  className={`flex items-center gap-1.5 px-2 py-1.5 rounded cursor-pointer text-xs ${
                    selected === name
                      ? "bg-amber-400/20 border border-amber-400/40 text-amber-200"
                      : "bg-[#1a1a1a] border border-[#333] text-white/70 hover:bg-[#2a2a2a]"
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={pinned.has(name)}
                    onChange={() => togglePin(name)}
                    onClick={(e) => e.stopPropagation()}
                    title="Show in attack panel"
                    className="accent-amber-400 shrink-0"
                  />
                  <div
                    onPointerDown={(e) => {
                      e.stopPropagation();
                      startDrag(e, name);
                    }}
                    onPointerMove={moveDrag}
                    onPointerUp={endDrag}
                    onPointerCancel={endDrag}
                    onClick={(e) => {
                      e.stopPropagation();
                      guardClick(() => {});
                    }}
                    onMouseDown={(e) => e.stopPropagation()}
                    title="Alt+drag to shortcut bar"
                    className="w-[22px] h-[22px] flex items-center justify-center rounded border border-amber-400/40 bg-amber-400/10 text-[13px] leading-none cursor-grab shrink-0 select-none text-amber-400"
                  >
                    {localIcons[name] ?? DEFAULT_MACRO_ICON}
                  </div>
                  <span className="truncate flex-1">{name}</span>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      deleteMacro(name);
                    }}
                    className="text-red-400/60 hover:text-red-400 ml-1 leading-none shrink-0"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* Right: code editor */}
          <div className="flex-1 flex flex-col gap-2 overflow-hidden">
            {selected ? (
              <>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    key={selected}
                    defaultValue={selected}
                    onBlur={(e) => renameMacro(selected, e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        renameMacro(selected, (e.target as HTMLInputElement).value);
                        (e.target as HTMLInputElement).blur();
                      }
                      if (e.key === "Escape") {
                        (e.target as HTMLInputElement).value = selected;
                        (e.target as HTMLInputElement).blur();
                      }
                    }}
                    className="flex-1 bg-transparent border-b border-[#444] text-white/70 text-[11px] uppercase tracking-wide outline-none focus:border-amber-400/50 font-mono min-w-0"
                  />
                  <span className="text-white/30 text-[11px] shrink-0">— JEXL script</span>
                </div>
                <JexlEditor
                  key={selected}
                  value={selectedCode}
                  onChange={updateCode}
                  commands={commands}
                  attackKeys={attackKeys}
                />
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-white/40 text-[11px]">Icon:</span>
                  <div
                    onPointerDown={(e) => startDrag(e, selected)}
                    onPointerMove={moveDrag}
                    onPointerUp={endDrag}
                    onPointerCancel={endDrag}
                    onClick={() => guardClick(() => {})}
                    onMouseDown={(e) => e.stopPropagation()}
                    title="Alt+drag to shortcut bar"
                    className="w-[32px] h-[32px] flex items-center justify-center rounded border-2 border-amber-400/60 bg-amber-400/15 text-[20px] leading-none cursor-grab shrink-0 select-none text-amber-400"
                  >
                    {localIcons[selected] ?? DEFAULT_MACRO_ICON}
                  </div>
                  <div className="w-px h-5 bg-white/15 shrink-0" />
                  {MACRO_ICON_PALETTE.map((emoji) => (
                    <button
                      key={emoji}
                      onClick={() => setLocalIcons((prev) => ({ ...prev, [selected]: emoji }))}
                      className={`w-[22px] h-[22px] flex items-center justify-center rounded text-[13px] leading-none transition-colors ${
                        (localIcons[selected] ?? DEFAULT_MACRO_ICON) === emoji
                          ? "bg-amber-400/30 border border-amber-400/70"
                          : "bg-[#1a1a1a] border border-[#333] hover:border-amber-400/40"
                      }`}
                    >
                      {emoji}
                    </button>
                  ))}
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-white/40 text-[11px]">Keys:</span>
                  {selectedKeys.map((k, i) => (
                    <span
                      key={i}
                      onClick={() => removeBinding(selected, i)}
                      className="bg-[#2a2a2a] border border-[#555] rounded-sm px-1.5 py-0.5 text-[10px] text-white/70 cursor-pointer hover:border-red-500 hover:text-red-400 font-mono"
                      title="Click to remove"
                    >
                      {k}
                    </span>
                  ))}
                  <button
                    onClick={() => setRecording({ macro: selected, index: selectedKeys.length })}
                    className="bg-transparent border border-dashed border-[#555] rounded-sm text-[#888] text-[10px] px-1.5 py-0.5 hover:border-[#888] font-mono"
                  >
                    + bind key
                  </button>
                </div>
                <div className="flex justify-end gap-2">
                  <Button variant="secondary" onClick={handleRun} className="font-mono text-xs">
                    ▶ Run
                  </Button>
                </div>
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center text-white/30 text-sm">
                Select or create a macro
              </div>
            )}
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-1 border-t border-[#2a2a2a]">
          <Button variant="secondary" onClick={onClose} className="font-mono">
            Cancel
          </Button>
          <Button onClick={handleSave} className="font-mono">
            Save
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
