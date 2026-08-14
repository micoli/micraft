import { useEffect, useRef, useState, type Dispatch, type RefObject, type SetStateAction } from "react";
import { type BlockInfoDto, type PlainColorDto } from "../../../apiTypes";
import { Block3DPreview } from "../../../../game/shared/Block3DPreview";
import { CssBlockCube } from "../../../../game/shared/BlockPreview";
import { VoxelPaletteBlock } from "./VoxelPaletteBlock";
import { VoxelColorPicker } from "./VoxelColorPicker";
import { VoxelShortcutBarSlot } from "./VoxelShortcutBarSlot";
import { type useAdminShortcutBar } from "./useAdminShortcutBar";
import { CLIP_AXES, ClipAxis, ClipPlaneState } from "./clipAxis";
import { ClipAxesInput } from "../../../../primitives/clipAxesInput";
import { dragMode, DragMode } from "../../../../primitives/DragMode";
import { type SelectionBox, type SelectionShape, type SelectionSnap } from "./selectionGizmo";
import { RESIZE_STEPS } from "./selectionVoxels";

const SELECTION_SHAPES: { key: SelectionShape; label: string }[] = [
  { key: "box", label: "Box" },
  { key: "sphere", label: "Sphere" },
  { key: "spheroid", label: "Spheroid" },
  { key: "cylinder", label: "Cylinder" },
];

const SELECTION_SNAPS: { key: SelectionSnap; label: string }[] = [
  { key: "none", label: "None" },
  { key: "voxel", label: "Voxel" },
  { key: "half", label: "½" },
  { key: "quarter", label: "¼" },
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

// Editable fields of the position/size widget — min* is the box's min corner (position), size* is
// its extent along that axis (max* is derived: min* + size*, so editing size never moves the box).
export type SelectionField = "minX" | "minY" | "minZ" | "sizeX" | "sizeY" | "sizeZ";

const SIDEBAR_WIDTH_STORAGE_KEY = "voxelEditorSidebarWidth";
const SIDEBAR_MIN_WIDTH = 220;
const SIDEBAR_MAX_WIDTH = 640;
const SIDEBAR_DEFAULT_WIDTH = 320;

function clampSidebarWidth(width: number): number {
  return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, width));
}

function loadSidebarWidth(): number {
  const stored = Number(localStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0 ? clampSidebarWidth(stored) : SIDEBAR_DEFAULT_WIDTH;
}

// Right-hand editor panel: drag-mode legend, selected-block preview + clip-plane sliders,
// shortcut bar, search, and the block palette. Shared verbatim by the Instance and Scene editors —
// this is pure presentation over props, no volume-specific (chunk vs scene buffer) logic.
export function VoxelEditorSidebar({
  modKeys,
  mode,
  onToggleSelect,
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
  clipboardCount,
  savedSelections,
  onAddSelectionToMemory,
  onSelectSavedSelection,
  onRemoveSavedSelection,
  activeDragMode,
  selectedType,
  blockDefsReady,
  previewsReady,
  previewProgress,
  getOrdinal,
  blockDefs,
  plainColors,
  selectedColorIndex,
  setSelectedColorIndex,
  clipPlanes,
  clipBounds,
  setClipPlanes,
  shortcutBar,
  hoveredShortcutSlot,
  setHoveredShortcutSlot,
  getPreview,
  search,
  setSearch,
  hoveredBlockName,
  hoveredRect,
  setHoveredBlockName,
  setHoveredRect,
  selectBlockType,
  paletteRef,
}: {
  modKeys: { shift: boolean; meta: boolean; alt: boolean; ctrl: boolean };
  mode: "place" | "break" | "select";
  onToggleSelect: () => void;
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
  clipboardCount: number | null;
  savedSelections: { shape: SelectionShape; box: SelectionBox }[];
  onAddSelectionToMemory: () => void;
  onSelectSavedSelection: (index: number) => void;
  onRemoveSavedSelection: (index: number) => void;
  activeDragMode: dragMode;
  selectedType: string | null;
  blockDefsReady: boolean;
  previewsReady: boolean;
  previewProgress: number;
  getOrdinal: (name: string) => number | null;
  blockDefs: BlockInfoDto[];
  plainColors: PlainColorDto[];
  selectedColorIndex: number;
  setSelectedColorIndex: (index: number) => void;
  clipPlanes: Record<ClipAxis, ClipPlaneState>;
  clipBounds: Record<ClipAxis, readonly [number, number]>;
  setClipPlanes: Dispatch<SetStateAction<Record<ClipAxis, ClipPlaneState>>>;
  shortcutBar: ReturnType<typeof useAdminShortcutBar>;
  hoveredShortcutSlot: number | null;
  setHoveredShortcutSlot: (idx: number | null) => void;
  getPreview: (ordinal: number) => string | null;
  search: string;
  setSearch: (value: string) => void;
  hoveredBlockName: string | null;
  hoveredRect: DOMRect | null;
  setHoveredBlockName: (name: string | null) => void;
  setHoveredRect: (rect: DOMRect | null) => void;
  selectBlockType: (name: string) => void;
  paletteRef?: RefObject<HTMLDivElement | null>;
}) {
  const [width, setWidth] = useState(loadSidebarWidth);
  const resizeStateRef = useRef<{ startX: number; startWidth: number } | null>(null);

  useEffect(() => {
    localStorage.setItem(SIDEBAR_WIDTH_STORAGE_KEY, String(width));
  }, [width]);

  useEffect(() => {
    function onMouseMove(e: MouseEvent) {
      const state = resizeStateRef.current;
      if (!state) return;
      // Handle sits on the left edge — dragging left (negative clientX delta) widens the panel.
      setWidth(clampSidebarWidth(state.startWidth + (state.startX - e.clientX)));
    }
    function onMouseUp() {
      if (!resizeStateRef.current) return;
      resizeStateRef.current = null;
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      // The canvas's CSS size already tracks the sidebar width live via flexbox, but Babylon only
      // recomputes its internal render size/aspect ratio on a window "resize" event — dispatch one
      // so the viewport (and the gizmo, which reads engine.getRenderWidth/Height) picks up the new
      // canvas dimensions once the drag settles, instead of staying stretched to the old aspect.
      window.dispatchEvent(new Event("resize"));
    }
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
    };
  }, []);

  function handleResizeStart(e: React.MouseEvent) {
    e.preventDefault();
    resizeStateRef.current = { startX: e.clientX, startWidth: width };
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  }

  return (
    <aside className="relative shrink-0 border-l border-[#2E3A4E] overflow-y-auto flex flex-col" style={{ width }}>
      <div
        onMouseDown={handleResizeStart}
        className="absolute left-0 top-0 bottom-0 w-1.5 -translate-x-1/2 cursor-col-resize z-10 hover:bg-[#3C50E0]/50"
      />
      <div className="relative top-2 left-1/2 -translate-x-1/2 flex gap-1.5 pointer-events-none items-center justify-center">
        {(
          [
            { key: "place", label: modKeys.shift || mode === "break" ? "Break" : "Place", hint: "⇧" },
            { key: "zoom", label: "Zoom", hint: "⌃" },
            { key: "pan", label: "Pan", hint: "⌥" },
            { key: "rotate", label: "Rotate", hint: "⌘" },
          ] as const
        ).map((m) => (
          <DragMode
            key={m.key}
            m={m}
            // Select mode has no place/break behavior — clicks pick voxels for selection instead —
            // so the "Place/Break" chip must not read active alongside the "Select" chip below,
            // even though activeDragMode defaults to "place" whenever no camera modifier is held.
            activeDragMode={mode === "select" && activeDragMode === "place" ? null : activeDragMode}
            // Clicking "Place/Break" while in select mode leaves select, mirroring onToggleSelect's
            // own select<->place toggle. Only wired for the "place" chip — zoom/pan/rotate are
            // camera-modifier indicators with no mode of their own to switch to.
            onClick={m.key === "place" && mode === "select" ? onToggleSelect : undefined}
          />
        ))}
        <button
          onClick={onToggleSelect}
          className={`px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto ${
            mode === "select" ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
          }`}
        >
          Select
        </button>
      </div>
      {mode === "select" && (
        <div className="relative top-2 left-1/2 -translate-x-1/2 flex gap-1.5 pointer-events-none items-center justify-center mt-1">
          {SELECTION_SHAPES.map((s) => (
            <button
              key={s.key}
              onClick={() => onSelectShape(s.key)}
              className={`px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto ${
                selectionShape === s.key ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>
      )}
      {mode === "select" && (
        <div className="relative top-2 left-1/2 -translate-x-1/2 flex gap-1.5 pointer-events-none items-center justify-center mt-1">
          {SELECTION_SNAPS.map((s) => (
            <button
              key={s.key}
              onClick={() => onSelectSnap(s.key)}
              className={`px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto ${
                selectionSnap === s.key ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>
      )}
      {mode === "select" && (
        <div className="relative top-2 left-1/2 -translate-x-1/2 flex gap-1.5 pointer-events-none items-center justify-center mt-1">
          <button
            onClick={onContractSelection}
            disabled={!hasSelection}
            title="Contract selection"
            className="px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
          >
            −
          </button>
          {RESIZE_STEPS.map((s) => (
            <button
              key={s.key}
              onClick={() => onSelectResizeStep(s.key)}
              className={`px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto ${
                resizeStep === s.key ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
              }`}
            >
              {s.label}
            </button>
          ))}
          <button
            onClick={onExpandSelection}
            disabled={!hasSelection}
            title="Expand selection"
            className="px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
          >
            +
          </button>
        </div>
      )}
      {mode === "select" && selection && (
        <div className="relative top-2 left-1/2 -translate-x-1/2 flex flex-col items-center gap-1 mt-1">
          <div className="flex items-center gap-1 pointer-events-auto">
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
                className="w-12 rounded bg-black/50 border border-[#2E3A4E] px-1 py-0.5 text-[9px] text-white font-mono outline-none focus:border-[#3C50E0]"
              />
            ))}
          </div>
          <div className="flex items-center gap-1 pointer-events-auto">
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
                className="w-12 rounded bg-black/50 border border-[#2E3A4E] px-1 py-0.5 text-[9px] text-white font-mono outline-none focus:border-[#3C50E0]"
              />
            ))}
          </div>
        </div>
      )}
      {mode === "select" && (
        <div className="relative top-2 left-1/2 -translate-x-1/2 flex gap-1.5 pointer-events-none items-center justify-center mt-1">
          {([0, 1] as const).map((slot) => {
            const name = patternBlocks[slot];
            const ordinal = name ? getOrdinal(name) : null;
            return (
              <div
                key={slot}
                onClick={() => onSelectPatternSlot(slot)}
                title={name ?? (slot === 0 ? "Pattern block A" : "Pattern block B (optional)")}
                className={`pointer-events-auto flex items-center gap-1 rounded px-1.5 py-1 cursor-pointer border-2 ${
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
      )}
      {mode === "select" && (
        <div className="relative top-2 left-1/2 -translate-x-1/2 flex flex-col items-center gap-1 pointer-events-none mt-1">
          <div className="flex gap-1.5 items-center justify-center">
            <button
              onClick={onFill}
              disabled={!hasSelection || !patternBlocks[0]}
              className="px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
            >
              Fill
            </button>
            <button
              onClick={onShell}
              disabled={!hasSelection || !patternBlocks[0]}
              className="px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
            >
              Shell
            </button>
            <button
              onClick={onCut}
              disabled={!hasSelection}
              className="px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
            >
              Cut
            </button>
          </div>
          {clipboardCount !== null && (
            <span className="text-[9px] text-[#8A99AF] pointer-events-none">Clipboard: {clipboardCount} blocks</span>
          )}
        </div>
      )}
      {mode === "select" && (
        <div className="shrink-0 border-b border-[#2E3A4E] p-2 flex flex-col gap-1">
          <div className="flex items-center justify-between">
            <span className="text-[9px] text-[#8A99AF] font-medium">
              Saved selections ({savedSelections.length}/10)
            </span>
            <button
              onClick={onAddSelectionToMemory}
              disabled={!hasSelection}
              className="px-2 py-0.5 rounded text-[10px] font-medium transition-colors bg-black/50 text-[#8A99AF] enabled:hover:bg-[#3C50E0] enabled:hover:text-white disabled:opacity-40"
            >
              + Add selection to memory
            </button>
          </div>
          {savedSelections.length > 0 && (
            <div className="flex flex-col gap-0.5">
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
                    className="text-[#8A99AF] hover:text-white text-[10px] leading-none shrink-0 ml-1"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
      <div className="shrink-0 border-b border-[#2E3A4E] flex flex-row items-center gap-3 py-3 px-2">
        <div className="flex flex-col gap-1 w-2/3 items-center justify-center">
          {selectedType && blockDefsReady && previewsReady && getOrdinal(selectedType) !== null ? (
            <Block3DPreview
              ordinal={getOrdinal(selectedType)!}
              size={96}
              colorHex={selectedColorIndex > 0 ? plainColors[selectedColorIndex - 1]?.hex : undefined}
            />
          ) : (
            <div className="h-24 w-24 flex items-center justify-center text-[#8A99AF] text-xs">
              {selectedType ? "Loading…" : "No block selected"}
            </div>
          )}
          {selectedType && <span className="text-white text-xs font-medium">{selectedType.replace(/_/g, " ")}</span>}
          {selectedType && blockDefs.find((b) => b.name === selectedType)?.plainColorable && (
            <VoxelColorPicker
              colors={plainColors}
              selectedIndex={selectedColorIndex}
              onSelect={setSelectedColorIndex}
            />
          )}
        </div>
        <div className="flex flex-col gap-1.5 w-1/3 min-w-0">
          {CLIP_AXES.map((axis) => (
            <ClipAxesInput
              key={axis}
              axis={axis}
              clipPlanes={clipPlanes}
              clipBounds={clipBounds}
              setClipPlanes={setClipPlanes}
            />
          ))}
        </div>
      </div>
      <div className="shrink-0 border-b border-[#2E3A4E] p-2">
        <div className="grid grid-cols-5 gap-1 justify-center">
          {shortcutBar.slots.map((slotBlock, idx) => (
            <VoxelShortcutBarSlot
              key={idx}
              shortcutBar={shortcutBar}
              idx={idx}
              slotBlock={slotBlock}
              getOrdinal={getOrdinal}
              blockDefs={blockDefs}
              getPreview={getPreview}
              blockDefsReady={blockDefsReady}
              previewsReady={previewsReady}
              hovered={hoveredShortcutSlot === idx}
              onHoverEnter={() => setHoveredShortcutSlot(idx)}
              onHoverLeave={() => setHoveredShortcutSlot(null)}
            />
          ))}
        </div>
        {shortcutBar.pageCount > 1 && (
          <div className="flex gap-1 justify-center mt-1.5">
            {Array.from({ length: shortcutBar.pageCount }, (index, p) => (
              <button
                key={p}
                onClick={() => shortcutBar.goToPage(p)}
                className={`w-4.5 h-6 rounded ${p === shortcutBar.currentPage ? "bg-[#3C50E0]" : "bg-white/25"}`}
              >
                {p + 1}
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="shrink-0 border-b border-[#2E3A4E] p-2">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search blocks…"
          className="w-full rounded bg-black/30 border border-[#2E3A4E] px-2 py-1 text-xs text-white placeholder:text-[#8A99AF] outline-none focus:border-[#3C50E0]"
        />
      </div>
      {!previewsReady && (
        <div className="h-0.5 shrink-0 bg-white/10">
          <div
            className="h-full bg-[#3C50E0] transition-[width]"
            style={{ width: `${Math.round(previewProgress * 100)}%` }}
          />
        </div>
      )}
      <div ref={paletteRef} className="flex flex-wrap gap-1 p-2 overflow-y-auto content-start justify-between">
        {blockDefs
          .filter((b) => b.name.toLowerCase().includes(search.toLowerCase()))
          .map((b) => (
            <VoxelPaletteBlock
              key={b.name}
              block={b}
              ordinal={getOrdinal(b.name)}
              selected={selectedType === b.name}
              getPreview={getPreview}
              blockDefsReady={blockDefsReady}
              previewsReady={previewsReady}
              hovered={hoveredBlockName === b.name}
              anchorRect={hoveredBlockName === b.name ? hoveredRect : null}
              onClick={() => selectBlockType(b.name)}
              onMouseEnter={(e) => {
                setHoveredRect(e.currentTarget.getBoundingClientRect());
                setHoveredBlockName(b.name);
              }}
              onMouseLeave={() => setHoveredBlockName(null)}
            />
          ))}
      </div>
    </aside>
  );
}
