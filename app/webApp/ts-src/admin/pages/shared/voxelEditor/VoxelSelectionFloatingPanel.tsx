import { useEffect, useRef, useState } from "react";
import { CssBlockCube } from "../../../../game/shared/BlockPreview";
import { Icon } from "../../../../primitives/Icon";
import { ICONS } from "../../../../primitives/icons";
import { type SelectionBox, type SelectionShape, type SelectionSnap } from "./selectionGizmo";
import { RESIZE_STEPS } from "./selectionVoxels";
import { type PasteTransform } from "./pasteTransform";
import { type PasteOrigin } from "./pasteGizmo";
import { type SelectionField } from "./VoxelEditorSidebar";

const SELECTION_SHAPES: { key: SelectionShape; label: string; icon: string }[] = [
  { key: "box", label: "Box", icon: ICONS.shapeBox },
  { key: "sphere", label: "Sphere", icon: ICONS.shapeSphere },
  { key: "spheroid", label: "Spheroid", icon: ICONS.shapeSpheroid },
  { key: "cylinder", label: "Cylinder", icon: ICONS.shapeCylinder },
];

const SELECTION_SNAPS: { key: SelectionSnap; label: string; icon: string }[] = [
  { key: "none", label: "None", icon: ICONS.snapNone },
  { key: "voxel", label: "Voxel", icon: ICONS.snapVoxel },
  { key: "half", label: "½", icon: ICONS.snapHalf },
  { key: "quarter", label: "¼", icon: ICONS.snapQuarter },
];

// Label for a saved-selection list row: shape name + rounded voxel dimensions (the box may have
// fractional bounds from the gizmo's quarter-voxel drag snap).
function formatSavedSelectionLabel(shape: SelectionShape, box: SelectionBox): string {
  const dx = Math.round(box.maxX - box.minX);
  const dy = Math.round(box.maxY - box.minY);
  const dz = Math.round(box.maxZ - box.minZ);
  return `${shape} ${dx}×${dy}×${dz}`;
}

// Trims to 2 decimals (enough for the gizmo's finest quarter-voxel snap) and drops trailing zeros
// — an integer coordinate reads as "4", not "4.00".
function formatNum(n: number): string {
  return Number(n.toFixed(2)).toString();
}

const PANEL_POS_STORAGE_KEY = "voxelEditorSelectionPanelPos";
const PANEL_DEFAULT_POS = { top: 48, left: 16 };

function clampPanelPos(pos: { top: number; left: number }): { top: number; left: number } {
  const maxLeft = Math.max(0, window.innerWidth - 40);
  const maxTop = Math.max(0, window.innerHeight - 40);
  return { top: Math.min(maxTop, Math.max(0, pos.top)), left: Math.min(maxLeft, Math.max(0, pos.left)) };
}

function loadPanelPos(): { top: number; left: number } {
  try {
    const stored = JSON.parse(localStorage.getItem(PANEL_POS_STORAGE_KEY) ?? "null");
    if (stored && Number.isFinite(stored.top) && Number.isFinite(stored.left)) return clampPanelPos(stored);
  } catch {
    // fall through to default
  }
  return PANEL_DEFAULT_POS;
}

// Floating, draggable window holding every select-mode widget (shape/snap/resize, pos/size
// fields, pattern slots, fill/shell/cut/copy/paste, saved selections). Position persists across
// reloads in localStorage — the gizmo/canvas underneath stays fully interactive since the panel
// only occupies its own bounding box.
export function VoxelSelectionFloatingPanel({
  selectionShape,
  onSelectShape,
  selectionSnap,
  onSelectSnap,
  hasSelection,
  selection,
  onSelectionFieldChange,
  resizeStep,
  onSelectResizeStep,
  onExpandSelection,
  onContractSelection,
  patternBlocks,
  activePatternSlot,
  onSelectPatternSlot,
  onClearPatternSlot,
  onFill,
  onShell,
  onCut,
  onCopy,
  clipboardCount,
  onPaste,
  isPasting,
  onConfirmPaste,
  onCancelPaste,
  onRotatePaste,
  onFlipPaste,
  pasteTransform,
  pasteOrigin,
  onMovePasteOrigin,
  savedSelections,
  onAddSelectionToMemory,
  onSelectSavedSelection,
  onRemoveSavedSelection,
  getOrdinal,
  previewsReady,
  blockDefsReady,
  getPreview,
}: {
  selectionShape: SelectionShape;
  onSelectShape: (shape: SelectionShape) => void;
  selectionSnap: SelectionSnap;
  onSelectSnap: (snap: SelectionSnap) => void;
  hasSelection: boolean;
  selection: SelectionBox | null;
  onSelectionFieldChange: (field: SelectionField, value: number) => void;
  resizeStep: number;
  onSelectResizeStep: (step: number) => void;
  onExpandSelection: () => void;
  onContractSelection: () => void;
  patternBlocks: [string | null, string | null];
  activePatternSlot: 0 | 1;
  onSelectPatternSlot: (slot: 0 | 1) => void;
  onClearPatternSlot: (slot: 0 | 1) => void;
  onFill: () => void;
  onShell: () => void;
  onCut: () => void;
  onCopy: () => void;
  clipboardCount: number | null;
  onPaste: () => void;
  isPasting: boolean;
  onConfirmPaste: () => void;
  onCancelPaste: () => void;
  onRotatePaste: (dir: -1 | 1) => void;
  onFlipPaste: (axis: "x" | "y" | "z") => void;
  pasteTransform: PasteTransform;
  pasteOrigin: PasteOrigin | null;
  onMovePasteOrigin: (field: "x" | "y" | "z", value: number) => void;
  savedSelections: { shape: SelectionShape; box: SelectionBox }[];
  onAddSelectionToMemory: () => void;
  onSelectSavedSelection: (index: number) => void;
  onRemoveSavedSelection: (index: number) => void;
  getOrdinal: (name: string) => number | null;
  previewsReady: boolean;
  blockDefsReady: boolean;
  getPreview: (ordinal: number) => string | null;
}) {
  const [pos, setPos] = useState(loadPanelPos);
  const dragStateRef = useRef<{ startX: number; startY: number; startTop: number; startLeft: number } | null>(null);

  useEffect(() => {
    localStorage.setItem(PANEL_POS_STORAGE_KEY, JSON.stringify(pos));
  }, [pos]);

  useEffect(() => {
    function onMouseMove(e: MouseEvent) {
      const state = dragStateRef.current;
      if (!state) return;
      setPos(
        clampPanelPos({
          top: state.startTop + (e.clientY - state.startY),
          left: state.startLeft + (e.clientX - state.startX),
        }),
      );
    }
    function onMouseUp() {
      dragStateRef.current = null;
      document.body.style.userSelect = "";
    }
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
    };
  }, []);

  function handleDragStart(e: React.MouseEvent) {
    e.preventDefault();
    dragStateRef.current = { startX: e.clientX, startY: e.clientY, startTop: pos.top, startLeft: pos.left };
    document.body.style.userSelect = "none";
  }

  return (
    <div
      className="fixed z-20 flex flex-col rounded-lg border border-[#2E3A4E] bg-[#0B1220]/95 shadow-lg backdrop-blur-sm w-64"
      style={{ top: pos.top, left: pos.left }}
    >
      <div
        onMouseDown={handleDragStart}
        className="shrink-0 flex items-center justify-center px-2 py-1 border-b border-[#2E3A4E] cursor-move rounded-t-lg bg-black/30"
      >
        <span className="text-[9px] text-[#8A99AF] font-medium">Selection</span>
      </div>
      <div className="flex flex-col gap-1.5 p-2 overflow-y-auto max-h-[70vh]">
        <div className="flex flex-wrap gap-1 items-center justify-center">
          {SELECTION_SHAPES.map((s) => (
            <button
              key={s.key}
              onClick={() => onSelectShape(s.key)}
              title={s.label}
              className={`flex items-center justify-center px-2 py-1 rounded transition-colors ${
                selectionShape === s.key ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
              }`}
            >
              <Icon d={s.icon} size={14} />
            </button>
          ))}
        </div>
        <div className="flex flex-wrap gap-1 items-center justify-center">
          {SELECTION_SNAPS.map((s) => (
            <button
              key={s.key}
              onClick={() => onSelectSnap(s.key)}
              title={s.label}
              className={`flex items-center justify-center px-2 py-1 rounded transition-colors ${
                selectionSnap === s.key ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
              }`}
            >
              <Icon d={s.icon} size={14} />
            </button>
          ))}
        </div>
        {selection && (
          <div className="flex flex-col items-center gap-1">
            <div className="flex items-center gap-1">
              <span className="text-[9px] text-[#8A99AF] w-8">&nbsp;</span>
              <span className="text-[9px] text-[#8A99AF] w-16">X</span>
              <span className="text-[9px] text-[#8A99AF] w-16">Y</span>
              <span className="text-[9px] text-[#8A99AF] w-16">Z</span>
            </div>
            <div className="flex items-center gap-1">
              <span className="text-[9px] text-[#8A99AF] w-8">Pos</span>
              {(["minX", "minY", "minZ"] as const).map((field) => (
                <input
                  key={field}
                  type="number"
                  step={0.25}
                  value={formatNum(selection[field])}
                  onChange={(e) => {
                    const v = Number(e.target.value);
                    if (Number.isFinite(v)) onSelectionFieldChange(field, v);
                  }}
                  className="w-16 rounded bg-black/50 border border-[#2E3A4E] px-1 py-0.5 text-[9px] text-white font-mono outline-none focus:border-[#3C50E0]"
                />
              ))}
            </div>
            <div className="flex items-center gap-1">
              <span className="text-[9px] text-[#8A99AF] w-8">Size</span>
              {(
                [
                  ["sizeX", selection.maxX - selection.minX],
                  ["sizeY", selection.maxY - selection.minY],
                  ["sizeZ", selection.maxZ - selection.minZ],
                ] as const
              ).map(([field, size]) => (
                <input
                  key={field}
                  type="number"
                  step={0.25}
                  min={0.25}
                  value={formatNum(size)}
                  onChange={(e) => {
                    const v = Number(e.target.value);
                    if (Number.isFinite(v)) onSelectionFieldChange(field, v);
                  }}
                  className="w-16 rounded bg-black/50 border border-[#2E3A4E] px-1 py-0.5 text-[10px] text-white font-mono outline-none focus:border-[#3C50E0]"
                />
              ))}
            </div>
          </div>
        )}
        <div className="flex flex-wrap gap-1 items-center justify-center">
          <button
            onClick={onContractSelection}
            disabled={!hasSelection}
            title="Contract selection"
            className="px-2 py-1 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
          >
            −
          </button>
          {RESIZE_STEPS.map((s) => (
            <button
              key={s.key}
              title={s.label}
              onClick={() => onSelectResizeStep(s.key)}
              className={`px-2 py-1 rounded text-[10px] font-medium transition-colors ${
                resizeStep === s.key ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
              }`}
            >
              <Icon d={s.icon} size={14} />
            </button>
          ))}
          <button
            onClick={onExpandSelection}
            disabled={!hasSelection}
            title="Expand selection"
            className="px-2 py-1 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
          >
            +
          </button>
        </div>
        <div className="flex flex-col items-center gap-1">
          <div className="flex flex-wrap gap-1 items-center justify-center">
            <button
              onClick={onCut}
              disabled={!hasSelection}
              className="px-2 py-1 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
            >
              Cut
            </button>
            <button
              onClick={onCopy}
              disabled={!hasSelection}
              className="px-2 py-1 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
            >
              Copy
            </button>
            <button
              onClick={onPaste}
              disabled={!clipboardCount}
              className={`px-2 py-1 rounded text-[10px] font-medium transition-colors disabled:opacity-40 ${
                isPasting
                  ? "bg-[#3C50E0] text-white"
                  : "bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white"
              }`}
            >
              Paste
            </button>
          </div>
          <div className="flex flex-wrap gap-1 items-center justify-center">
            <button
              onClick={onFill}
              disabled={!hasSelection || !patternBlocks[0]}
              className="px-2 py-1 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
            >
              Fill
            </button>
            <button
              onClick={onShell}
              disabled={!hasSelection || !patternBlocks[0]}
              className="px-2 py-1 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
            >
              Shell
            </button>
          </div>
          {clipboardCount !== null && (
            <span className="text-[9px] text-[#8A99AF]">Clipboard: {clipboardCount} blocks</span>
          )}
          {isPasting && pasteOrigin && (
            <>
              <div className="flex items-center gap-1">
                <span className="text-[9px] text-[#8A99AF] w-8">Pos</span>
                {(["x", "y", "z"] as const).map((axis) => (
                  <input
                    key={axis}
                    type="number"
                    step={1}
                    value={pasteOrigin[axis]}
                    onChange={(e) => {
                      const v = Number(e.target.value);
                      if (Number.isFinite(v)) onMovePasteOrigin(axis, v);
                    }}
                    className="w-16 rounded bg-black/50 border border-[#2E3A4E] px-1 py-0.5 text-[10px] text-white font-mono outline-none focus:border-[#3C50E0]"
                  />
                ))}
              </div>
              <div className="flex flex-wrap gap-1 items-center justify-center">
                <button
                  onClick={() => onRotatePaste(-1)}
                  title="Rotate -90°"
                  className="px-2 py-0.5 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] hover:bg-[#3C50E0] hover:text-white"
                >
                  ⟲ -90°
                </button>
                <button
                  onClick={() => onRotatePaste(1)}
                  title="Rotate +90°"
                  className="px-2 py-0.5 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] hover:bg-[#3C50E0] hover:text-white"
                >
                  ⟳ +90°
                </button>
                {(["x", "y", "z"] as const).map((axis) => (
                  <button
                    key={axis}
                    onClick={() => onFlipPaste(axis)}
                    title={`Flip ${axis.toUpperCase()}`}
                    className={`px-2 py-0.5 rounded text-[10px] font-medium transition-colors ${
                      pasteTransform[axis === "x" ? "flipX" : axis === "y" ? "flipY" : "flipZ"]
                        ? "bg-[#3C50E0] text-white"
                        : "bg-black/50 text-[#8A99AF] hover:bg-[#3C50E0] hover:text-white"
                    }`}
                  >
                    Flip {axis.toUpperCase()}
                  </button>
                ))}
              </div>
              <div className="flex gap-1 items-center justify-center">
                <button
                  onClick={onConfirmPaste}
                  className="px-2 py-0.5 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] hover:bg-[#3C50E0] hover:text-white"
                >
                  Confirm (Enter)
                </button>
                <button
                  onClick={onCancelPaste}
                  className="px-2 py-0.5 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] hover:bg-[#3C50E0] hover:text-white"
                >
                  Cancel (Esc)
                </button>
              </div>
            </>
          )}
        </div>
        <div className="flex flex-wrap gap-1 items-center justify-center">
          {([0, 1] as const).map((slot) => {
            const name = patternBlocks[slot];
            const ordinal = name ? getOrdinal(name) : null;
            return (
              <div
                key={slot}
                onClick={() => onSelectPatternSlot(slot)}
                title={name ?? (slot === 0 ? "Pattern block A" : "Pattern block B (optional)")}
                className={`flex items-center gap-1 rounded px-1.5 py-1 cursor-pointer border-2 ${
                  activePatternSlot === slot ? "border-[#3C50E0]" : "border-transparent"
                } bg-black/50`}
              >
                <span className="text-[9px] font-medium text-[#8A99AF]">{slot === 0 ? "A" : "B"}</span>
                <div className="h-4 w-4 flex items-center justify-center shrink-0">
                  {name && ordinal !== null && previewsReady ? (
                    getPreview(ordinal) ? (
                      <img
                        alt=""
                        src={getPreview(ordinal)!}
                        width={16}
                        height={16}
                        style={{ imageRendering: "pixelated", display: "block" }}
                      />
                    ) : blockDefsReady ? (
                      <CssBlockCube ordinal={ordinal} size={16} />
                    ) : null
                  ) : (
                    <div className="h-3 w-3 rounded-sm border border-dashed border-[#8A99AF]" />
                  )}
                </div>
                {name && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onClearPatternSlot(slot);
                    }}
                    className="text-[#8A99AF] hover:text-white text-[10px] leading-none"
                  >
                    ×
                  </button>
                )}
              </div>
            );
          })}
        </div>
        <div className="flex flex-col gap-1 border-t border-[#2E3A4E] pt-1.5">
          <div className="flex items-center justify-between">
            <span className="text-[9px] text-[#8A99AF] font-medium">Saved ({savedSelections.length}/10)</span>
            <button
              onClick={onAddSelectionToMemory}
              disabled={!hasSelection}
              className="px-2 py-0.5 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
            >
              + Add
            </button>
          </div>
          {savedSelections.length > 0 && (
            <div className="flex flex-col gap-0.5 overflow-y-auto max-h-[108px] pr-3">
              {savedSelections.map((entry, idx) => (
                <div
                  key={idx}
                  onClick={() => onSelectSavedSelection(idx)}
                  className="flex items-center justify-between rounded px-1.5 py-1 bg-black/30 hover:bg-black/50 cursor-pointer"
                >
                  <span className="text-[10px] text-[#8A99AF] truncate">
                    {formatSavedSelectionLabel(entry.shape, entry.box)}
                  </span>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onRemoveSavedSelection(idx);
                    }}
                    className="text-[#8A99AF] hover:text-white text-[10px] leading-none shrink-0 ml-1 px-1 py-0.5"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
