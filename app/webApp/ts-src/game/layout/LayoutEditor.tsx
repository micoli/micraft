import { useState, useRef, useCallback, useEffect } from "react";
import { GameLayout, LayoutWidget } from "../types";
import {
  DEFAULT_WIDGETS,
  WIDGET_REGISTRY,
  defaultLayout,
  resolveActiveLayout,
  fillMissingWidgets,
} from "./LayoutEngine";

interface Props {
  open: boolean;
  layouts: GameLayout[];
  activeLayout: string;
  onSave: (layouts: GameLayout[], activeLayout: string) => void;
  onClose: () => void;
}

interface DragState {
  type: string;
  mode: "move" | "resize";
  startMouseX: number;
  startMouseY: number;
  startW: number;
  startH: number;
  startX: number;
  startY: number;
}

export function LayoutEditor({ open, layouts, activeLayout, onSave, onClose }: Props) {
  const [localLayouts, setLocalLayouts] = useState<GameLayout[]>([]);
  const [localActive, setLocalActive] = useState("default");
  const [nameInput, setNameInput] = useState("");
  const [selectedWidget, setSelectedWidget] = useState<string | null>(null);
  const drag = useRef<DragState | null>(null);
  const gridRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) {
      const copy = layouts.map((l) => fillMissingWidgets({ ...l, widgets: [...l.widgets] }));
      setLocalLayouts(copy);
      setLocalActive(activeLayout);
      setNameInput(activeLayout);
    }
  }, [open, layouts, activeLayout]);

  const currentLayout = resolveActiveLayout(localLayouts, localActive);

  const updateWidgets = useCallback(
    (updater: (ws: LayoutWidget[]) => LayoutWidget[]) => {
      setLocalLayouts((prev) => prev.map((l) => (l.name === localActive ? { ...l, widgets: updater(l.widgets) } : l)));
    },
    [localActive],
  );

  const onMouseDown = (e: React.MouseEvent, type: string, mode: "move" | "resize") => {
    e.preventDefault();
    e.stopPropagation();
    const w = currentLayout.widgets.find((w) => w.type === type);
    if (!w) return;
    setSelectedWidget(type);
    drag.current = {
      type,
      mode,
      startMouseX: e.clientX,
      startMouseY: e.clientY,
      startX: w.x,
      startY: w.y,
      startW: w.w,
      startH: w.h,
    };
  };

  const onMouseMove = (e: React.MouseEvent) => {
    if (!drag.current || !gridRef.current) return;
    const { type, mode, startMouseX, startMouseY, startX, startY, startW, startH } = drag.current;
    const rect = gridRef.current.getBoundingClientRect();
    const cellW = rect.width / 48;
    const cellH = rect.height / 48;
    const dx = Math.round((e.clientX - startMouseX) / cellW);
    const dy = Math.round((e.clientY - startMouseY) / cellH);
    const def = WIDGET_REGISTRY.find((d) => d.type === type);
    const min = { w: def?.minW ?? 2, h: def?.minH ?? 2 };

    updateWidgets((ws) =>
      ws.map((w) => {
        if (w.type !== type) return w;
        if (mode === "move") {
          return {
            ...w,
            x: Math.max(0, Math.min(48 - w.w, startX + dx)),
            y: Math.max(0, Math.min(48 - w.h, startY + dy)),
          };
        }
        return {
          ...w,
          w: Math.max(min.w, Math.min(48 - w.x, startW + dx)),
          h: Math.max(min.h, Math.min(48 - w.y, startH + dy)),
        };
      }),
    );
  };

  const onMouseUp = () => {
    drag.current = null;
  };

  const handleReset = () => {
    setLocalLayouts((prev) => prev.map((l) => (l.name === localActive ? { ...l, widgets: [...DEFAULT_WIDGETS] } : l)));
  };

  const handleNewLayout = () => {
    const base = localActive;
    let candidate = base + "_copy";
    let n = 1;
    while (localLayouts.some((l) => l.name === candidate)) candidate = base + "_copy" + ++n;
    const src = localLayouts.find((l) => l.name === localActive) ?? defaultLayout();
    const newL: GameLayout = { name: candidate, widgets: [...src.widgets] };
    setLocalLayouts((prev) => [...prev, newL]);
    setLocalActive(candidate);
    setNameInput(candidate);
  };

  const handleRenameActive = (newName: string) => {
    setNameInput(newName);
    const trimmed = newName.trim();
    if (!trimmed || localLayouts.some((l) => l.name === trimmed && l.name !== localActive)) return;
    setLocalLayouts((prev) => prev.map((l) => (l.name === localActive ? { ...l, name: trimmed } : l)));
    setLocalActive(trimmed);
  };

  const handleSave = () => {
    onSave(localLayouts, localActive);
    onClose();
  };

  const handleSelectLayout = (name: string) => {
    setLocalActive(name);
    setNameInput(name);
  };

  if (!open) return null;

  const widgets = currentLayout.widgets;

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 3000,
        background: "rgba(0,0,0,0.85)",
        display: "flex",
        flexDirection: "column",
      }}
      onMouseMove={onMouseMove}
      onMouseUp={onMouseUp}
    >
      {/* Toolbar */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          padding: "8px 16px",
          background: "rgba(0,0,0,0.7)",
          borderBottom: "1px solid rgba(255,255,255,0.15)",
          flexShrink: 0,
          flexWrap: "wrap",
        }}
      >
        <span style={{ color: "#fff", font: "bold 14px monospace", marginRight: 8 }}>Layout Editor</span>

        <select
          value={localActive}
          onChange={(e) => handleSelectLayout(e.target.value)}
          style={{
            background: "rgba(0,0,0,0.6)",
            color: "#fff",
            border: "1px solid rgba(255,255,255,0.3)",
            borderRadius: 4,
            padding: "3px 6px",
            font: "12px monospace",
          }}
        >
          {localLayouts.map((l) => (
            <option key={l.name} value={l.name}>
              {l.name}
            </option>
          ))}
        </select>

        <input
          value={nameInput}
          onChange={(e) => handleRenameActive(e.target.value)}
          placeholder="Layout name"
          style={{
            background: "rgba(0,0,0,0.6)",
            color: "#fff",
            border: "1px solid rgba(255,255,255,0.3)",
            borderRadius: 4,
            padding: "3px 8px",
            font: "12px monospace",
            width: 120,
          }}
        />

        <button onClick={handleNewLayout} style={btnStyle}>
          + New
        </button>
        <button onClick={handleReset} style={btnStyle}>
          Reset
        </button>

        <div style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
          <button
            onClick={handleSave}
            style={{
              ...btnStyle,
              background: "rgba(60,140,60,0.8)",
              borderColor: "rgba(100,200,100,0.5)",
            }}
          >
            Save
          </button>
          <button
            onClick={onClose}
            style={{
              ...btnStyle,
              background: "rgba(140,40,40,0.8)",
              borderColor: "rgba(200,80,80,0.5)",
            }}
          >
            ✕
          </button>
        </div>
      </div>

      {/* Grid */}
      <div
        ref={gridRef}
        style={{
          flex: 1,
          position: "relative",
          overflow: "hidden",
          backgroundImage:
            "linear-gradient(rgba(255,255,255,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.04) 1px, transparent 1px)",
          backgroundSize: "calc(100% / 48) calc(100% / 48)",
        }}
      >
        {widgets.map((w) => (
          <div
            key={w.type}
            style={{
              position: "absolute",
              left: `calc(${w.x} / 48 * 100%)`,
              top: `calc(${w.y} / 48 * 100%)`,
              width: `calc(${w.w} / 48 * 100%)`,
              height: `calc(${w.h} / 48 * 100%)`,
              zIndex: selectedWidget === w.type ? 10 : 1,
              background: WIDGET_REGISTRY.find((d) => d.type === w.type)?.editorColor ?? "rgba(100,100,100,0.7)",
              border: selectedWidget === w.type ? "2px solid rgba(255,220,80,0.9)" : "2px solid rgba(255,255,255,0.4)",
              borderRadius: 4,
              cursor: "grab",
              userSelect: "none",
              boxSizing: "border-box",
              display: "flex",
              alignItems: "flex-start",
              justifyContent: "flex-start",
              padding: "4px 6px",
              overflow: "hidden",
            }}
            onMouseDown={(e) => onMouseDown(e, w.type, "move")}
          >
            <span
              style={{
                color: "#fff",
                font: "bold 11px monospace",
                pointerEvents: "none",
                textShadow: "1px 1px 2px rgba(0,0,0,0.8)",
              }}
            >
              {WIDGET_REGISTRY.find((d) => d.type === w.type)?.editorLabel ?? w.type}
              <br />
              <span style={{ font: "9px monospace", opacity: 0.7 }}>
                {w.x},{w.y} {w.w}×{w.h}
              </span>
            </span>
            {/* Resize handle */}
            <div
              style={{
                position: "absolute",
                bottom: 2,
                right: 2,
                width: 12,
                height: 12,
                cursor: "nwse-resize",
                background: "rgba(255,255,255,0.6)",
                borderRadius: 2,
              }}
              onMouseDown={(e) => onMouseDown(e, w.type, "resize")}
            />
          </div>
        ))}
      </div>
    </div>
  );
}

const btnStyle: React.CSSProperties = {
  background: "rgba(60,60,80,0.8)",
  color: "#fff",
  border: "1px solid rgba(255,255,255,0.25)",
  borderRadius: 4,
  padding: "4px 10px",
  font: "12px monospace",
  cursor: "pointer",
};
