import { useEffect, useRef, useState, type Dispatch, type RefObject, type SetStateAction } from "react";
import { type BlockInfoDto, type PlainColorDto } from "../../../apiTypes";
import { Block3DPreview } from "../../../../game/shared/Block3DPreview";
import { VoxelPaletteBlock } from "./VoxelPaletteBlock";
import { VoxelColorPicker } from "./VoxelColorPicker";
import { type useAdminShortcutBar } from "./useAdminShortcutBar";
import { ClipAxis, ClipPlaneState } from "./clipAxis";
import { dragMode, DragMode } from "../../../../primitives/DragMode";
import { type SelectionBox, type SelectionShape, type SelectionSnap } from "./selectionGizmo";
import { type PasteTransform } from "./pasteTransform";
import { type PasteOrigin } from "./pasteGizmo";
import { VoxelSelectionFloatingPanel } from "./VoxelSelectionFloatingPanel";
import { VoxelClipPlanesFloatingPanel } from "./VoxelClipPlanesFloatingPanel";
import { ShortcutBarIcons } from "./ShortcutBarIcons";
import { ShortcutBarPageSelector } from "./ShortcutBarPageSelector";

export type SelectionField = "minX" | "minY" | "minZ" | "sizeX" | "sizeY" | "sizeZ";

export interface SelectionPanelProps {
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
}

const CLIP_PLANES_VISIBLE_STORAGE_KEY = "voxelEditorClipPlanesVisible";

function loadClipPlanesVisible(): boolean {
  return localStorage.getItem(CLIP_PLANES_VISIBLE_STORAGE_KEY) !== "false";
}

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
}: SelectionPanelProps & {
  modKeys: { shift: boolean; meta: boolean; alt: boolean; ctrl: boolean };
  mode: "place" | "break" | "select";
  onToggleSelect: () => void;
  activeDragMode: dragMode;
  selectedType: string | null;
  blockDefsReady: boolean;
  previewsReady: boolean;
  previewProgress: number;
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
  const [showClipPlanes, setShowClipPlanes] = useState(loadClipPlanesVisible);
  const resizeStateRef = useRef<{ startX: number; startWidth: number } | null>(null);

  useEffect(() => {
    localStorage.setItem(CLIP_PLANES_VISIBLE_STORAGE_KEY, String(showClipPlanes));
  }, [showClipPlanes]);

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
        <DragMode
          m={{ key: "place", label: modKeys.shift || mode === "break" ? "Break" : "Place", hint: "⇧" }}
          // Select mode has no place/break behavior — clicks pick voxels for selection instead —
          // so the "Place/Break" chip must not read active alongside the "Select" chip below,
          // even though activeDragMode defaults to "place" whenever no camera modifier is held.
          activeDragMode={mode === "select" && activeDragMode === "place" ? null : activeDragMode}
          // Clicking "Place/Break" while in select mode leaves select, mirroring onToggleSelect's
          // own select<->place toggle.
          onClick={mode === "select" ? onToggleSelect : undefined}
        />
        <button
          onClick={onToggleSelect}
          className={`px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto ${
            mode === "select" ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
          }`}
        >
          Select
        </button>
        <button
          onClick={() => setShowClipPlanes((v) => !v)}
          className={`px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto ${
            showClipPlanes ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
          }`}
        >
          Clip Pane
        </button>
      </div>
      {mode === "select" && (
        <VoxelSelectionFloatingPanel
          selectionShape={selectionShape}
          onSelectShape={onSelectShape}
          selectionSnap={selectionSnap}
          onSelectSnap={onSelectSnap}
          hasSelection={hasSelection}
          selection={selection}
          onSelectionFieldChange={onSelectionFieldChange}
          resizeStep={resizeStep}
          onSelectResizeStep={onSelectResizeStep}
          onExpandSelection={onExpandSelection}
          onContractSelection={onContractSelection}
          patternBlocks={patternBlocks}
          activePatternSlot={activePatternSlot}
          onSelectPatternSlot={onSelectPatternSlot}
          onClearPatternSlot={onClearPatternSlot}
          onFill={onFill}
          onShell={onShell}
          onCut={onCut}
          onCopy={onCopy}
          clipboardCount={clipboardCount}
          onPaste={onPaste}
          isPasting={isPasting}
          onConfirmPaste={onConfirmPaste}
          onCancelPaste={onCancelPaste}
          onRotatePaste={onRotatePaste}
          onFlipPaste={onFlipPaste}
          pasteTransform={pasteTransform}
          pasteOrigin={pasteOrigin}
          onMovePasteOrigin={onMovePasteOrigin}
          savedSelections={savedSelections}
          onAddSelectionToMemory={onAddSelectionToMemory}
          onSelectSavedSelection={onSelectSavedSelection}
          onRemoveSavedSelection={onRemoveSavedSelection}
          getOrdinal={getOrdinal}
          previewsReady={previewsReady}
          blockDefsReady={blockDefsReady}
          getPreview={getPreview}
        />
      )}
      {showClipPlanes && (
        <VoxelClipPlanesFloatingPanel clipPlanes={clipPlanes} clipBounds={clipBounds} setClipPlanes={setClipPlanes} />
      )}
      <div className="shrink-0 border-b border-[#2E3A4E] flex flex-row items-center gap-3 py-3 px-2">
        <div className="flex flex-col gap-1 w-full items-center justify-center">
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
      </div>
      <div className="shrink-0 border-b border-[#2E3A4E] p-2">
        <ShortcutBarIcons
          shortcutBar={shortcutBar}
          getOrdinal={getOrdinal}
          blockDefs={blockDefs}
          getPreview={getPreview}
          blockDefsReady={blockDefsReady}
          previewsReady={previewsReady}
          hoveredShortcutSlot={hoveredShortcutSlot}
          setHoveredShortcutSlot={setHoveredShortcutSlot}
        />
        {shortcutBar.pageCount > 1 && <ShortcutBarPageSelector shortcutBar={shortcutBar} />}
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
