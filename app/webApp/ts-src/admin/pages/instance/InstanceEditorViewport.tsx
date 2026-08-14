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
import { createSelectionGizmo, type SelectionBox, type SelectionShape } from "../shared/voxelEditor/selectionGizmo";
import { useBlockRegistry } from "../shared/voxelEditor/useBlockRegistry";
import { useModifierDragMode } from "../shared/voxelEditor/useModifierDragMode";
import { useActionError } from "../shared/voxelEditor/useActionError";
import { makeUndoRedoController, type UndoEntryBase } from "../shared/voxelEditor/undoRedoStack";
import { VoxelEditorSidebar } from "../shared/voxelEditor/VoxelEditorSidebar";
import { connectEditSocket } from "../shared/voxelEditor/editSocket";
import type { InstanceBlockDto } from "../../apiTypes";

const CHUNK_SIZE = 16;
// How far (in chunks) around the camera target to keep block geometry loaded. Scales with
// zoom (camera.radius) so zooming out pulls in more chunks, capped so a huge zone can't force
// thousands of simultaneous chunk fetches.
const MAX_VIEW_RADIUS_CHUNKS = 8;
// Hysteresis margin: a chunk is only unloaded once it's this far past the load radius, so
// camera jitter right at the boundary doesn't thrash fetch/hide every frame.
const VIEW_RADIUS_UNLOAD_MARGIN = 1;
// Voxel-picking epsilon: nudges the picked point across the hit face along its normal before
// flooring, so the coordinate resolves to the block on the correct side of the face.
const PICK_EPSILON = 0.01;

const cameraStorageKey = (zoneId: string) => `instanceEditorCamera:${zoneId}`;

// Snapshot of a cell's content right before a place/break overwrote it, so undo can write it back.
interface UndoEntry extends UndoEntryBase {
  xOffset: number;
  zOffset: number;
}

// XZ sub-voxel slot targeted within cell (tx,tz), mirroring the in-game hover math
// (LocalPlayerController.kt) so the admin editor's ghost/break-overlay and the resulting
// api.instances.setBlock call agree on the same slot the player is visually aiming at.
function computeSlotOffset(
  pickedX: number,
  pickedZ: number,
  tx: number,
  tz: number,
  brickSizeX: number,
  brickSizeZ: number,
  rotation: number,
): [number, number] {
  const effFracX = rotation % 2 === 0 ? brickSizeX : brickSizeZ;
  const effFracZ = rotation % 2 === 0 ? brickSizeZ : brickSizeX;
  const studStepX = effFracX < 1 ? effFracX : effFracX > 1 ? 0.5 : 0;
  const studStepZ = effFracZ < 1 ? effFracZ : effFracZ > 1 ? 0.5 : 0;
  if (studStepX <= 0 && studStepZ <= 0) return [0, 0];
  const fracX = Math.min(0.9999, Math.max(0, pickedX - tx));
  const fracZ = Math.min(0.9999, Math.max(0, pickedZ - tz));
  const slotsX = studStepX > 0 ? Math.max(1, Math.floor(1 / studStepX)) : 1;
  const slotsZ = studStepZ > 0 ? Math.max(1, Math.floor(1 / studStepZ)) : 1;
  const xOffset = studStepX > 0 ? Math.min(slotsX - 1, Math.floor(fracX / studStepX)) : 0;
  const zOffset = studStepZ > 0 ? Math.min(slotsZ - 1, Math.floor(fracZ / studStepZ)) : 0;
  return [xOffset, zOffset];
}

export function InstanceEditorViewport({ zone }: { zone: InstanceZoneDto }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const paletteRef = useRef<HTMLDivElement>(null);
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
  const [loadError, setLoadError] = useState<string | null>(null);
  const { actionError, flashActionError } = useActionError();
  const [search, setSearch] = useState("");
  const modeRef = useRef(mode);
  const selectedTypeRef = useRef(selectedType);
  const ordinalByNameRef = useRef(ordinalByName);
  // In-memory only (reset on zone remount, like the scene itself) — not persisted, unlike camera/shortcut bar.
  const undoStackRef = useRef<UndoEntry[]>([]);
  const redoStackRef = useRef<UndoEntry[]>([]);

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
    }
  }, [mode]);

  useEffect(() => {
    selectionShapeRef.current = selectionShape;
    selectionGizmoRef.current?.setShape(selectionShape);
  }, [selectionShape]);

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

  // Resets any color pick made for a previous block — a color index only makes sense paired with
  // the plainColorable block it was chosen for.
  function selectBlockType(name: string) {
    setMode("place");
    setSelectedType(name);
    setSelectedColorIndex(0);
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
    const selectionGizmo = createSelectionGizmo(B, scene, clipBounds, (box) => setSelection(box));
    selectionGizmo.setShape(selectionShapeRef.current);
    selectionGizmoRef.current = selectionGizmo;
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
        .then(() => visibleChunks.add(key))
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
    );

    function captureBlock(at: UndoEntry): UndoEntry {
      if (!wasmExports)
        return { x: at.x, y: at.y, z: at.z, type: "AIR", state: 0, xOffset: at.xOffset, zOffset: at.zOffset };
      const ordinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, at.x, at.y, at.z);
      const state = wasmExports.mcAdminGetBlockStateAt(scene, at.x, at.y, at.z);
      const type = window.mc.getBlockDef(ordinal)?.name ?? "AIR";
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

    // Resolves the cell a placement click should target: normally the empty neighbor cell in the
    // direction of the clicked face (standard adjacent-placement), but redirected into the
    // clicked block's OWN cell when that block is an XZ+Y-fractional entity (e.g. LEGO_PIECE) —
    // otherwise a second piece in a different slot of the same voxel could never be reached, since
    // the first piece is solid and blocks the ray from ever reaching past it. Mirrors the same
    // lateral-redirect fix in LocalPlayerController.kt for the real game client.
    function resolvePlacementCell(
      pickedPoint: InstanceType<typeof BABYLON.Vector3>,
      normal: InstanceType<typeof BABYLON.Vector3>,
    ): [number, number, number] {
      const adjX = Math.floor(pickedPoint.x + normal.x * PICK_EPSILON);
      const adjY = Math.floor(pickedPoint.y + normal.y * PICK_EPSILON);
      const adjZ = Math.floor(pickedPoint.z + normal.z * PICK_EPSILON);
      const tgtX = Math.floor(pickedPoint.x - normal.x * PICK_EPSILON);
      const tgtY = Math.floor(pickedPoint.y - normal.y * PICK_EPSILON);
      const tgtZ = Math.floor(pickedPoint.z - normal.z * PICK_EPSILON);
      if (adjY === tgtY && wasmExports) {
        const targetOrdinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, tgtX, tgtY, tgtZ);
        const targetDef = window.mc.getBlockDef(targetOrdinal);
        if ((targetDef?.heightFraction ?? 1) < 1) return [tgtX, tgtY, tgtZ];
      }
      return [adjX, adjY, adjZ];
    }

    // Hover preview: recomputed on every pointer move that isn't an active camera drag, so the
    // ghost/outline (place mode) or the red break overlay (break mode) track the cursor before a
    // click, exactly like moving the mouse in-game moves the block-placement preview.
    function updateHoverPreview(evt: PointerEvent) {
      if (modeRef.current === "select") {
        overlay.disposeAll();
        return;
      }
      const effectiveMode = evt.shiftKey ? "break" : modeRef.current;
      const pick = scene.pick(scene.pointerX, scene.pointerY);
      if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) {
        overlay.disposeAll();
        return;
      }
      const normal = pick.getNormal(true);
      const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);

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
        const bs = targetDef?.brickSize ?? [1, 1, 1];
        const [xOffset, zOffset] = computeSlotOffset(
          pick.pickedPoint.x,
          pick.pickedPoint.z,
          bx,
          bz,
          bs[0],
          bs[2],
          rotation,
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
        [tx, ty, tz] = resolvePlacementCell(pick.pickedPoint, normal);
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
      const bs = blockDef?.brickSize ?? [1, 1, 1];
      const [xOffset, zOffset] = computeSlotOffset(
        pick.pickedPoint.x,
        pick.pickedPoint.z,
        tx,
        tz,
        bs[0],
        bs[2],
        overlay.getPlacementRotation(),
      );
      overlay.showGhostAndOutline(tx, ty, tz, ordinal, selectedColorIndexRef.current, xOffset, zOffset);
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
      onClick: ({ pick, normal, mode: currentMode, shiftKey }) => {
        const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);

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
            const bs = targetDef?.brickSize ?? [1, 1, 1];
            [breakXOffset, breakZOffset] = computeSlotOffset(
              pick.pickedPoint.x,
              pick.pickedPoint.z,
              bx,
              bz,
              bs[0],
              bs[2],
              rotation,
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
          [tx, ty, tz] = resolvePlacementCell(pick.pickedPoint, normal);
        } else {
          return;
        }
        if (ty < zone.yMin || ty > zone.yMax || !inZone(tx, tz)) return;
        const ordinal = ordinalByNameRef.current.get(type);
        const placeDef = ordinal != null ? window.mc.getBlockDef(ordinal) : undefined;
        const placeBs = placeDef?.brickSize ?? [1, 1, 1];
        const [xOffset, zOffset] = computeSlotOffset(
          pick.pickedPoint.x,
          pick.pickedPoint.z,
          tx,
          tz,
          placeBs[0],
          placeBs[2],
          overlay.getPlacementRotation(),
        );
        let prevType = "AIR";
        let prevState = 0;
        if (wasmExports) {
          const prevOrdinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, tx, ty, tz);
          // Full state byte (rotation + color, see BlockState.kt) so undo restores the exact
          // previous look, not just its rotation.
          prevState = wasmExports.mcAdminGetBlockStateAt(scene, tx, ty, tz);
          prevType = window.mc.getBlockDef(prevOrdinal)?.name ?? "AIR";
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
      editSocket.close();
      axesGizmo.dispose();
      engine.dispose();
      sceneRef.current = null;
      overlayMeshesRef.current = null;
      selectionGizmoRef.current = null;
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
      />
    </div>
  );
}
