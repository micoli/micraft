import { useEffect, useLayoutEffect, useState } from "react";
import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { Button } from "../../primitives/Button";
import { JexlEditor } from "./JexlEditor";
import type { ActionBlockFormData } from "../types";

interface Props {
  data: ActionBlockFormData;
  onClose: () => void;
}

type VarPair = { key: string; value: string };

export function ActionBlockEditor({ data, onClose }: Props) {
  const [name, setName] = useState(data.name);
  const [onActivate, setOnActivate] = useState(data.onActivate);
  const [onTargetEvent, setOnTargetEvent] = useState(data.onTargetEvent);
  const [onRemoteEvent, setOnRemoteEvent] = useState(data.onRemoteEvent);
  const [vars, setVars] = useState<VarPair[]>(
    Object.entries(data.variables ?? {}).map(([key, value]) => ({ key, value })),
  );
  const [newKey, setNewKey] = useState("");
  const [newValue, setNewValue] = useState("");

  useLayoutEffect(() => {
    if (window.mcState) window.mcState.modalOpen = true;
  }, []);

  useEffect(() => {
    setName(data.name);
    setOnActivate(data.onActivate);
    setOnTargetEvent(data.onTargetEvent);
    setOnRemoteEvent(data.onRemoteEvent);
    setVars(Object.entries(data.variables ?? {}).map(([key, value]) => ({ key, value })));
  }, [data]);

  const addVar = () => {
    const k = newKey.trim();
    if (!k || vars.some((v) => v.key === k)) return;
    setVars((prev) => [...prev, { key: k, value: newValue }]);
    setNewKey("");
    setNewValue("");
  };

  const remove = () => {
    window.mc?.deleteActionBlock?.(JSON.stringify({ pos: data.pos }));
    onClose();
  };

  const save = () => {
    const variables: Record<string, string> = {};
    for (const v of vars) if (v.key.trim()) variables[v.key.trim()] = v.value;
    window.mc?.saveActionBlock?.(
      JSON.stringify({ pos: data.pos, name: name.trim(), onActivate, onTargetEvent, onRemoteEvent, variables }),
    );
    onClose();
  };

  const field = (label: string, value: string, set: (v: string) => void) => (
    <div className="flex flex-col gap-1 text-[11px] text-white/60">
      {label} <span className="text-white/30">— JEXL</span>
      <div className="h-24 flex flex-col">
        <JexlEditor value={value} onChange={set} commands={[]} attackKeys={[]} />
      </div>
    </div>
  );

  return (
    <Dialog modal={true} open={true} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        movable
        className="min-w-[520px] max-w-[640px] flex flex-col font-mono p-5 gap-3"
        onEscapeKeyDown={(e) => e.preventDefault()}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.stopPropagation();
            onClose();
          }
        }}
      >
        <DialogTitle className="text-center text-lg tracking-[0.2em]">ACTION BLOCK</DialogTitle>
        <div className="text-[10px] text-white/30 text-center -mt-2">
          {data.pos.x}, {data.pos.y}, {data.pos.z}
        </div>

        <label className="flex flex-col gap-1 text-[11px] text-white/60">
          Name
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="bg-[#1a1a1a] border border-[#444] rounded-sm text-white text-xs px-2 py-1 outline-none font-mono focus:border-amber-400/50"
          />
        </label>
        {data.error && <div className="text-red-400 text-[11px]">{data.error}</div>}

        {field("onActivate", onActivate, setOnActivate)}
        {field("onTargetEvent", onTargetEvent, setOnTargetEvent)}
        {field("onRemoteEvent", onRemoteEvent, setOnRemoteEvent)}

        <div className="flex flex-col gap-1 text-[11px] text-white/60">
          Variables
          <div className="flex flex-col gap-1">
            {vars.map((v, i) => (
              <div key={v.key} className="flex gap-1 items-center">
                <span className="text-amber-300 text-xs w-1/3 truncate">{v.key}</span>
                <input
                  value={v.value}
                  onChange={(e) =>
                    setVars((prev) => prev.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))
                  }
                  className="flex-1 bg-[#1a1a1a] border border-[#444] rounded-sm text-white text-xs px-2 py-0.5 outline-none font-mono"
                />
                <button
                  onClick={() => setVars((prev) => prev.filter((_, j) => j !== i))}
                  className="text-red-400/60 hover:text-red-400 px-1"
                >
                  ×
                </button>
              </div>
            ))}
            <div className="flex gap-1">
              <input
                value={newKey}
                onChange={(e) => setNewKey(e.target.value)}
                placeholder="key"
                className="w-1/3 bg-[#1a1a1a] border border-[#444] rounded-sm text-white text-xs px-2 py-0.5 outline-none font-mono"
              />
              <input
                value={newValue}
                onChange={(e) => setNewValue(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && addVar()}
                placeholder="value"
                className="flex-1 bg-[#1a1a1a] border border-[#444] rounded-sm text-white text-xs px-2 py-0.5 outline-none font-mono"
              />
              <button
                onClick={addVar}
                disabled={!newKey.trim()}
                className="bg-[#2a2a2a] border border-dashed border-[#555] rounded-sm text-[#aaa] text-xs px-2 disabled:opacity-40"
              >
                +
              </button>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between gap-2 pt-1 border-t border-[#2a2a2a]">
          <button
            onClick={remove}
            className="font-mono text-xs text-red-400/70 hover:text-red-400 border border-red-500/30 hover:border-red-500/60 rounded-sm px-2 py-1"
          >
            Delete
          </button>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={onClose} className="font-mono">
              Cancel
            </Button>
            <Button onClick={save} className="font-mono">
              Save
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
