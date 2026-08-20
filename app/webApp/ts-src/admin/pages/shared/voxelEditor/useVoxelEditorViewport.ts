import { useEffect, useRef, useState } from "react";
import {
  useAllBlockPreviewsReady,
  useBlockDefsReady,
  useBlockPreviewProgress,
  useBlockPreviews,
} from "../../../../game/shared/BlockPreview";
import { startPreloading } from "../../../../game/shared/blockPreviewCache";
import { useAdminShortcutBar, type InstanceShortcutSlot } from "./useAdminShortcutBar";
import { applyClipPlanes, type ClipAxis, type ClipPlaneState } from "./clipAxis";
import { createSelectionGizmo, type SelectionBox, type SelectionShape, type SelectionSnap } from "./selectionGizmo";
import { createPasteGizmo, type PasteOrigin } from "./pasteGizmo";
import { createRailTestCart } from "./railTestCart";
import { createRailSwitchMarkers } from "./railSwitchMarkers";
import { IDENTITY_PASTE_TRANSFORM, type PasteTransform } from "./pasteTransform";
import { buildBlockPreviewMeshes } from "../../../../game/lib/chunkBuilder";
import { useBlockRegistry } from "./useBlockRegistry";
import { useModifierDragMode } from "./useModifierDragMode";
import { useActionError } from "./useActionError";
import { type UndoGroup } from "./undoRedoStack";
import { type SelectionField } from "./VoxelEditorSidebar";
import { resizeSelectionBox } from "./selectionVoxels";
import type { VoxelBlockLike } from "./voxelVolumeAdapter";

// Saved-selection memory list cap (select mode) — same idea as MAX_UNDO_ENTRIES, a fixed-size
// local buffer with oldest entries dropped once full.
const MAX_SAVED_SELECTIONS = 10;

export interface UseVoxelEditorViewportParams {
  volumeId: string;
  clipBounds: Record<ClipAxis, readonly [number, number]>;
  initialClipPlanes: Record<ClipAxis, ClipPlaneState>;
  initialShortcutBarPages?: InstanceShortcutSlot[][];
}

// All state/refs/handlers shared verbatim between InstanceEditorViewport and SceneEditorViewport:
// place/break/select modes, paste, undo/redo bookkeeping, rail-test state machine, clip planes,
// saved selections. The genuinely different parts (Babylon scene setup, geometry loading, wasm
// export calls) stay in each component and in voxelEditorSceneController.ts, which consumes the
// refs/setters this hook returns.
export function useVoxelEditorViewport(params: UseVoxelEditorViewportParams) {
  const { clipBounds, initialClipPlanes, initialShortcutBarPages } = params;

  const [hoveredBlockName, setHoveredBlockName] = useState<string | null>(null);
  const [hoveredRect, setHoveredRect] = useState<DOMRect | null>(null);
  const [hoveredShortcutSlot, setHoveredShortcutSlot] = useState<number | null>(null);
  const [hoveredVoxelName, setHoveredVoxelName] = useState<string | null>(null);
  const { blockDefs, ordinalByName, nameByOrdinal, plainColors, getOrdinal } = useBlockRegistry();
  const [selectedType, setSelectedType] = useState<string | null>(null);
  const [selectedColorIndex, setSelectedColorIndex] = useState(0);
  const selectedColorIndexRef = useRef(selectedColorIndex);
  const [mode, setMode] = useState<"place" | "break" | "select">("place");
  const [selection, setSelection] = useState<SelectionBox | null>(null);
  const selectionRef = useRef<SelectionBox | null>(selection);
  const [selectionShape, setSelectionShape] = useState<SelectionShape>("box");
  const selectionShapeRef = useRef(selectionShape);
  const [selectionSnap, setSelectionSnap] = useState<SelectionSnap>("none");
  const selectionSnapRef = useRef(selectionSnap);
  // Fill/Shell pattern (block A, optional block B for a checkerboard alternation) and which slot a
  // palette click assigns to while in select mode — see selectBlockType below.
  const [patternBlocks, setPatternBlocks] = useState<[string | null, string | null]>([null, null]);
  const patternBlocksRef = useRef(patternBlocks);
  const [activePatternSlot, setActivePatternSlot] = useState<0 | 1>(0);
  const activePatternSlotRef = useRef(activePatternSlot);
  // Internal clipboard for Cut — relative to the cut selection's min corner. In-memory only, no
  // paste yet. clipboardCount is just the sidebar's "Clipboard: N blocks" display.
  const clipboardRef = useRef<{
    entries: { relX: number; relY: number; relZ: number; type: string; state: number }[];
  } | null>(null);
  const [clipboardCount, setClipboardCount] = useState<number | null>(null);
  // Paste-in-progress state: pasteOriginRef is the live drag position (moved directly by the
  // gizmo's drag callback), mirrored into pasteOrigin React state so the sidebar's X/Y/Z inputs
  // stay in sync with drag moves and vice versa.
  const pasteOriginRef = useRef<PasteOrigin | null>(null);
  const [pasteOrigin, setPasteOrigin] = useState<PasteOrigin | null>(null);
  const [isPasting, setIsPasting] = useState(false);
  const isPastingRef = useRef(false);
  const pasteGizmoRef = useRef<ReturnType<typeof createPasteGizmo> | null>(null);
  const pastePreviewMeshesRef = useRef<ReturnType<typeof buildBlockPreviewMeshes>>([]);
  // Rotate/flip applied to the clipboard before Confirm — reactive (not just a ref) so the
  // sidebar's Flip X/Y/Z buttons can show which axes are active.
  const [pasteTransform, setPasteTransform] = useState<PasteTransform>(IDENTITY_PASTE_TRANSFORM);
  const pasteTransformRef = useRef(pasteTransform);
  const pasteActionsRef = useRef<{
    start: () => void;
    confirm: () => void;
    cancel: () => void;
    rotate: (dir: -1 | 1) => void;
    flip: (axis: "x" | "y" | "z") => void;
    move: (origin: PasteOrigin) => void;
  }>({
    start: () => {},
    confirm: () => {},
    cancel: () => {},
    rotate: () => {},
    flip: () => {},
    move: () => {},
  });
  // Local (per-browser-tab, not persisted) list of remembered selections — click a row to restore
  // it as the active selection, capped at MAX_SAVED_SELECTIONS with oldest dropped once full.
  const [savedSelections, setSavedSelections] = useState<{ shape: SelectionShape; box: SelectionBox }[]>([]);
  // Step used by the sidebar's expand/contract selection buttons — voxel/half/quarter, same
  // granularity as RESIZE_STEPS.
  const [resizeStep, setResizeStep] = useState<number>(1);
  const [loadError, setLoadError] = useState<string | null>(null);
  const { actionError, flashActionError } = useActionError();
  const [search, setSearch] = useState("");
  // Rail circuit test cart (see railTestCart.ts) — "picking" means the next viewport click on a
  // rail block starts the test; "running" means the cart is currently traveling the network.
  // Independent of place/break/select mode, so testRailStateRef only gates the pointer
  // controller's click-interception path, not modeRef's own place/break/select dispatch.
  const [testRailState, setTestRailState] = useState<"idle" | "picking" | "running">("idle");
  const testRailStateRef = useRef(testRailState);
  const railTestCartRef = useRef<ReturnType<typeof createRailTestCart> | null>(null);
  // Switch/junction overlay markers (see railSwitchMarkers.ts) — shown for the whole "picking"/
  // "running" duration of a rail test, refreshed after every reload so a toggled branch's label
  // reflects the persisted state. refreshJunctionsRef is set from the scene-setup effect (it needs
  // wasmExports/scene, both local to that effect) so this function-scoped toggleTestRail can still
  // trigger it.
  const railSwitchMarkersRef = useRef<ReturnType<typeof createRailSwitchMarkers> | null>(null);
  const refreshJunctionsRef = useRef<() => void>(() => {});
  useEffect(() => {
    testRailStateRef.current = testRailState;
  }, [testRailState]);

  const modeRef = useRef(mode);
  const selectedTypeRef = useRef(selectedType);
  const ordinalByNameRef = useRef(ordinalByName);
  const nameByOrdinalRef = useRef(nameByOrdinal);
  // In-memory only (reset on volume remount, like the scene itself) — not persisted, unlike
  // camera/shortcut bar. Each stack slot is a GROUP of entries (see undoRedoStack.ts) — a single
  // place/break is a group of one, a Fill/Shell/Cut is a group of every voxel it touched.
  const undoStackRef = useRef<UndoGroup<VoxelBlockLike>[]>([]);
  const redoStackRef = useRef<UndoGroup<VoxelBlockLike>[]>([]);
  const runSelectionOpRef = useRef<(kind: "fill" | "shell" | "cut" | "copy") => void>(() => {});

  const [clipPlanes, setClipPlanes] = useState<Record<ClipAxis, ClipPlaneState>>(initialClipPlanes);
  const clipPlanesRef = useRef(clipPlanes);
  useEffect(() => {
    clipPlanesRef.current = clipPlanes;
  }, [clipPlanes]);
  // Bridges the scene/overlay-mesh set owned by each component's scene-setup effect to the
  // clip-plane sync effect below and the sidebar handlers, which run outside that effect's closure.
  const sceneRef = useRef<InstanceType<typeof BABYLON.Scene> | null>(null);
  const overlayMeshesRef = useRef<Set<InstanceType<typeof BABYLON.Mesh>> | null>(null);
  const clipMeshesRef = useRef<Partial<Record<ClipAxis, InstanceType<typeof BABYLON.Mesh>>>>({});
  const selectionGizmoRef = useRef<ReturnType<typeof createSelectionGizmo> | null>(null);

  // The eslint react-hooks/immutability rule forbids a component writing `v.someRef.current = x`
  // directly, since `v` is a hook's return value — these setters wrap that assignment inside the
  // hook itself so the scene-setup effect (which owns the actual Babylon objects) can still hand
  // them off to this hook's bridge refs without tripping the rule. Reading `.current` from the
  // component/controller is unaffected — only writes from outside the hook are the problem.
  function setScene(scene: InstanceType<typeof BABYLON.Scene> | null) {
    sceneRef.current = scene;
  }
  function setOverlayMeshes(meshes: Set<InstanceType<typeof BABYLON.Mesh>> | null) {
    overlayMeshesRef.current = meshes;
  }
  function setClipMeshes(meshes: Partial<Record<ClipAxis, InstanceType<typeof BABYLON.Mesh>>>) {
    clipMeshesRef.current = meshes;
  }
  function setSelectionGizmoInstance(gizmo: ReturnType<typeof createSelectionGizmo> | null) {
    selectionGizmoRef.current = gizmo;
  }
  function setPasteGizmoInstance(gizmo: ReturnType<typeof createPasteGizmo> | null) {
    pasteGizmoRef.current = gizmo;
  }
  function setRailTestCartInstance(cart: ReturnType<typeof createRailTestCart> | null) {
    railTestCartRef.current = cart;
  }
  function setRailSwitchMarkersInstance(markers: ReturnType<typeof createRailSwitchMarkers> | null) {
    railSwitchMarkersRef.current = markers;
  }
  function setRefreshJunctionsImpl(fn: () => void) {
    refreshJunctionsRef.current = fn;
  }
  function setPasteActionsImpl(actions: typeof pasteActionsRef.current) {
    pasteActionsRef.current = actions;
  }
  function setRunSelectionOpImpl(fn: (kind: "fill" | "shell" | "cut" | "copy") => void) {
    runSelectionOpRef.current = fn;
  }
  // Mirrors the paste gizmo's own live drag position — pasteActions.start/move already update
  // this internally; only the raw pasteGizmo drag callback (wired directly in each component,
  // outside pasteActions) needs this setter.
  function setPasteOriginRefValue(origin: PasteOrigin | null) {
    pasteOriginRef.current = origin;
  }

  useEffect(() => {
    startPreloading();
  }, []);

  useEffect(() => {
    modeRef.current = mode;
    if (mode !== "select") {
      setSelection(null);
      setSelectionShape("box");
      setSelectionSnap("none");
      pasteActionsRef.current.cancel();
    } else {
      // Seeds select mode with a live starting point (a bare toggle previously left the gizmo
      // hidden until the user made a manual pick) — a single voxel centered on the volume's
      // barycenter, i.e. the midpoint of clipBounds along each axis.
      const cx = Math.floor((clipBounds.x[0] + clipBounds.x[1]) / 2);
      const cy = Math.floor((clipBounds.y[0] + clipBounds.y[1]) / 2);
      const cz = Math.floor((clipBounds.z[0] + clipBounds.z[1]) / 2);
      setSelection({ minX: cx, minY: cy, minZ: cz, maxX: cx + 1, maxY: cy + 1, maxZ: cz + 1 });
    }
  }, [mode, clipBounds]);

  useEffect(() => {
    selectionShapeRef.current = selectionShape;
    selectionGizmoRef.current?.setShape(selectionShape);
  }, [selectionShape]);

  useEffect(() => {
    selectionSnapRef.current = selectionSnap;
    selectionGizmoRef.current?.setSnap(selectionSnap);
  }, [selectionSnap]);
  useEffect(() => {
    patternBlocksRef.current = patternBlocks;
  }, [patternBlocks]);
  useEffect(() => {
    activePatternSlotRef.current = activePatternSlot;
  }, [activePatternSlot]);

  function toggleSelectMode() {
    setMode((m) => (m === "select" ? "place" : "select"));
  }
  useEffect(() => {
    selectedTypeRef.current = selectedType;
  }, [selectedType]);
  useEffect(() => {
    ordinalByNameRef.current = ordinalByName;
  }, [ordinalByName]);
  useEffect(() => {
    nameByOrdinalRef.current = nameByOrdinal;
  }, [nameByOrdinal]);
  useEffect(() => {
    selectedColorIndexRef.current = selectedColorIndex;
  }, [selectedColorIndex]);

  // Resets any color pick made for a previous block — a color index only makes sense paired with
  // the plainColorable block it was chosen for. In select mode, a palette click assigns the
  // Fill/Shell pattern slot instead of switching to place mode.
  function selectBlockType(name: string) {
    if (modeRef.current === "select") {
      setPatternBlocks((prev) => {
        const next: [string | null, string | null] = [...prev];
        next[activePatternSlotRef.current] = name;
        return next;
      });
      return;
    }
    setMode("place");
    setSelectedType(name);
    setSelectedColorIndex(0);
  }

  function clearPatternSlot(slot: 0 | 1) {
    setPatternBlocks((prev) => {
      const next: [string | null, string | null] = [...prev];
      next[slot] = null;
      return next;
    });
  }

  function runSelectionOp(kind: "fill" | "shell" | "cut" | "copy") {
    runSelectionOpRef.current(kind);
  }

  function startPaste() {
    pasteActionsRef.current.start();
  }

  function confirmPaste() {
    pasteActionsRef.current.confirm();
  }

  function cancelPaste() {
    pasteActionsRef.current.cancel();
  }

  function rotatePaste(dir: -1 | 1) {
    pasteActionsRef.current.rotate(dir);
  }

  function flipPaste(axis: "x" | "y" | "z") {
    pasteActionsRef.current.flip(axis);
  }

  function movePasteOrigin(field: "x" | "y" | "z", value: number) {
    const origin = pasteOriginRef.current;
    if (!origin || !Number.isFinite(value)) return;
    pasteActionsRef.current.move({ ...origin, [field]: value });
  }

  function addSelectionToMemory() {
    const box = selectionRef.current;
    if (!box) return;
    setSavedSelections((prev) => {
      const next = [...prev, { shape: selectionShapeRef.current, box }];
      return next.length > MAX_SAVED_SELECTIONS ? next.slice(next.length - MAX_SAVED_SELECTIONS) : next;
    });
  }

  function selectSavedSelection(index: number) {
    const entry = savedSelections[index];
    if (!entry) return;
    setSelectionShape(entry.shape);
    setSelection(entry.box);
  }

  function removeSavedSelection(index: number) {
    setSavedSelections((prev) => prev.filter((_, i) => i !== index));
  }

  function expandSelection() {
    const box = selectionRef.current;
    if (!box) return;
    setSelection(resizeSelectionBox(box, selectionShapeRef.current, clipBounds, resizeStep));
  }

  function contractSelection() {
    const box = selectionRef.current;
    if (!box) return;
    setSelection(resizeSelectionBox(box, selectionShapeRef.current, clipBounds, -resizeStep));
  }

  // Direct edit of the position/size widget. minX/minY/minZ move the box's min corner (size
  // unchanged); sizeX/sizeY/sizeZ resize from that same min corner (position unchanged) — max* is
  // always derived, never edited directly.
  function setSelectionField(field: SelectionField, value: number) {
    const box = selectionRef.current;
    if (!box) return;
    let next: SelectionBox;
    switch (field) {
      case "minX":
        next = { ...box, minX: value, maxX: value + (box.maxX - box.minX) };
        break;
      case "minY":
        next = { ...box, minY: value, maxY: value + (box.maxY - box.minY) };
        break;
      case "minZ":
        next = { ...box, minZ: value, maxZ: value + (box.maxZ - box.minZ) };
        break;
      case "sizeX":
        next = { ...box, maxX: box.minX + Math.max(0.25, value) };
        break;
      case "sizeY":
        next = { ...box, maxY: box.minY + Math.max(0.25, value) };
        break;
      case "sizeZ":
        next = { ...box, maxZ: box.minZ + Math.max(0.25, value) };
        break;
    }
    setSelection(next);
  }

  const blockDefsReady = useBlockDefsReady();
  const previewsReady = useAllBlockPreviewsReady();
  const previewProgress = useBlockPreviewProgress();
  const getPreview = useBlockPreviews();

  const shortcutBar = useAdminShortcutBar({
    initialPages: initialShortcutBarPages,
    onSelectBreak: () => setMode("break"),
    onSelectBlock: (blockName) => selectBlockType(blockName),
  });
  // The pointer-controller mount effect (in each component) only re-runs on [volumeId,
  // blockDefsReady] — a ref keeps its Ctrl+click handler reading the live slots/currentPage
  // instead of the stale ones captured at mount, same rationale as selectedTypeRef/
  // ordinalByNameRef above.
  const shortcutBarRef = useRef(shortcutBar);
  useEffect(() => {
    shortcutBarRef.current = shortcutBar;
  });

  function toggleTestRail() {
    if (testRailStateRef.current === "running") {
      railTestCartRef.current?.stop();
      railSwitchMarkersRef.current?.clear();
      setTestRailState("idle");
      return;
    }
    setTestRailState((s) => {
      if (s === "picking") {
        railSwitchMarkersRef.current?.clear();
        return "idle";
      }
      refreshJunctionsRef.current();
      // Break is the natural companion tool while a rail test is active — it removes/inspects
      // track without a block type armed to accidentally place over the circuit being tested.
      shortcutBarRef.current.selectSlot(0);
      return "picking";
    });
  }

  const { modKeys, activeDragMode } = useModifierDragMode();

  // Reactive clip-plane sync: runs whenever the sidebar toggles/moves a plane, and again after
  // scene remount (volumeId/blockDefsReady) so a plane already enabled survives a volume switch.
  useEffect(() => {
    const scene = sceneRef.current;
    const overlayMeshes = overlayMeshesRef.current;
    const B = window.BABYLON;
    if (!scene || !overlayMeshes || !B) return;
    applyClipPlanes(B, scene, clipPlanes, clipBounds, overlayMeshes, clipMeshesRef.current);
  }, [clipPlanes, clipBounds, params.volumeId, blockDefsReady]);

  // Mirrors the selection state into the gizmo — null both when no selection was made yet and
  // whenever the editor leaves select mode (see the mode effect above, which clears `selection`).
  useEffect(() => {
    selectionRef.current = selection;
    selectionGizmoRef.current?.setSelection(selection);
  }, [selection]);

  return {
    hoveredBlockName,
    setHoveredBlockName,
    hoveredRect,
    setHoveredRect,
    hoveredShortcutSlot,
    setHoveredShortcutSlot,
    hoveredVoxelName,
    setHoveredVoxelName,
    blockDefs,
    ordinalByName,
    ordinalByNameRef,
    nameByOrdinal,
    nameByOrdinalRef,
    plainColors,
    getOrdinal,
    selectedType,
    selectedTypeRef,
    setSelectedType,
    selectedColorIndex,
    selectedColorIndexRef,
    setSelectedColorIndex,
    mode,
    modeRef,
    setMode,
    toggleSelectMode,
    selection,
    selectionRef,
    setSelection,
    selectionShape,
    selectionShapeRef,
    setSelectionShape,
    selectionSnap,
    selectionSnapRef,
    setSelectionSnap,
    selectionGizmoRef,
    patternBlocks,
    patternBlocksRef,
    activePatternSlot,
    activePatternSlotRef,
    setActivePatternSlot,
    selectBlockType,
    clearPatternSlot,
    setSelectionField,
    expandSelection,
    contractSelection,
    resizeStep,
    setResizeStep,
    savedSelections,
    addSelectionToMemory,
    selectSavedSelection,
    removeSavedSelection,
    clipboardRef,
    clipboardCount,
    setClipboardCount,
    pasteOriginRef,
    pasteOrigin,
    setPasteOrigin,
    isPasting,
    isPastingRef,
    setIsPasting,
    pasteGizmoRef,
    pastePreviewMeshesRef,
    pasteTransform,
    pasteTransformRef,
    setPasteTransform,
    pasteActionsRef,
    startPaste,
    confirmPaste,
    cancelPaste,
    rotatePaste,
    flipPaste,
    movePasteOrigin,
    undoStackRef,
    redoStackRef,
    runSelectionOpRef,
    runSelectionOp,
    testRailState,
    testRailStateRef,
    setTestRailState,
    railTestCartRef,
    railSwitchMarkersRef,
    refreshJunctionsRef,
    toggleTestRail,
    clipPlanes,
    clipPlanesRef,
    setClipPlanes,
    sceneRef,
    overlayMeshesRef,
    clipMeshesRef,
    setScene,
    setOverlayMeshes,
    setClipMeshes,
    setSelectionGizmoInstance,
    setPasteGizmoInstance,
    setRailTestCartInstance,
    setRailSwitchMarkersInstance,
    setRefreshJunctionsImpl,
    setPasteActionsImpl,
    setRunSelectionOpImpl,
    setPasteOriginRefValue,
    loadError,
    setLoadError,
    actionError,
    flashActionError,
    search,
    setSearch,
    modKeys,
    activeDragMode,
    shortcutBar,
    shortcutBarRef,
    blockDefsReady,
    previewsReady,
    previewProgress,
    getPreview,
  };
}

export type VoxelEditorViewportState = ReturnType<typeof useVoxelEditorViewport>;
