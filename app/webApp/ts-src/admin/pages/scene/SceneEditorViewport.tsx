import { useEffect, useMemo, useRef, useState } from "react";
import { putApiAdminScenesByIdLayout } from "../../../generated/api/requests";
import type { SceneDto } from "../../apiTypes";
import {
  useAllBlockPreviewsReady,
  useBlockDefsReady,
  useBlockPreviewProgress,
  useBlockPreviews,
} from "../../../game/shared/BlockPreview";
import { startPreloading } from "../../../game/shared/blockPreviewCache";
import { useAdminShortcutBar } from "../shared/voxelEditor/useAdminShortcutBar";
import { createAxesGizmo } from "../shared/voxelEditor/axesGizmo";
import { applyClipPlanes, ClipAxis, ClipPlaneState } from "../shared/voxelEditor/clipAxis";
import { packState } from "../shared/voxelEditor/blockState";
import { saveCameraState } from "../shared/voxelEditor/cameraStorage";
import { createOrbitCamera, setupBasicLighting } from "../shared/voxelEditor/orbitCamera";
import { setupOrbitPointerController } from "../shared/voxelEditor/orbitPointerController";
import { createOverlayController } from "../shared/voxelEditor/overlayController";
import {
  createSelectionGizmo,
  type SelectionBox,
  type SelectionShape,
  type SelectionSnap,
} from "../shared/voxelEditor/selectionGizmo";
import { useBlockRegistry } from "../shared/voxelEditor/useBlockRegistry";
import { useModifierDragMode } from "../shared/voxelEditor/useModifierDragMode";
import { useActionError } from "../shared/voxelEditor/useActionError";
import { makeUndoRedoController, type UndoEntryBase, type UndoGroup } from "../shared/voxelEditor/undoRedoStack";
import { VoxelEditorSidebar } from "../shared/voxelEditor/VoxelEditorSidebar";
import { connectEditSocket, type BlockEditSocket } from "../shared/voxelEditor/editSocket";
import {
  computeSelectionVoxels,
  resolvePatternBlock,
  MAX_SELECTION_OP_VOXELS,
  type Pattern,
} from "../shared/voxelEditor/selectionVoxels";
import type { SceneBlockDto } from "../../apiTypes";

// Voxel-picking epsilon: nudges the picked point across the hit face along its normal before
// flooring, so the coordinate resolves to the block on the correct side of the face — same
// technique as InstanceEditorViewport.tsx.
const PICK_EPSILON = 0.01;

const cameraStorageKey = (sceneId: string) => `sceneEditorCamera:${sceneId}`;

// Snapshot of a cell's content right before a place/break overwrote it, so undo can write it
// back. Unlike InstanceEditorViewport's UndoEntry, no xOffset/zOffset — the Scene HTTP contract
// (PUT .../blocks) only has x/y/z/type/state, no sub-voxel slot fields.
type UndoEntry = UndoEntryBase;

// Parses GET /api/admin/scenes/{id}/blocks/raw's binary layout: 3 big-endian Int32 header fields
// (width, height, depth) followed by two equal-length byte arrays (blocks, then states), flat
// index = x*height*depth + y*depth + z — see AdminScenePreview.kt's mcSceneLoad.
function parseSceneRaw(buf: ArrayBuffer): {
  width: number;
  height: number;
  depth: number;
  blocks: Uint8Array;
  states: Uint8Array;
} {
  const view = new DataView(buf);
  const width = view.getInt32(0, false);
  const height = view.getInt32(4, false);
  const depth = view.getInt32(8, false);
  const cellCount = width * height * depth;
  const blocksStart = 12;
  const statesStart = blocksStart + cellCount;
  const blocks = new Uint8Array(buf, blocksStart, cellCount);
  const states = new Uint8Array(buf, statesStart, cellCount);
  return { width, height, depth, blocks, states };
}

export function SceneEditorViewport({ scene }: { scene: SceneDto }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [hoveredBlockName, setHoveredBlockName] = useState<string | null>(null);
  const [hoveredRect, setHoveredRect] = useState<DOMRect | null>(null);
  const [hoveredShortcutSlot, setHoveredShortcutSlot] = useState<number | null>(null);
  const { blockDefs, ordinalByName, plainColors, getOrdinal } = useBlockRegistry();
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
  const [loadError, setLoadError] = useState<string | null>(null);
  const { actionError, flashActionError } = useActionError();
  const [search, setSearch] = useState("");
  const modeRef = useRef(mode);
  const selectedTypeRef = useRef(selectedType);
  const ordinalByNameRef = useRef(ordinalByName);
  const getPreview = useBlockPreviews();
  // Each stack slot is a GROUP of entries (see undoRedoStack.ts) — a single place/break is a group
  // of one, a Fill/Shell/Cut is a group of every voxel it touched.
  const undoStackRef = useRef<UndoGroup<UndoEntry>[]>([]);
  const redoStackRef = useRef<UndoGroup<UndoEntry>[]>([]);
  const runSelectionOpRef = useRef<(kind: "fill" | "shell" | "cut") => void>(() => {});

  const clipBounds = useMemo(
    () => ({
      x: [0, scene.width] as const,
      y: [0, scene.height] as const,
      z: [0, scene.depth] as const,
    }),
    [scene.width, scene.height, scene.depth],
  );
  // Not persisted server-side — the Scene HTTP contract has no layout/clipPlanes endpoint
  // (unlike InstanceZoneDto.clipPlanes), so this resets on remount like the geometry itself.
  const [clipPlanes, setClipPlanes] = useState<Record<ClipAxis, ClipPlaneState>>(() => ({
    x: { enabled: false, flipped: false, pos: (clipBounds.x[0] + clipBounds.x[1]) / 2 },
    y: { enabled: false, flipped: false, pos: (clipBounds.y[0] + clipBounds.y[1]) / 2 },
    z: { enabled: false, flipped: false, pos: (clipBounds.z[0] + clipBounds.z[1]) / 2 },
  }));
  const clipPlanesRef = useRef(clipPlanes);
  useEffect(() => {
    clipPlanesRef.current = clipPlanes;
  }, [clipPlanes]);
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
    }
  }, [mode]);

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
    selectedColorIndexRef.current = selectedColorIndex;
  }, [selectedColorIndex]);

  // In select mode, a palette click assigns the Fill/Shell pattern slot instead of switching to
  // place mode — see InstanceEditorViewport's selectBlockType for the same behavior.
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

  function runSelectionOp(kind: "fill" | "shell" | "cut") {
    runSelectionOpRef.current(kind);
  }

  const blockDefsReady = useBlockDefsReady();
  const previewsReady = useAllBlockPreviewsReady();
  const previewProgress = useBlockPreviewProgress();

  const shortcutBar = useAdminShortcutBar({
    initialPages: scene.shortcutBarPages,
    onSelectBreak: () => setMode("break"),
    onSelectBlock: (blockName) => selectBlockType(blockName),
  });

  // Persists shortcut-bar state onto the scene's own YAML record so it survives reload/navigation
  // — same rationale as InstanceEditorViewport's layout persistence. Skips the very first run
  // (mount reflecting back what the scene already has) so switching scenes or reloading doesn't
  // fire a no-op PUT.
  const layoutMounted = useRef(false);
  useEffect(() => {
    if (!layoutMounted.current) {
      layoutMounted.current = true;
      return;
    }
    const timeout = setTimeout(() => {
      putApiAdminScenesByIdLayout({
        path: { id: scene.id },
        body: { shortcutBarPages: shortcutBar.pages as string[][] },
        throwOnError: true,
      }).catch(console.error);
    }, 500);
    return () => clearTimeout(timeout);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- scene.id intentionally excluded, only its identity matters via the closure
  }, [shortcutBar.pages]);

  const { modKeys, activeDragMode } = useModifierDragMode();

  const inBounds = (x: number, y: number, z: number) =>
    x >= 0 && x < scene.width && y >= 0 && y < scene.height && z >= 0 && z < scene.depth;

  useEffect(() => {
    setLoadError(null);
    if (!window.webApp) {
      setLoadError("WASM module not loaded (webApp.js missing).");
      return;
    }
    // SceneMesher silently no-ops until block defs are ready (mirrors ChunkManager's behavior).
    if (!blockDefsReady) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const babylonScene = new B.Scene(engine);
    babylonScene.clearColor = new B.Color4(0.06, 0.06, 0.08, 1);
    sceneRef.current = babylonScene;
    clipMeshesRef.current = {};

    let wasmExports: Awaited<NonNullable<typeof window.webApp>> | null = null;
    window.webApp!.then((e) => {
      wasmExports = e;
    });

    const centerX = scene.width / 2;
    const centerY = scene.height / 2;
    const centerZ = scene.depth / 2;
    const span = Math.max(scene.width, scene.depth, scene.height, 4);

    const camera = createOrbitCamera(
      B,
      babylonScene,
      canvas,
      cameraStorageKey(scene.id),
      { x: centerX, y: centerY, z: centerZ },
      span,
    );
    setupBasicLighting(B, babylonScene);

    // Single ground plane sized to the whole volume footprint, positioned at y=0 (the volume's
    // lowest layer) — replaces InstanceEditorViewport's one-ground-per-chunk tiling, since a
    // Scene is exactly one contiguous buffer, not a chunk grid. Centered under [0,width)x[0,depth)
    // to match the block placement convention (block (x,y,z) spans [x,x+1)x[y,y+1)x[z,z+1)).
    const groundMat = new B.StandardMaterial("sceneGroundMat", babylonScene);
    // Single non-repeated texture spanning the whole ground (not tiled) so marks can be aligned
    // to the volume center rather than to each tile independently: fine line every cell, brighter
    // every 5 cells, brightest every 10 cells, counted outward from the center of the floor.
    const gridPx = 32;
    const texW = scene.width * gridPx;
    const texH = scene.depth * gridPx;
    const gridTex = new B.DynamicTexture("sceneGroundGrid", { width: texW, height: texH }, babylonScene, false);
    const gridCtx = gridTex.getContext() as CanvasRenderingContext2D;
    gridCtx.fillStyle = "#26262e";
    gridCtx.fillRect(0, 0, texW, texH);
    const cx = scene.width / 2;
    const cz = scene.depth / 2;
    function drawGridLines(count: number, cellPx: number, center: number, size: number, vertical: boolean) {
      for (let i = 0; i <= count; i++) {
        const offset = i - center;
        const isTen = Math.round(offset) % 10 === 0;
        const isFive = Math.round(offset) % 5 === 0;
        gridCtx.strokeStyle = isTen ? "#c9c9d6" : isFive ? "#8a8a99" : "#5a5a66";
        gridCtx.lineWidth = isTen ? 3 : isFive ? 2 : 1;
        const pos = i * cellPx;
        gridCtx.beginPath();
        if (vertical) {
          gridCtx.moveTo(pos, 0);
          gridCtx.lineTo(pos, size);
        } else {
          gridCtx.moveTo(0, pos);
          gridCtx.lineTo(size, pos);
        }
        gridCtx.stroke();
      }
    }
    drawGridLines(scene.width, gridPx, cx, texH, true);
    drawGridLines(scene.depth, gridPx, cz, texW, false);
    // Numeric labels at every 10-cell intersection across the whole floor (not just the ones
    // crossing the center), so distances stay readable from any part of the grid.
    // Colors match the viewport axes gizmo (axesGizmo.ts) so X/Z labels read as the same axis.
    const xAxisColor = "#e5484d";
    const zAxisColor = "#3d63dd";
    const labelFontPx = Math.floor(gridPx * 0.6);
    gridCtx.font = `bold ${labelFontPx}px monospace`;
    gridCtx.textBaseline = "top";
    gridCtx.lineWidth = 3;
    gridCtx.strokeStyle = "#000000";
    for (let i = 0; i <= scene.width; i++) {
      if (Math.round(i - cx) % 10 !== 0) continue;
      for (let j = 0; j <= scene.depth; j++) {
        if (Math.round(j - cz) % 10 !== 0) continue;
        const xLabel = `${Math.round(i - cx)}`;
        const zLabel = `${Math.round(j - cz)}`;
        const ty = j * gridPx + 4;
        gridCtx.textAlign = "right";
        gridCtx.fillStyle = zAxisColor;
        gridCtx.strokeText(zLabel, i * gridPx - 4, ty);
        gridCtx.fillText(zLabel, i * gridPx - 4, ty);
        gridCtx.textAlign = "left";
        gridCtx.fillStyle = xAxisColor;
        gridCtx.strokeText(xLabel, i * gridPx + 4, ty);
        gridCtx.fillText(xLabel, i * gridPx + 4, ty);
      }
    }
    // Smaller labels at every 5-cell intersection (skipping the ones already labeled above at
    // every 10, to avoid drawing on top of them), same axis colors.
    const fiveLabelFontPx = Math.floor(gridPx * 0.35);
    gridCtx.font = `bold ${fiveLabelFontPx}px monospace`;
    gridCtx.lineWidth = 2;
    gridCtx.strokeStyle = "#000000";
    for (let i = 0; i <= scene.width; i++) {
      const ox = Math.round(i - cx);
      if (ox % 5 !== 0) continue;
      for (let j = 0; j <= scene.depth; j++) {
        const oz = Math.round(j - cz);
        if (oz % 5 !== 0) continue;
        if (ox % 10 === 0 && oz % 10 === 0) continue;
        const ty = j * gridPx + 4;
        gridCtx.textAlign = "right";
        gridCtx.fillStyle = zAxisColor;
        gridCtx.strokeText(`${oz}`, i * gridPx - 4, ty);
        gridCtx.fillText(`${oz}`, i * gridPx - 4, ty);
        gridCtx.textAlign = "left";
        gridCtx.fillStyle = xAxisColor;
        gridCtx.strokeText(`${ox}`, i * gridPx + 4, ty);
        gridCtx.fillText(`${ox}`, i * gridPx + 4, ty);
      }
    }
    gridTex.update();
    gridTex.wrapU = B.Texture.CLAMP_ADDRESSMODE;
    gridTex.wrapV = B.Texture.CLAMP_ADDRESSMODE;
    groundMat.diffuseTexture = gridTex;
    groundMat.specularColor = B.Color3.Black();
    groundMat.alpha = 0.35;
    // Camera can orbit below y=0 (e.g. panning under the volume) — without this the grid, being
    // single-sided by default, disappears since its backface faces the camera from underneath.
    groundMat.backFaceCulling = false;
    groundMat.freeze();
    const ground = B.MeshBuilder.CreateGround("floor", { width: scene.width, height: scene.depth }, babylonScene);
    ground.position = new B.Vector3(scene.width / 2, 0, scene.depth / 2);
    ground.material = groundMat;
    ground.freezeWorldMatrix();
    const groundMeshes = new Set<InstanceType<typeof BABYLON.Mesh>>([ground]);

    const overlayMeshes = new Set<InstanceType<typeof BABYLON.Mesh>>();
    overlayMeshesRef.current = overlayMeshes;
    const overlay = createOverlayController(B, babylonScene, overlayMeshes);
    const selectionGizmo = createSelectionGizmo(
      B,
      babylonScene,
      clipBounds,
      (box) => setSelection(box),
      flashActionError,
    );
    selectionGizmo.setShape(selectionShapeRef.current);
    selectionGizmo.setSnap(selectionSnapRef.current);
    selectionGizmoRef.current = selectionGizmo;
    let disposed = false;

    // Whole-volume load: fetch the raw binary buffer once and mesh it in one call — no
    // chunk-streaming equivalent, since a Scene is always small enough to load in full. The live
    // edit socket only opens once this initial bulk load has succeeded, so a broadcast edit from
    // another editor never lands on a buffer that isn't meshed yet.
    let editSocket: BlockEditSocket<SceneBlockDto> | null = null;

    async function loadScene() {
      const exports = await window.webApp!;
      // Binary payload (application/octet-stream) — not a JSON API, kept as a manual fetch.
      const buf = await fetch(`/api/admin/scenes/${encodeURIComponent(scene.id)}/blocks/raw`).then((r) =>
        r.arrayBuffer(),
      );
      const { width, height, depth, blocks, states } = parseSceneRaw(buf);
      exports.mcSceneLoad(babylonScene, width, height, depth, blocks, states);
      applyClipPlanes(B!, babylonScene, clipPlanesRef.current, clipBounds, overlayMeshes, clipMeshesRef.current);
      for (const m of babylonScene.meshes) {
        const mesh = m as InstanceType<typeof BABYLON.Mesh>;
        if (!groundMeshes.has(mesh) && !overlayMeshes.has(mesh)) mesh.isPickable = true;
      }
      if (disposed) return;
      editSocket = connectEditSocket<SceneBlockDto>(
        "scenes",
        scene.id,
        (edit) => {
          const ordinal = ordinalByNameRef.current.get(edit.type) ?? (edit.type === "AIR" ? 0 : null);
          if (ordinal != null) exportsSetBlock(edit.x, edit.y, edit.z, ordinal, edit.state);
        },
        (message) => flashActionError(message),
        // A batch broadcast from another admin tab (e.g. its own Fill/Shell/Cut). No batch variant
        // of mcSceneSetBlock exists — each voxel still remeshes the whole buffer individually, a
        // known cost bounded by MAX_SELECTION_OP_VOXELS.
        (edits) => {
          for (const edit of edits) {
            const ordinal = ordinalByNameRef.current.get(edit.type) ?? (edit.type === "AIR" ? 0 : null);
            if (ordinal != null) exportsSetBlock(edit.x, edit.y, edit.z, ordinal, edit.state);
          }
        },
      );
    }

    loadScene().catch((e) => {
      console.error("Failed to load scene", e);
      if (!disposed) setLoadError(String(e));
    });

    function captureBlock(at: UndoEntry): UndoEntry {
      if (!wasmExports) return { x: at.x, y: at.y, z: at.z, type: "AIR", state: 0 };
      const ordinal = wasmExports.mcSceneGetBlockOrdinalAt(at.x, at.y, at.z);
      const state = wasmExports.mcSceneGetBlockStateAt(at.x, at.y, at.z);
      const type = window.mc.getBlockDef(ordinal)?.name ?? "AIR";
      return { x: at.x, y: at.y, z: at.z, type, state };
    }

    function exportsSetBlock(x: number, y: number, z: number, ordinal: number, state: number) {
      if (!wasmExports) return;
      wasmExports.mcSceneSetBlock(babylonScene, x, y, z, ordinal, state);
      // mcSceneSetBlock re-meshes the buffer, and chunkBuilder.ts defaults every new mesh to
      // isPickable=false — without re-enabling it here, a block placed on top of a just-placed
      // block can never be picked, so stacking silently caps at one block high (only the ground
      // stays clickable). Mirrors the same re-enable loop loadScene() runs after its initial mesh.
      for (const m of babylonScene.meshes) {
        const mesh = m as InstanceType<typeof BABYLON.Mesh>;
        if (!groundMeshes.has(mesh) && !overlayMeshes.has(mesh)) mesh.isPickable = true;
      }
    }

    // Applies an edit both server-side (via the live edit socket, the only write path) and
    // locally via the WASM mesher (instant, optimistic — no round-trip wait). Mirrors
    // InstanceEditorViewport's applyEntry, minus the chunk reload (mcSceneSetBlock re-meshes the
    // whole buffer synchronously).
    function applyEntry(entry: UndoEntry, onSuccess: () => void) {
      const ordinal = ordinalByNameRef.current.get(entry.type) ?? (entry.type === "AIR" ? 0 : null);
      editSocket?.send({ x: entry.x, y: entry.y, z: entry.z, type: entry.type, state: entry.state });
      onSuccess();
      if (ordinal != null) exportsSetBlock(entry.x, entry.y, entry.z, ordinal, entry.state);
    }

    const { pushUndo, performUndo, performRedo } = makeUndoRedoController<UndoEntry>(
      undoStackRef,
      redoStackRef,
      captureBlock,
      applyEntry,
    );

    // Fill/Shell/Cut on the current selection — one WS batch frame (editSocket.sendBatch) instead
    // of N individual sends, one undo-stack group instead of N slots. See selectionVoxels.ts for
    // the voxel enumeration/cap.
    runSelectionOpRef.current = (kind: "fill" | "shell" | "cut") => {
      const box = selectionRef.current;
      if (!box) return;
      if (kind !== "cut" && !patternBlocksRef.current[0]) return;
      const voxels = computeSelectionVoxels(box, selectionShapeRef.current, kind === "cut" ? "fill" : kind);
      if (!voxels) {
        flashActionError(`Selection too large (max ${MAX_SELECTION_OP_VOXELS} voxels)`);
        return;
      }
      const valid = voxels.filter((v) => inBounds(v.x, v.y, v.z));
      if (valid.length === 0) return;

      const pattern: Pattern = { a: patternBlocksRef.current[0] ?? "AIR", b: patternBlocksRef.current[1] ?? undefined };
      const originX = Math.floor(box.minX);
      const originY = Math.floor(box.minY);
      const originZ = Math.floor(box.minZ);
      const edits: SceneBlockDto[] = [];
      const undoGroup: UndoEntry[] = [];
      const clipEntries: { relX: number; relY: number; relZ: number; type: string; state: number }[] = [];

      for (const v of valid) {
        const prevOrdinal = wasmExports ? wasmExports.mcSceneGetBlockOrdinalAt(v.x, v.y, v.z) : 0;
        const prevState = wasmExports ? wasmExports.mcSceneGetBlockStateAt(v.x, v.y, v.z) : 0;
        const prevType = wasmExports ? (window.mc.getBlockDef(prevOrdinal)?.name ?? "AIR") : "AIR";
        undoGroup.push({ x: v.x, y: v.y, z: v.z, type: prevType, state: prevState });

        const type = kind === "cut" ? "AIR" : resolvePatternBlock(pattern, v.x, v.y, v.z);
        if (kind === "cut") {
          clipEntries.push({
            relX: v.x - originX,
            relY: v.y - originY,
            relZ: v.z - originZ,
            type: prevType,
            state: prevState,
          });
        }
        edits.push({ x: v.x, y: v.y, z: v.z, type, state: 0 });
      }

      editSocket?.sendBatch(edits);
      pushUndo(undoGroup);
      for (const edit of edits) {
        const ordinal = ordinalByNameRef.current.get(edit.type) ?? (edit.type === "AIR" ? 0 : null);
        if (ordinal != null) exportsSetBlock(edit.x, edit.y, edit.z, ordinal, edit.state);
      }
      if (kind === "cut") {
        clipboardRef.current = { entries: clipEntries };
        setClipboardCount(clipEntries.length);
      }
    };

    // Resolves the cell a placement click should target: the empty neighbor cell in the direction
    // of the clicked face — simpler than InstanceEditorViewport's resolvePlacementCell since Scene
    // has no fractional-entity system to redirect into the clicked block's own cell for.
    function resolvePlacementCell(
      pickedPoint: InstanceType<typeof BABYLON.Vector3>,
      normal: InstanceType<typeof BABYLON.Vector3>,
    ): [number, number, number] {
      return [
        Math.floor(pickedPoint.x + normal.x * PICK_EPSILON),
        Math.floor(pickedPoint.y + normal.y * PICK_EPSILON),
        Math.floor(pickedPoint.z + normal.z * PICK_EPSILON),
      ];
    }

    function updateHoverPreview(evt: PointerEvent) {
      if (modeRef.current === "select") {
        overlay.disposeAll();
        return;
      }
      const effectiveMode = evt.shiftKey ? "break" : modeRef.current;
      const pick = babylonScene.pick(babylonScene.pointerX, babylonScene.pointerY);
      if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) {
        overlay.disposeAll();
        return;
      }
      const normal = pick.getNormal(true);
      const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);

      if (effectiveMode === "break") {
        overlay.disposeGhost();
        overlay.disposeOutline();
        if (onGround || !normal || !wasmExports) {
          overlay.disposeBreakOverlay();
          return;
        }
        const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
        const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
        const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
        if (!inBounds(bx, by, bz)) {
          overlay.disposeBreakOverlay();
          return;
        }
        const targetOrdinal = wasmExports.mcSceneGetBlockOrdinalAt(bx, by, bz);
        const targetState = wasmExports.mcSceneGetBlockStateAt(bx, by, bz);
        const rotation = targetState & 0x03;
        overlay.showBreakOverlay(bx, by, bz, targetOrdinal, rotation);
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
        ty = 0;
        tz = Math.floor(pick.pickedPoint.z);
      } else if (normal) {
        [tx, ty, tz] = resolvePlacementCell(pick.pickedPoint, normal);
      } else {
        overlay.disposeGhost();
        overlay.disposeOutline();
        return;
      }
      if (!inBounds(tx, ty, tz)) {
        overlay.disposeGhost();
        overlay.disposeOutline();
        return;
      }
      overlay.showGhostAndOutline(tx, ty, tz, ordinal, selectedColorIndexRef.current);
    }

    function onKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) return;
      if (e.code === "KeyU") {
        performUndo();
        return;
      }
      if (e.code === "KeyY") {
        performRedo();
        return;
      }
      if (e.code !== "KeyR") return;
      overlay.rotatePlacement();
      updateHoverPreview({ shiftKey: e.shiftKey } as PointerEvent);
    }
    window.addEventListener("keydown", onKeyDown);

    const disposePointerController = setupOrbitPointerController({
      B,
      scene: babylonScene,
      camera,
      canvas,
      getMode: () => modeRef.current,
      onHoverMove: updateHoverPreview,
      onClick: ({ pick, normal, mode: currentMode, shiftKey }) => {
        const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);

        if (currentMode === "select") {
          if (onGround || !normal || !pick.pickedPoint) return;
          const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
          const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
          const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
          if (!inBounds(bx, by, bz)) return;
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
          if (!inBounds(bx, by, bz)) return;
          let prevType = "AIR";
          let prevState = 0;
          if (wasmExports) {
            const targetOrdinal = wasmExports.mcSceneGetBlockOrdinalAt(bx, by, bz);
            prevState = wasmExports.mcSceneGetBlockStateAt(bx, by, bz);
            prevType = window.mc.getBlockDef(targetOrdinal)?.name ?? "AIR";
          }
          editSocket?.send({ x: bx, y: by, z: bz, type: "AIR", state: 0 });
          pushUndo({ x: bx, y: by, z: bz, type: prevType, state: prevState });
          exportsSetBlock(bx, by, bz, 0, 0);
          return;
        }

        const type = selectedTypeRef.current;
        if (!type || !pick.pickedPoint) return;
        let tx: number, ty: number, tz: number;
        if (onGround) {
          tx = Math.floor(pick.pickedPoint.x);
          ty = 0;
          tz = Math.floor(pick.pickedPoint.z);
        } else if (normal) {
          [tx, ty, tz] = resolvePlacementCell(pick.pickedPoint, normal);
        } else {
          return;
        }
        if (!inBounds(tx, ty, tz)) return;
        const ordinal = ordinalByNameRef.current.get(type);
        if (ordinal == null) return;
        let prevType = "AIR";
        let prevState = 0;
        if (wasmExports) {
          const prevOrdinal = wasmExports.mcSceneGetBlockOrdinalAt(tx, ty, tz);
          prevState = wasmExports.mcSceneGetBlockStateAt(tx, ty, tz);
          prevType = window.mc.getBlockDef(prevOrdinal)?.name ?? "AIR";
        }
        const state = packState(overlay.getPlacementRotation(), selectedColorIndexRef.current);
        editSocket?.send({ x: tx, y: ty, z: tz, type, state });
        pushUndo({ x: tx, y: ty, z: tz, type: prevType, state: prevState });
        exportsSetBlock(tx, ty, tz, ordinal, state);
      },
    });

    const axesGizmo = createAxesGizmo(B, engine, camera);
    engine.runRenderLoop(() => {
      babylonScene.render();
      axesGizmo.render();
    });
    const onResize = () => engine.resize();
    window.addEventListener("resize", onResize);

    let lastCameraSave = 0;
    const viewMatrixObserver = camera.onViewMatrixChangedObservable.add(() => {
      const now = performance.now();
      if (now - lastCameraSave < 300) return;
      lastCameraSave = now;
      saveCameraState(cameraStorageKey(scene.id), {
        alpha: camera.alpha,
        beta: camera.beta,
        radius: camera.radius,
        targetX: camera.target.x,
        targetY: camera.target.y,
        targetZ: camera.target.z,
      });
    });

    return () => {
      disposed = true;
      camera.onViewMatrixChangedObservable.remove(viewMatrixObserver);
      disposePointerController();
      window.removeEventListener("resize", onResize);
      window.removeEventListener("keydown", onKeyDown);
      overlay.disposeAll();
      selectionGizmo.dispose();
      editSocket?.close();
      window.webApp?.then((exports) => exports.mcSceneDispose()).catch(() => {});
      axesGizmo.dispose();
      engine.dispose();
      sceneRef.current = null;
      overlayMeshesRef.current = null;
      selectionGizmoRef.current = null;
      clipMeshesRef.current = {};
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- scene.id identity is the intended re-mount trigger
  }, [scene.id, blockDefsReady]);

  useEffect(() => {
    const babylonScene = sceneRef.current;
    const overlayMeshes = overlayMeshesRef.current;
    const B = window.BABYLON;
    if (!babylonScene || !overlayMeshes || !B) return;
    applyClipPlanes(B, babylonScene, clipPlanes, clipBounds, overlayMeshes, clipMeshesRef.current);
  }, [clipPlanes, clipBounds, scene.id, blockDefsReady]);

  // Mirrors the selection state into the gizmo — null both when no selection was made yet and
  // whenever the editor leaves select mode (see the mode effect above, which clears `selection`).
  useEffect(() => {
    selectionRef.current = selection;
    selectionGizmoRef.current?.setSelection(selection);
  }, [selection]);

  return (
    <div className="flex-1 flex overflow-hidden">
      <div className="flex-[4] relative">
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
        patternBlocks={patternBlocks}
        activePatternSlot={activePatternSlot}
        onSelectPatternSlot={setActivePatternSlot}
        onClearPatternSlot={clearPatternSlot}
        onFill={() => runSelectionOp("fill")}
        onShell={() => runSelectionOp("shell")}
        onCut={() => runSelectionOp("cut")}
        clipboardCount={clipboardCount}
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
      />
    </div>
  );
}
