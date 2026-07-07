import { useState, useRef, useEffect } from "react";
import { Dialog, DialogContent, DialogTitle } from "../../primitives/Dialog";
import { Button } from "../../primitives/Button";

const PINNED_KEY = "__pinned_macros__";

interface MacroEditorProps {
  open: boolean;
  macros: Record<string, string>;
  customCommands: Record<string, string[]>;
  onSave: (macros: Record<string, string>, customCommands: Record<string, string[]>) => void;
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

export function MacroEditor({ open, macros, customCommands, onSave, onClose }: MacroEditorProps) {
  const [localMacros, setLocalMacros] = useState<Record<string, string>>({});
  const [localBindings, setLocalBindings] = useState<Record<string, string[]>>({});
  const [pinned, setPinned] = useState<Set<string>>(new Set());
  const [selected, setSelected] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [recording, setRecording] = useState<{ macro: string; index: number } | null>(null);
  const recordingRef = useRef<{ macro: string; index: number } | null>(null);

  useEffect(() => {
    recordingRef.current = recording;
  }, [recording]);

  useEffect(() => {
    if (open) {
      setLocalMacros({ ...macros });
      const macroBindings: Record<string, string[]> = {};
      for (const [k, v] of Object.entries(customCommands)) {
        if (k.startsWith("macro:")) macroBindings[k] = v;
      }
      setLocalBindings(macroBindings);
      setPinned(new Set(customCommands[PINNED_KEY] ?? []));
      setSelected(Object.keys(macros)[0] ?? null);
      setNewName("");
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
    window.mcRunMacro?.(selected);
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
    onSave(localMacros, merged);
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
                <textarea
                  value={selectedCode}
                  onChange={(e) => updateCode(e.target.value)}
                  spellCheck={false}
                  className="flex-1 bg-[#0e0e0e] border border-[#333] rounded text-green-300 text-[12px] px-3 py-2 font-mono resize-none outline-none focus:border-[#555]"
                  placeholder={`// JEXL API:\n// send("/tp 0 64 0")   — slash command\n// send("/heal")          — another command`}
                />
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
