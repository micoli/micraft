import { useEffect, useMemo, useRef, useState } from "react";
import { putApiAdminInstancesByIdLayout } from "../../../generated/api/requests";
import type { InstanceZoneDto } from "../../apiTypes";
import {
  useAllBlockPreviewsReady,
  useBlockDefsReady,
  useBlockPreviewProgress,
  useBlockPreviews,
} from "../../../game/shared/BlockPreview";
import { startPreloading } from "../../../game/shared/blockPreviewCache";
import { useAdminShortcutBar } from "../shared/voxelEditor/useAdminShortcutBar";
import { applyClipPlanes, ClipAxis, ClipPlaneState } from "../shared/voxelEditor/clipAxis";
import { packState } from "../shared/voxelEditor/blockState";
import { saveCameraState } from "../shared/voxelEditor/cameraStorage";
import { createOrbitCamera, setupBasicLighting } from "../shared/voxelEditor/orbitCamera";
import { createAxesGizmo } from "../shared/voxelEditor/axesGizmo";
import { setupOrbitPointerController } from "../shared/voxelEditor/orbitPointerController";
import { createOverlayController } from "../shared/voxelEditor/overlayController";
import {
  createSelectionGizmo,
  type SelectionBox,
  type SelectionShape,
  type SelectionSnap,
} from "../shared/voxelEditor/selectionGizmo";
import { createPasteGizmo, type PasteOrigin } from "../shared/voxelEditor/pasteGizmo";
import { createRailTestCart, type RailTestCart } from "../shared/voxelEditor/railTestCart";
import {
  createRailSwitchMarkers,
  type RailJunction,
  type RailSwitchMarkers,
} from "../shared/voxelEditor/railSwitchMarkers";
import {
  applyPasteTransform,
  IDENTITY_PASTE_TRANSFORM,
  type PasteTransform,
} from "../shared/voxelEditor/pasteTransform";
import { buildBlockPreviewMeshes } from "../../../game/lib/chunkBuilder";
import { useBlockRegistry } from "../shared/voxelEditor/useBlockRegistry";
import { useModifierDragMode } from "../shared/voxelEditor/useModifierDragMode";
import { useActionError } from "../shared/voxelEditor/useActionError";
import { makeUndoRedoController, type UndoEntryBase, type UndoGroup } from "../shared/voxelEditor/undoRedoStack";
import { VoxelEditorSidebar, type SelectionField } from "../shared/voxelEditor/VoxelEditorSidebar";
import { ViewportCameraHud } from "../shared/voxelEditor/ViewportCameraHud";
import { HoveredVoxelNameHud } from "../shared/voxelEditor/HoveredVoxelNameHud";
import { connectEditSocket } from "../shared/voxelEditor/editSocket";
import {
  computeSelectionVoxels,
  resolvePatternBlock,
  resizeSelectionBox,
  MAX_SELECTION_OP_VOXELS,
  type Pattern,
} from "../shared/voxelEditor/selectionVoxels";
import type { InstanceBlockDto } from "../../apiTypes";
import {
  computeSlotOffset,
  resolvePlacementCell,
  usedSlotAt,
  PICK_EPSILON,
} from "../shared/voxelEditor/fractionalPlacement";

const CHUNK_SIZE = 16;
// How far (in chunks) around the camera target to keep block geometry loaded. Scales with
// zoom (camera.radius) so zooming out pulls in more chunks, capped so a huge zone can't force
// thousands of simultaneous chunk fetches.
const MAX_VIEW_RADIUS_CHUNKS = 8;
// Saved-selection memory list cap (select mode) — same idea as MAX_UNDO_ENTRIES, a fixed-size
// local buffer with oldest entries dropped once full.
const MAX_SAVED_SELECTIONS = 10;
// Hysteresis margin: a chunk is only unloaded once it's this far past the load radius, so
// camera jitter right at the boundary doesn't thrash fetch/hide every frame.
const VIEW_RADIUS_UNLOAD_MARGIN = 1;

const cameraStorageKey = (zoneId: string) => `instanceEditorCamera:${zoneId}`;

// Snapshot of a cell's content right before a place/break overwrote it, so undo can write it back.
interface UndoEntry extends UndoEntryBase {
  xOffset: number;
  zOffset: number;
}

export function InstanceEditorViewport({ zone }: { zone: InstanceZoneDto }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const paletteRef = useRef<HTMLDivElement>(null);
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
  const railTestCartRef = useRef<RailTestCart | null>(null);
  // Switch/junction overlay markers (see railSwitchMarkers.ts) — shown for the whole "picking"/
  // "running" duration of a rail test, refreshed after every reload so a toggled branch's label
  // reflects the persisted state. refreshJunctionsRef is set from inside the scene-setup effect
  // (it needs wasmExports/scene, both local to that effect) so this function-scoped
  // toggleTestRail can still trigger it.
  const railSwitchMarkersRef = useRef<RailSwitchMarkers | null>(null);
  const refreshJunctionsRef = useRef<() => void>(() => {});
  useEffect(() => {
    testRailStateRef.current = testRailState;
  }, [testRailState]);
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
      return "picking";
    });
  }
  const modeRef = useRef(mode);
  const selectedTypeRef = useRef(selectedType);
  const ordinalByNameRef = useRef(ordinalByName);
  const nameByOrdinalRef = useRef(nameByOrdinal);
  // In-memory only (reset on zone remount, like the scene itself) — not persisted, unlike camera/shortcut bar.
  // Each stack slot is a GROUP of entries (see undoRedoStack.ts) — a single place/break is a group
  // of one, a Fill/Shell/Cut is a group of every voxel it touched.
  const undoStackRef = useRef<UndoGroup<UndoEntry>[]>([]);
  const redoStackRef = useRef<UndoGroup<UndoEntry>[]>([]);
  const runSelectionOpRef = useRef<(kind: "fill" | "shell" | "cut" | "copy") => void>(() => {});

  // Clip-plane bounds derived purely from zone data (no scene dependency) — one range per axis
  // the position slider can move within.
  const clipBounds = useMemo(() => {
    const cxs = zone.chunks.map((c) => c.cx);
    const czs = zone.chunks.map((c) => c.cz);
    const minCx = Math.min(...cxs);
    const maxCx = Math.max(...cxs);
    const minCz = Math.min(...czs);
    const maxCz = Math.max(...czs);
    return {
      x: [minCx * CHUNK_SIZE, (maxCx + 1) * CHUNK_SIZE] as const,
      y: [zone.yMin, zone.yMax] as const,
      z: [minCz * CHUNK_SIZE, (maxCz + 1) * CHUNK_SIZE] as const,
    };
  }, [zone]);
  const [clipPlanes, setClipPlanes] = useState<Record<ClipAxis, ClipPlaneState>>(() => {
    const saved = zone.clipPlanes;
    return {
      x: saved?.x ?? { enabled: false, flipped: false, pos: (clipBounds.x[0] + clipBounds.x[1]) / 2 },
      y: saved?.y ?? { enabled: false, flipped: false, pos: (clipBounds.y[0] + clipBounds.y[1]) / 2 },
      z: saved?.z ?? { enabled: false, flipped: false, pos: (clipBounds.z[0] + clipBounds.z[1]) / 2 },
    };
  });
  const clipPlanesRef = useRef(clipPlanes);
  useEffect(() => {
    clipPlanesRef.current = clipPlanes;
  }, [clipPlanes]);
  // Bridges the scene/overlay-mesh set owned by the main scene-setup effect below to the
  // clip-plane sync effect and the sidebar handlers, which run outside that effect's closure.
  const sceneRef = useRef<InstanceType<typeof BABYLON.Scene> | null>(null);
  const overlayMeshesRef = useRef<Set<InstanceType<typeof BABYLON.Mesh>> | null>(null);
  const clipMeshesRef = useRef<Partial<Record<ClipAxis, InstanceType<typeof BABYLON.Mesh>>>>({});
  const selectionGizmoRef = useRef<ReturnType<typeof createSelectionGizmo> | null>(null);

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
      // hidden until the user made a manual pick) — a single voxel centered on the zone's
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
    initialPages: zone.shortcutBarPages,
    onSelectBreak: () => setMode("break"),
    onSelectBlock: (blockName) => selectBlockType(blockName),
  });
  // The pointer-controller mount effect below only re-runs on [zone.id, blockDefsReady] — a ref
  // keeps its Ctrl+click handler reading the live slots/currentPage instead of the stale ones
  // captured at mount, same rationale as selectedTypeRef/ordinalByNameRef above.
  const shortcutBarRef = useRef(shortcutBar);
  useEffect(() => {
    shortcutBarRef.current = shortcutBar;
  });

  // Persists clip-plane and shortcut-bar state onto the zone's own YAML record so it survives
  // reload/navigation, instead of the old localStorage-per-browser approach. Debounced since the
  // clip-plane sliders fire on every drag pixel. Skips the very first run (mount reflecting back
  // what the zone already has) so switching zones or reloading doesn't fire a no-op PUT.
  const layoutMounted = useRef(false);
  useEffect(() => {
    if (!layoutMounted.current) {
      layoutMounted.current = true;
      return;
    }
    const timeout = setTimeout(() => {
      putApiAdminInstancesByIdLayout({
        path: { id: zone.id },
        // shortcutBarPages entries may be null (empty slot) — the generated schema doesn't
        // preserve nested-array element nullability, but the wire format does.
        body: { clipPlanes, shortcutBarPages: shortcutBar.pages as string[][] },
        throwOnError: true,
      }).catch(console.error);
    }, 500);
    return () => clearTimeout(timeout);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- zone.id intentionally excluded, only its identity matters via the closure
  }, [clipPlanes, shortcutBar.pages]);

  const { modKeys, activeDragMode } = useModifierDragMode();

  useEffect(() => {
    setLoadError(null);
    if (zone.chunks.length === 0) {
      setLoadError("Zone has no chunks.");
      return;
    }
    if (!window.webApp) {
      setLoadError("WASM module not loaded (webApp.js missing).");
      return;
    }
    // ChunkManager.renderChunk() silently no-ops (enqueues without draining) until block defs
    // are ready — starting chunk loads before that would appear to do nothing at all.
    if (!blockDefsReady) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.06, 0.06, 0.08, 1);
    sceneRef.current = scene;
    clipMeshesRef.current = {};

    // Cached once resolved so hover/click handlers can call mcAdminGetBlockOrdinalAt
    // synchronously instead of re-awaiting the (already-resolved) module promise every frame.
    let wasmExports: Awaited<NonNullable<typeof window.webApp>> | null = null;
    window.webApp!.then((e) => {
      wasmExports = e;
    });

    const cxs = zone.chunks.map((c) => c.cx);
    const czs = zone.chunks.map((c) => c.cz);
    const minCx = Math.min(...cxs);
    const maxCx = Math.max(...cxs);
    const minCz = Math.min(...czs);
    const maxCz = Math.max(...czs);
    const centerX = ((minCx + maxCx + 1) * CHUNK_SIZE) / 2;
    const centerZ = ((minCz + maxCz + 1) * CHUNK_SIZE) / 2;
    const centerY = (zone.yMin + zone.yMax) / 2 + 0.5;
    const span = Math.max((maxCx - minCx + 1) * CHUNK_SIZE, (maxCz - minCz + 1) * CHUNK_SIZE, zone.yMax - zone.yMin, 4);

    const camera = createOrbitCamera(
      B,
      scene,
      canvas,
      cameraStorageKey(zone.id),
      { x: centerX, y: centerY, z: centerZ },
      span,
    );
    setupBasicLighting(B, scene);

    const chunkSet = new Set(zone.chunks.map((c) => `${c.cx},${c.cz}`));
    const inZone = (x: number, z: number) => {
      const cx = Math.floor(x / CHUNK_SIZE);
      const cz = Math.floor(z / CHUNK_SIZE);
      return chunkSet.has(`${cx},${cz}`);
    };

    const groundMat = new B.StandardMaterial("zoneGroundMat", scene);
    groundMat.diffuseColor = new B.Color3(0.2, 0.2, 0.25);
    groundMat.alpha = 0.35;
    groundMat.freeze();
    const groundMeshes = new Set<InstanceType<typeof BABYLON.Mesh>>();
    for (const { cx, cz } of zone.chunks) {
      const g = B.MeshBuilder.CreateGround(`ground-${cx}-${cz}`, { width: CHUNK_SIZE, height: CHUNK_SIZE }, scene);
      g.position = new B.Vector3(cx * CHUNK_SIZE + CHUNK_SIZE / 2, zone.yMin, cz * CHUNK_SIZE + CHUNK_SIZE / 2);
      g.material = groundMat;
      g.freezeWorldMatrix();
      groundMeshes.add(g);
    }

    // ── Real chunk mesh rendering, via the same WASM chunk mesher the live game uses ──────
    // (Chunk.decodeWire + ChunkManager.renderChunk) instead of a hand-rolled per-block
    // renderer — see AdminChunkPreview.kt. Each chunk is one real textured mesh, keyed
    // internally by "cx,cz"; hiding/showing is dispose+refetch rather than per-block
    // bookkeeping, since fetches are cheap (one small HTTP call per chunk).
    const chunkKeyOf = (cx: number, cz: number) => `${cx},${cz}`;
    const chunkLoading = new Set<string>();
    const visibleChunks = new Set<string>();
    // Ghost/outline overlay meshes must stay unpickable no matter how many chunk (re)loads happen
    // around them (see loadChunk below) — populated by the overlay controller below.
    const overlayMeshes = new Set<InstanceType<typeof BABYLON.Mesh>>();
    overlayMeshesRef.current = overlayMeshes;
    const overlay = createOverlayController(B, scene, overlayMeshes);
    const selectionGizmo = createSelectionGizmo(B, scene, clipBounds, (box) => setSelection(box), flashActionError);
    selectionGizmo.setShape(selectionShapeRef.current);
    selectionGizmo.setSnap(selectionSnapRef.current);
    selectionGizmoRef.current = selectionGizmo;

    // Paste preview: one buildBlockPreviewMeshes call per clipboard entry (each call renders a
    // single voxel at local origin — see chunkBuilder.ts), positioned at pasteOrigin + rel and
    // left at the ghost shader's built-in 0.5 alpha (BLOCK_GHOST_FRAG in game/lib/block.ts).
    function disposePastePreview() {
      for (const m of pastePreviewMeshesRef.current) m.dispose();
      pastePreviewMeshesRef.current = [];
    }
    function renderPastePreview(origin: PasteOrigin) {
      disposePastePreview();
      const entries = applyPasteTransform(clipboardRef.current?.entries ?? [], pasteTransformRef.current);
      const meshes: ReturnType<typeof buildBlockPreviewMeshes> = [];
      for (const entry of entries) {
        const ordinal = ordinalByNameRef.current.get(entry.type);
        if (ordinal == null) continue;
        for (const mesh of buildBlockPreviewMeshes(scene, ordinal, 0)) {
          mesh.position = new B!.Vector3(origin.x + entry.relX, origin.y + entry.relY, origin.z + entry.relZ);
          mesh.isPickable = false;
          // Same depth-bias neutralization as overlayController.ts's showGhostAndOutline — the
          // ghost shader's zOffset bias is tuned for FPS-scale render distance and misbehaves at
          // this editor's much larger orbit-camera distances (see that file's comment).
          if (mesh.material) {
            mesh.material.zOffset = 0;
            mesh.material.zOffsetUnits = 0;
            mesh.material.disableDepthWrite = true;
          }
          mesh.renderingGroupId = 1;
          meshes.push(mesh);
        }
      }
      pastePreviewMeshesRef.current = meshes;
    }
    const pasteGizmo = createPasteGizmo(B, scene, (origin) => {
      pasteOriginRef.current = origin;
      setPasteOrigin(origin);
      renderPastePreview(origin);
    });
    pasteGizmoRef.current = pasteGizmo;

    const railTestCart = createRailTestCart(
      B,
      scene,
      (wx, wy, wz) => (wasmExports ? wasmExports.mcAdminRailTestStart(scene, wx, wy, wz) : 0),
      (deltaSeconds) => (wasmExports ? wasmExports.mcAdminRailTestTick(scene, deltaSeconds) : ""),
      () => wasmExports?.mcAdminRailTestStop(),
    );
    railTestCartRef.current = railTestCart;

    const railSwitchMarkers = createRailSwitchMarkers(B, scene);
    railSwitchMarkersRef.current = railSwitchMarkers;
    function refreshJunctions() {
      if (!wasmExports) return;
      const csv = wasmExports.mcAdminListJunctions(scene);
      const junctions: RailJunction[] = csv
        ? csv.split(";").map((row) => {
            const [wx, wy, wz, branchCount, currentBranch] = row.split(",").map(Number);
            return { wx, wy, wz, branchCount, currentBranch };
          })
        : [];
      railSwitchMarkers.update(junctions);
    }
    refreshJunctionsRef.current = refreshJunctions;

    pasteActionsRef.current = {
      start: () => {
        const entries = clipboardRef.current?.entries;
        if (!entries || entries.length === 0) return;
        const box = selectionRef.current;
        const cx = box ? Math.floor(box.minX) : Math.floor((clipBounds.x[0] + clipBounds.x[1]) / 2);
        const cy = box ? Math.floor(box.minY) : Math.floor((clipBounds.y[0] + clipBounds.y[1]) / 2);
        const cz = box ? Math.floor(box.minZ) : Math.floor((clipBounds.z[0] + clipBounds.z[1]) / 2);
        const origin: PasteOrigin = { x: cx, y: cy, z: cz };
        pasteOriginRef.current = origin;
        setPasteOrigin(origin);
        isPastingRef.current = true;
        setIsPasting(true);
        pasteTransformRef.current = IDENTITY_PASTE_TRANSFORM;
        setPasteTransform(IDENTITY_PASTE_TRANSFORM);
        // Hides the selection wireframe while the paste gizmo is active — the two overlap and
        // fight for visual attention at the same spot. Selection state itself is untouched, so
        // it reappears once paste ends (see cancel below).
        selectionGizmo.setSelection(null);
        pasteGizmo.setOrigin(origin);
        renderPastePreview(origin);
      },
      move: (origin) => {
        if (!pasteOriginRef.current) return;
        pasteOriginRef.current = origin;
        setPasteOrigin(origin);
        pasteGizmo.setOrigin(origin);
        renderPastePreview(origin);
      },
      rotate: (dir) => {
        if (!pasteOriginRef.current) return;
        const rotation = (((pasteTransformRef.current.rotation + dir) % 4) + 4) % 4;
        const next: PasteTransform = { ...pasteTransformRef.current, rotation: rotation as 0 | 1 | 2 | 3 };
        pasteTransformRef.current = next;
        setPasteTransform(next);
        renderPastePreview(pasteOriginRef.current);
      },
      flip: (axis) => {
        if (!pasteOriginRef.current) return;
        const key: "flipX" | "flipY" | "flipZ" = axis === "x" ? "flipX" : axis === "y" ? "flipY" : "flipZ";
        const next: PasteTransform = { ...pasteTransformRef.current, [key]: !pasteTransformRef.current[key] };
        pasteTransformRef.current = next;
        setPasteTransform(next);
        renderPastePreview(pasteOriginRef.current);
      },
      confirm: () => {
        const origin = pasteOriginRef.current;
        const entries = applyPasteTransform(clipboardRef.current?.entries ?? [], pasteTransformRef.current);
        if (!origin || entries.length === 0) {
          pasteActionsRef.current.cancel();
          return;
        }
        const edits: InstanceBlockDto[] = [];
        const undoGroup: UndoEntry[] = [];
        const touchedChunks = new Set<string>();
        for (const entry of entries) {
          const x = origin.x + entry.relX;
          const y = origin.y + entry.relY;
          const z = origin.z + entry.relZ;
          if (y < zone.yMin || y > zone.yMax || !inZone(x, z)) continue;
          const prevOrdinal = wasmExports ? wasmExports.mcAdminGetBlockOrdinalAt(scene, x, y, z) : 0;
          const prevState = wasmExports ? wasmExports.mcAdminGetBlockStateAt(scene, x, y, z) : 0;
          const prevType = wasmExports ? (nameByOrdinalRef.current[prevOrdinal] ?? "AIR") : "AIR";
          undoGroup.push({ x, y, z, type: prevType, state: prevState, xOffset: 0, zOffset: 0 });
          edits.push({ x, y, z, type: entry.type, state: entry.state, xOffset: 0, zOffset: 0 });
          touchedChunks.add(chunkKeyOf(Math.floor(x / CHUNK_SIZE), Math.floor(z / CHUNK_SIZE)));
        }
        if (edits.length > 0) {
          editSocket.sendBatch(edits);
          pushUndo(undoGroup);
          for (const key of touchedChunks) {
            const [ccx, ccz] = key.split(",").map(Number);
            reloadChunk(ccx, ccz);
          }
        }
        pasteActionsRef.current.cancel();
      },
      cancel: () => {
        disposePastePreview();
        pasteGizmo.setOrigin(null);
        pasteOriginRef.current = null;
        setPasteOrigin(null);
        isPastingRef.current = false;
        setIsPasting(false);
        selectionGizmo.setSelection(selectionRef.current);
      },
    };

    let disposed = false;

    async function loadChunk(cx: number, cz: number) {
      const exports = await window.webApp!;
      const res = await fetch(`/api/chunks/${cx}/${cz}`);
      if (!res.ok) throw new Error(`chunk fetch failed: ${res.status}`);
      const bytes = new Uint8Array(await res.arrayBuffer());
      exports.mcAdminLoadChunk(scene, bytes, zone.yMin, zone.yMax);
      // Block materials are created lazily by ChunkManager.getBlockMaterials() on first chunk mesh —
      // re-apply the current clip-plane state so freshly created materials pick up an already-toggled plane.
      applyClipPlanes(B!, scene, clipPlanesRef.current, clipBounds, overlayMeshes, clipMeshesRef.current);
      // chunkBuilder.ts sets isPickable=false on chunk meshes — the live game targets blocks via
      // a custom voxel raycast, not scene.pick(). The editor relies on scene.pick() for place/
      // break, so re-enable picking on whatever meshes this call just (re)built. Every reload (a
      // place/break, or just streaming newly-visible chunks while panning) walks ALL scene meshes,
      // so the ghost/outline overlays must be explicitly excluded or they'd become pickable again —
      // scene.pick() would then occasionally hit the ghost itself, feeding its own stale position
      // back into the next hover update (looks like the ghost "leaning on itself").
      for (const m of scene.meshes) {
        const mesh = m as InstanceType<typeof BABYLON.Mesh>;
        if (!groundMeshes.has(mesh) && !overlayMeshes.has(mesh)) mesh.isPickable = true;
      }
    }

    function showChunk(cx: number, cz: number) {
      const key = chunkKeyOf(cx, cz);
      if (visibleChunks.has(key) || chunkLoading.has(key)) return;
      chunkLoading.add(key);
      loadChunk(cx, cz)
        .then(() => visibleChunks.add(key))
        .catch((e) => {
          console.error(`Failed to load instance chunk ${key}, retrying in 2s`, e);
          if (!disposed) setTimeout(() => showChunk(cx, cz), 2000);
        })
        .finally(() => chunkLoading.delete(key));
    }

    function hideChunk(cx: number, cz: number) {
      const key = chunkKeyOf(cx, cz);
      if (!visibleChunks.has(key)) return;
      visibleChunks.delete(key);
      window.webApp!.then((exports) => exports.mcAdminDisposeChunk(cx, cz)).catch(console.error);
    }

    // Unlike showChunk, always re-fetches and re-meshes even if already visible — used after a
    // place/break so the edited block actually shows up (showChunk alone would no-op since the
    // chunk is already marked visible).
    function reloadChunk(cx: number, cz: number) {
      const key = chunkKeyOf(cx, cz);
      if (chunkLoading.has(key)) return;
      chunkLoading.add(key);
      loadChunk(cx, cz)
        .then(() => {
          visibleChunks.add(key);
          // A switch toggle re-lands here through the same edit path as any other block change —
          // refresh the overlay labels so a just-toggled branch shows its new state.
          if (testRailStateRef.current !== "idle") refreshJunctions();
        })
        .catch((e) => console.error(`Failed to reload instance chunk ${key}`, e))
        .finally(() => chunkLoading.delete(key));
    }

    // Live edit socket: the only write path for instance block edits (see AdminController
    // .registerEditWs — the old PUT .../blocks REST endpoint was removed). Opened alongside chunk
    // streaming rather than after some "initial load" milestone, since instance geometry loads
    // continuously per-chunk as the camera moves; a broadcast edit for a chunk not currently
    // visible is simply ignored — it'll be up to date whenever that chunk streams in next.
    const editSocket = connectEditSocket<InstanceBlockDto>(
      "instances",
      zone.id,
      (edit) => {
        const cx = Math.floor(edit.x / CHUNK_SIZE);
        const cz = Math.floor(edit.z / CHUNK_SIZE);
        if (visibleChunks.has(chunkKeyOf(cx, cz))) reloadChunk(cx, cz);
      },
      (message) => flashActionError(message),
      // A batch broadcast from another admin tab (e.g. its own Fill/Shell/Cut) — dedupe touched
      // chunks so each one only remeshes once, instead of replaying the single-edit handler above
      // per voxel.
      (edits) => {
        const chunks = new Set<string>();
        for (const edit of edits) {
          const key = chunkKeyOf(Math.floor(edit.x / CHUNK_SIZE), Math.floor(edit.z / CHUNK_SIZE));
          if (visibleChunks.has(key)) chunks.add(key);
        }
        for (const key of chunks) {
          const [cx, cz] = key.split(",").map(Number);
          reloadChunk(cx, cz);
        }
      },
    );

    // Debug helper (KeyV): logs every non-air block in the current selection as JSON — blocktype,
    // position, rotation (bits 0-1 of the state byte, mirrors BlockState.rotation() in Kotlin).
    function dumpSelectionToConsole() {
      const box = selectionRef.current;
      if (!box || !wasmExports) return;
      const voxels = computeSelectionVoxels(box, selectionShapeRef.current, "fill");
      if (!voxels) {
        flashActionError(`Selection too large (max ${MAX_SELECTION_OP_VOXELS} voxels)`);
        return;
      }
      const blocks = voxels
        .map(({ x, y, z }) => {
          const ordinal = wasmExports!.mcAdminGetBlockOrdinalAt(scene, x, y, z);
          const state = wasmExports!.mcAdminGetBlockStateAt(scene, x, y, z);
          const type = nameByOrdinalRef.current[ordinal] ?? "AIR";
          return { type, x, y, z, rotation: state & 0x03 };
        })
        .filter((b) => b.type !== "AIR");
      console.log(JSON.stringify(blocks, null, 2));
    }

    function captureBlock(at: UndoEntry): UndoEntry {
      if (!wasmExports)
        return { x: at.x, y: at.y, z: at.z, type: "AIR", state: 0, xOffset: at.xOffset, zOffset: at.zOffset };
      const ordinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, at.x, at.y, at.z);
      const state = wasmExports.mcAdminGetBlockStateAt(scene, at.x, at.y, at.z);
      const type = nameByOrdinalRef.current[ordinal] ?? "AIR";
      return { x: at.x, y: at.y, z: at.z, type, state, xOffset: at.xOffset, zOffset: at.zOffset };
    }

    function applyEntry(entry: UndoEntry, onSuccess: () => void) {
      editSocket.send({
        x: entry.x,
        y: entry.y,
        z: entry.z,
        type: entry.type,
        state: entry.state,
        xOffset: entry.xOffset,
        zOffset: entry.zOffset,
      });
      onSuccess();
      reloadChunk(Math.floor(entry.x / CHUNK_SIZE), Math.floor(entry.z / CHUNK_SIZE));
    }

    const { pushUndo, performUndo, performRedo } = makeUndoRedoController<UndoEntry>(
      undoStackRef,
      redoStackRef,
      captureBlock,
      applyEntry,
    );

    // Fill/Shell/Cut on the current selection — one WS batch frame (editSocket.sendBatch) instead
    // of N individual sends, one undo-stack group instead of N slots, one reloadChunk per touched
    // chunk instead of per voxel. See selectionVoxels.ts for the voxel enumeration/cap.
    runSelectionOpRef.current = (kind: "fill" | "shell" | "cut" | "copy") => {
      const box = selectionRef.current;
      if (!box) return;
      if (kind !== "cut" && kind !== "copy" && !patternBlocksRef.current[0]) return;
      const shapeMode = kind === "cut" || kind === "copy" ? "fill" : kind;
      const voxels = computeSelectionVoxels(box, selectionShapeRef.current, shapeMode);
      if (!voxels) {
        flashActionError(`Selection too large (max ${MAX_SELECTION_OP_VOXELS} voxels)`);
        return;
      }
      const valid = voxels.filter((v) => v.y >= zone.yMin && v.y <= zone.yMax && inZone(v.x, v.z));
      if (valid.length === 0) return;

      const pattern: Pattern = { a: patternBlocksRef.current[0] ?? "AIR", b: patternBlocksRef.current[1] ?? undefined };
      const originX = Math.floor(box.minX);
      const originY = Math.floor(box.minY);
      const originZ = Math.floor(box.minZ);
      const edits: InstanceBlockDto[] = [];
      const undoGroup: UndoEntry[] = [];
      const clipEntries: { relX: number; relY: number; relZ: number; type: string; state: number }[] = [];
      const touchedChunks = new Set<string>();

      for (const v of valid) {
        const prevOrdinal = wasmExports ? wasmExports.mcAdminGetBlockOrdinalAt(scene, v.x, v.y, v.z) : 0;
        const prevState = wasmExports ? wasmExports.mcAdminGetBlockStateAt(scene, v.x, v.y, v.z) : 0;
        const prevType = wasmExports ? (nameByOrdinalRef.current[prevOrdinal] ?? "AIR") : "AIR";

        if (kind === "cut" || kind === "copy") {
          clipEntries.push({
            relX: v.x - originX,
            relY: v.y - originY,
            relZ: v.z - originZ,
            type: prevType,
            state: prevState,
          });
        }
        // Copy is read-only — no edit, no undo entry, geometry stays untouched.
        if (kind === "copy") continue;

        undoGroup.push({ x: v.x, y: v.y, z: v.z, type: prevType, state: prevState, xOffset: 0, zOffset: 0 });
        const type = kind === "cut" ? "AIR" : resolvePatternBlock(pattern, v.x, v.y, v.z);
        edits.push({ x: v.x, y: v.y, z: v.z, type, state: 0, xOffset: 0, zOffset: 0 });
        touchedChunks.add(chunkKeyOf(Math.floor(v.x / CHUNK_SIZE), Math.floor(v.z / CHUNK_SIZE)));
      }

      if (kind !== "copy") {
        editSocket.sendBatch(edits);
        pushUndo(undoGroup);
        for (const key of touchedChunks) {
          const [cx, cz] = key.split(",").map(Number);
          reloadChunk(cx, cz);
        }
      }
      if (kind === "cut" || kind === "copy") {
        clipboardRef.current = { entries: clipEntries };
        setClipboardCount(clipEntries.length);
      }
    };

    function viewRadiusChunks(): number {
      return Math.min(MAX_VIEW_RADIUS_CHUNKS, Math.max(1, Math.ceil(camera.radius / CHUNK_SIZE) + 1));
    }

    function updateVisibleChunks() {
      const radius = viewRadiusChunks();
      const targetCx = Math.floor(camera.target.x / CHUNK_SIZE);
      const targetCz = Math.floor(camera.target.z / CHUNK_SIZE);
      for (const { cx, cz } of zone.chunks) {
        if (Math.max(Math.abs(cx - targetCx), Math.abs(cz - targetCz)) <= radius) showChunk(cx, cz);
      }
      for (const key of Array.from(visibleChunks)) {
        const [cx, cz] = key.split(",").map(Number);
        if (Math.max(Math.abs(cx - targetCx), Math.abs(cz - targetCz)) > radius + VIEW_RADIUS_UNLOAD_MARGIN) {
          hideChunk(cx, cz);
        }
      }
    }

    updateVisibleChunks();
    let lastVisibilityCheck = 0;
    const viewMatrixObserver = camera.onViewMatrixChangedObservable.add(() => {
      const now = performance.now();
      if (now - lastVisibilityCheck < 300) return;
      lastVisibilityCheck = now;
      updateVisibleChunks();
      saveCameraState(cameraStorageKey(zone.id), {
        alpha: camera.alpha,
        beta: camera.beta,
        radius: camera.radius,
        targetX: camera.target.x,
        targetY: camera.target.y,
        targetZ: camera.target.z,
      });
    });

    // Thin wrappers binding the shared fractionalPlacement.ts helpers to this editor's wasm
    // exports (mcAdminGetUsedXZOffsetAt/mcAdminGetBlockOrdinalAt) and JS scene handle — see that
    // module for the actual slot/redirect logic (shared with SceneEditorViewport.tsx).
    function usedSlotAtLocal(wx: number, wy: number, wz: number): [number, number] | null {
      if (!wasmExports) return null;
      return usedSlotAt((x, y, z) => wasmExports!.mcAdminGetUsedXZOffsetAt(scene, x, y, z), wx, wy, wz);
    }

    function resolvePlacementCellLocal(
      pickedPoint: InstanceType<typeof BABYLON.Vector3>,
      normal: InstanceType<typeof BABYLON.Vector3>,
    ): [number, number, number] {
      if (!wasmExports) {
        return [
          Math.floor(pickedPoint.x + normal.x * PICK_EPSILON),
          Math.floor(pickedPoint.y + normal.y * PICK_EPSILON),
          Math.floor(pickedPoint.z + normal.z * PICK_EPSILON),
        ];
      }
      return resolvePlacementCell(
        pickedPoint,
        normal,
        (x, y, z) => wasmExports!.mcAdminGetBlockOrdinalAt(scene, x, y, z),
        (ordinal) => window.mc.getBlockDef(ordinal),
      );
    }

    // Hover preview: recomputed on every pointer move that isn't an active camera drag, so the
    // ghost/outline (place mode) or the red break overlay (break mode) track the cursor before a
    // click, exactly like moving the mouse in-game moves the block-placement preview.
    // Block name shown top-right whenever a voxel is under the cursor — independent of edit mode,
    // so it also updates while in select mode or mid-rail-test-picking (unlike the ghost/break
    // overlay below, which those modes suppress).
    function updateHoveredVoxelName(
      pick: ReturnType<typeof scene.pick>,
      normal: InstanceType<typeof BABYLON.Vector3> | null,
      onGround: boolean,
    ) {
      if (!pick?.hit || !pick.pickedPoint || onGround || !normal || !wasmExports) {
        setHoveredVoxelName(null);
        return;
      }
      const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
      const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
      const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
      if (by < zone.yMin || by > zone.yMax || !inZone(bx, bz)) {
        setHoveredVoxelName(null);
        return;
      }
      const ordinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, bx, by, bz);
      const name = nameByOrdinalRef.current[ordinal];
      setHoveredVoxelName(name && name !== "AIR" ? name : null);
    }

    function updateHoverPreview(evt: PointerEvent) {
      const hoverPick = scene.pick(scene.pointerX, scene.pointerY);
      const hoverNormal = hoverPick?.hit ? hoverPick.getNormal(true) : null;
      const hoverOnGround =
        !!hoverPick?.pickedMesh && groundMeshes.has(hoverPick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);
      updateHoveredVoxelName(hoverPick, hoverNormal, hoverOnGround);

      if (modeRef.current === "select" || testRailStateRef.current === "picking") {
        overlay.disposeAll();
        return;
      }
      const effectiveMode = evt.shiftKey ? "break" : modeRef.current;
      const pick = hoverPick;
      if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) {
        overlay.disposeAll();
        return;
      }
      const normal = hoverNormal;
      const onGround = hoverOnGround;

      if (effectiveMode === "break") {
        overlay.disposeGhost();
        overlay.disposeOutline();
        if (onGround || !normal) {
          overlay.disposeBreakOverlay();
          return;
        }
        const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
        const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
        const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
        if (!wasmExports) {
          overlay.disposeBreakOverlay();
          return;
        }
        const targetOrdinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, bx, by, bz);
        const targetState = wasmExports.mcAdminGetBlockStateAt(scene, bx, by, bz);
        // Bits 0-1 of the state byte — mirrors BlockState.rotation() (BlockState.kt); duplicated
        // here since there's no shared code path between Kotlin and this TS admin bundle.
        const rotation = targetState & 0x03;
        const targetDef = window.mc.getBlockDef(targetOrdinal);
        const bs = targetDef?.brickSize ?? [2, 2, 2];
        const [xOffset, zOffset] = computeSlotOffset(
          pick.pickedPoint.x,
          pick.pickedPoint.z,
          bx,
          bz,
          bs[0],
          bs[2],
          rotation,
          normal.x !== 0,
          normal.z !== 0,
          usedSlotAtLocal(bx, by, bz),
        );
        overlay.showBreakOverlay(bx, by, bz, targetOrdinal, rotation, xOffset, zOffset);
        return;
      }
      overlay.disposeBreakOverlay();

      const type = selectedTypeRef.current;
      const ordinal = type ? ordinalByNameRef.current.get(type) : undefined;
      if (ordinal == null) {
        overlay.disposeGhost();
        overlay.disposeOutline();
        return;
      }
      let tx: number, ty: number, tz: number;
      if (onGround) {
        tx = Math.floor(pick.pickedPoint.x);
        ty = zone.yMin;
        tz = Math.floor(pick.pickedPoint.z);
      } else if (normal) {
        [tx, ty, tz] = resolvePlacementCellLocal(pick.pickedPoint, normal);
      } else {
        overlay.disposeGhost();
        overlay.disposeOutline();
        return;
      }
      if (ty < zone.yMin || ty > zone.yMax || !inZone(tx, tz)) {
        overlay.disposeGhost();
        overlay.disposeOutline();
        return;
      }
      const blockDef = window.mc.getBlockDef(ordinal);
      const bs = blockDef?.brickSize ?? [2, 2, 2];
      const [xOffset, zOffset] = computeSlotOffset(
        pick.pickedPoint.x,
        pick.pickedPoint.z,
        tx,
        tz,
        bs[0],
        bs[2],
        overlay.getPlacementRotation(),
        !onGround && !!normal && normal.x !== 0,
        !onGround && !!normal && normal.z !== 0,
        usedSlotAtLocal(tx, ty, tz),
      );
      overlay.showGhostAndOutline(tx, ty, tz, ordinal, selectedColorIndexRef.current, xOffset, zOffset);
    }

    function onKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) return;
      if (isPastingRef.current) {
        if (e.code === "Enter" || e.code === "NumpadEnter") {
          pasteActionsRef.current.confirm();
          return;
        }
        if (e.code === "Escape") {
          pasteActionsRef.current.cancel();
          return;
        }
      }
      if (e.code === "Escape" && testRailStateRef.current !== "idle") {
        railTestCartRef.current?.stop();
        railSwitchMarkersRef.current?.clear();
        setTestRailState("idle");
        return;
      }
      if (e.code === "KeyU") {
        performUndo();
        return;
      }
      if (e.code === "KeyY") {
        performRedo();
        return;
      }
      if (e.code === "KeyT") {
        toggleTestRail();
        return;
      }
      if (e.code === "KeyV") {
        dumpSelectionToConsole();
        return;
      }
      if (e.code !== "KeyR") return;
      overlay.rotatePlacement();
      // Force a rebuild at the last known cursor position so the rotation is visible immediately,
      // instead of waiting for the next mouse move.
      updateHoverPreview({ shiftKey: e.shiftKey } as PointerEvent);
    }
    window.addEventListener("keydown", onKeyDown);

    const disposePointerController = setupOrbitPointerController({
      B,
      scene,
      camera,
      canvas,
      getMode: () => modeRef.current,
      onHoverMove: updateHoverPreview,
      onCtrlClick: (pick) => {
        const normal = pick.getNormal(true);
        const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);
        if (onGround || !normal || !pick.pickedPoint || !wasmExports) return;
        const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
        const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
        const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
        if (by < zone.yMin || by > zone.yMax || !inZone(bx, bz)) return;
        const ordinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, bx, by, bz);
        const name = nameByOrdinalRef.current[ordinal];
        if (!name || name === "AIR") return;
        const slotIdx = shortcutBarRef.current.slots.findIndex((s) => s === name);
        if (slotIdx !== -1) {
          shortcutBarRef.current.selectSlot(slotIdx);
        } else {
          selectBlockType(name);
        }
      },
      onClick: ({ pick, normal, mode: currentMode, shiftKey }) => {
        const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);

        if (testRailStateRef.current !== "idle") {
          const junction = railSwitchMarkers.hitTest(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh> | null);
          if (junction) {
            editSocket.sendRaw({ x: junction.wx, y: junction.wy, z: junction.wz });
            reloadChunk(Math.floor(junction.wx / CHUNK_SIZE), Math.floor(junction.wz / CHUNK_SIZE));
            return;
          }
        }

        if (testRailStateRef.current === "picking") {
          if (onGround || !normal || !pick.pickedPoint) return;
          const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
          const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
          const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
          if (railTestCartRef.current?.start(bx, by, bz)) {
            setTestRailState("running");
          } else {
            flashActionError("Not a rail block");
          }
          return;
        }

        if (currentMode === "select") {
          if (onGround || !normal || !pick.pickedPoint) return;
          const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
          const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
          const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
          const prev = selectionRef.current;
          // Shift-click extends the existing selection to include the clicked block instead of
          // replacing it — only when a selection already exists; otherwise it's a plain new pick.
          if (shiftKey && prev) {
            setSelection({
              minX: Math.min(prev.minX, bx),
              minY: Math.min(prev.minY, by),
              minZ: Math.min(prev.minZ, bz),
              maxX: Math.max(prev.maxX, bx + 1),
              maxY: Math.max(prev.maxY, by + 1),
              maxZ: Math.max(prev.maxZ, bz + 1),
            });
          } else {
            setSelection({ minX: bx, minY: by, minZ: bz, maxX: bx + 1, maxY: by + 1, maxZ: bz + 1 });
          }
          return;
        }

        if (currentMode === "break") {
          if (onGround || !normal || !pick.pickedPoint) return;
          const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
          const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
          const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
          // Resolve the targeted block's own brickSize so the removal hits the exact XZ sub-slot
          // aimed at (e.g. one LEGO_PIECE among several stacked/slotted in the same cell), same
          // idea as the in-game precise break (LocalPlayerController.kt).
          let breakXOffset = 0;
          let breakZOffset = 0;
          let prevType = "AIR";
          let prevState = 0;
          if (wasmExports) {
            const targetOrdinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, bx, by, bz);
            const targetState = wasmExports.mcAdminGetBlockStateAt(scene, bx, by, bz);
            const rotation = targetState & 0x03; // BlockState.rotation() mirror — see updateHoverPreview
            const targetDef = window.mc.getBlockDef(targetOrdinal);
            const bs = targetDef?.brickSize ?? [2, 2, 2];
            [breakXOffset, breakZOffset] = computeSlotOffset(
              pick.pickedPoint.x,
              pick.pickedPoint.z,
              bx,
              bz,
              bs[0],
              bs[2],
              rotation,
              normal.x !== 0,
              normal.z !== 0,
              usedSlotAtLocal(bx, by, bz),
            );
            prevType = targetDef?.name ?? "AIR";
            // Full state byte (rotation + color) so undo restores the exact previous look.
            prevState = targetState;
          }
          editSocket.send({
            x: bx,
            y: by,
            z: bz,
            type: "AIR",
            state: 0,
            xOffset: breakXOffset,
            zOffset: breakZOffset,
          });
          pushUndo({
            x: bx,
            y: by,
            z: bz,
            type: prevType,
            state: prevState,
            xOffset: breakXOffset,
            zOffset: breakZOffset,
          });
          reloadChunk(Math.floor(bx / CHUNK_SIZE), Math.floor(bz / CHUNK_SIZE));
          return;
        }

        // place mode
        const type = selectedTypeRef.current;
        if (!type || !pick.pickedPoint) return;
        let tx: number, ty: number, tz: number;
        if (onGround) {
          tx = Math.floor(pick.pickedPoint.x);
          ty = zone.yMin;
          tz = Math.floor(pick.pickedPoint.z);
        } else if (normal) {
          [tx, ty, tz] = resolvePlacementCellLocal(pick.pickedPoint, normal);
        } else {
          return;
        }
        if (ty < zone.yMin || ty > zone.yMax || !inZone(tx, tz)) return;
        const ordinal = ordinalByNameRef.current.get(type);
        const placeDef = ordinal != null ? window.mc.getBlockDef(ordinal) : undefined;
        const placeBs = placeDef?.brickSize ?? [2, 2, 2];
        const [xOffset, zOffset] = computeSlotOffset(
          pick.pickedPoint.x,
          pick.pickedPoint.z,
          tx,
          tz,
          placeBs[0],
          placeBs[2],
          overlay.getPlacementRotation(),
          !onGround && !!normal && normal.x !== 0,
          !onGround && !!normal && normal.z !== 0,
          usedSlotAtLocal(tx, ty, tz),
        );
        let prevType = "AIR";
        let prevState = 0;
        if (wasmExports) {
          const prevOrdinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, tx, ty, tz);
          // Full state byte (rotation + color, see BlockState.kt) so undo restores the exact
          // previous look, not just its rotation.
          prevState = wasmExports.mcAdminGetBlockStateAt(scene, tx, ty, tz);
          prevType = nameByOrdinalRef.current[prevOrdinal] ?? "AIR";
        }
        const state = packState(overlay.getPlacementRotation(), selectedColorIndexRef.current);
        editSocket.send({ x: tx, y: ty, z: tz, type, state, xOffset, zOffset });
        pushUndo({ x: tx, y: ty, z: tz, type: prevType, state: prevState, xOffset, zOffset });
        reloadChunk(Math.floor(tx / CHUNK_SIZE), Math.floor(tz / CHUNK_SIZE));
      },
    });

    const axesGizmo = createAxesGizmo(B, engine, camera);
    engine.runRenderLoop(() => {
      scene.render();
      axesGizmo.render();
    });
    const onResize = () => engine.resize();
    window.addEventListener("resize", onResize);

    return () => {
      disposed = true;
      camera.onViewMatrixChangedObservable.remove(viewMatrixObserver);
      disposePointerController();
      window.removeEventListener("resize", onResize);
      window.removeEventListener("keydown", onKeyDown);
      overlay.disposeAll();
      selectionGizmo.dispose();
      disposePastePreview();
      pasteGizmo.dispose();
      railTestCart.dispose();
      railTestCartRef.current = null;
      railSwitchMarkers.dispose();
      railSwitchMarkersRef.current = null;
      editSocket.close();
      axesGizmo.dispose();
      engine.dispose();
      sceneRef.current = null;
      overlayMeshesRef.current = null;
      selectionGizmoRef.current = null;
      pasteGizmoRef.current = null;
      pastePreviewMeshesRef.current = [];
      clipMeshesRef.current = {};
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- zone.id identity is the intended re-mount trigger
  }, [zone.id, blockDefsReady]);

  // Reactive clip-plane sync: runs whenever the sidebar toggles/moves a plane, and again after
  // scene remount (zone.id/blockDefsReady) so a plane already enabled survives a zone switch.
  useEffect(() => {
    const scene = sceneRef.current;
    const overlayMeshes = overlayMeshesRef.current;
    const B = window.BABYLON;
    if (!scene || !overlayMeshes || !B) return;
    applyClipPlanes(B, scene, clipPlanes, clipBounds, overlayMeshes, clipMeshesRef.current);
  }, [clipPlanes, clipBounds, zone.id, blockDefsReady]);

  // Mirrors the selection state into the gizmo — null both when no selection was made yet and
  // whenever the editor leaves select mode (see the mode effect above, which clears `selection`).
  useEffect(() => {
    selectionRef.current = selection;
    selectionGizmoRef.current?.setSelection(selection);
  }, [selection]);

  return (
    <div className="flex-1 flex overflow-hidden">
      <div className="flex-[4] relative">
        <ViewportCameraHud activeDragMode={activeDragMode} />
        <HoveredVoxelNameHud name={hoveredVoxelName} />
        {actionError && (
          <div className="absolute bottom-2 left-1/2 -translate-x-1/2 max-w-[80%] px-3 py-1.5 rounded bg-red-900/90 text-red-100 text-xs z-10 pointer-events-none">
            {actionError}
          </div>
        )}
        {loadError ? (
          <div className="w-full h-full flex items-center justify-center text-red-400 text-sm p-6 text-center">
            {loadError}
          </div>
        ) : (
          <canvas ref={canvasRef} className="w-full h-full block" />
        )}
      </div>
      <VoxelEditorSidebar
        modKeys={modKeys}
        mode={mode}
        onToggleSelect={toggleSelectMode}
        selectionShape={selectionShape}
        onSelectShape={setSelectionShape}
        selectionSnap={selectionSnap}
        onSelectSnap={setSelectionSnap}
        hasSelection={selection !== null}
        selection={selection}
        onSelectionFieldChange={setSelectionField}
        patternBlocks={patternBlocks}
        activePatternSlot={activePatternSlot}
        onSelectPatternSlot={setActivePatternSlot}
        onClearPatternSlot={clearPatternSlot}
        onFill={() => runSelectionOp("fill")}
        onShell={() => runSelectionOp("shell")}
        onCut={() => runSelectionOp("cut")}
        onCopy={() => runSelectionOp("copy")}
        clipboardCount={clipboardCount}
        onPaste={startPaste}
        isPasting={isPasting}
        onConfirmPaste={confirmPaste}
        onCancelPaste={cancelPaste}
        onRotatePaste={rotatePaste}
        onFlipPaste={flipPaste}
        pasteTransform={pasteTransform}
        pasteOrigin={pasteOrigin}
        onMovePasteOrigin={movePasteOrigin}
        savedSelections={savedSelections}
        onAddSelectionToMemory={addSelectionToMemory}
        onSelectSavedSelection={selectSavedSelection}
        onRemoveSavedSelection={removeSavedSelection}
        resizeStep={resizeStep}
        onSelectResizeStep={setResizeStep}
        onExpandSelection={expandSelection}
        onContractSelection={contractSelection}
        activeDragMode={activeDragMode}
        selectedType={selectedType}
        blockDefsReady={blockDefsReady}
        previewsReady={previewsReady}
        previewProgress={previewProgress}
        getOrdinal={getOrdinal}
        blockDefs={blockDefs}
        plainColors={plainColors}
        selectedColorIndex={selectedColorIndex}
        setSelectedColorIndex={setSelectedColorIndex}
        clipPlanes={clipPlanes}
        clipBounds={clipBounds}
        setClipPlanes={setClipPlanes}
        shortcutBar={shortcutBar}
        hoveredShortcutSlot={hoveredShortcutSlot}
        setHoveredShortcutSlot={setHoveredShortcutSlot}
        getPreview={getPreview}
        search={search}
        setSearch={setSearch}
        hoveredBlockName={hoveredBlockName}
        hoveredRect={hoveredRect}
        setHoveredBlockName={setHoveredBlockName}
        setHoveredRect={setHoveredRect}
        selectBlockType={selectBlockType}
        paletteRef={paletteRef}
        testRailState={testRailState}
        onToggleTestRail={toggleTestRail}
      />
    </div>
  );
}
