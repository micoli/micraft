import { useEffect, useRef, useState } from "react";
import { api, type BlockInfoDto, type InstanceZoneDto } from "../../api";
import { Block3DPreview, CssBlockCube, useBlockDefsReady } from "../../../game/shared/BlockPreview";
import { useInstanceShortcutBar } from "../../hooks/useInstanceShortcutBar";
import { buildBlockPreviewMeshes } from "../../../game/chunks/chunkBuilder";
import { boxLines } from "../../../game/targeting/targeting";

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

function rgbToHex([r, g, b]: [number, number, number]): string {
  return `#${[r, g, b].map((v) => v.toString(16).padStart(2, "0")).join("")}`;
}

interface StoredCameraState {
  alpha: number;
  beta: number;
  radius: number;
  targetX: number;
  targetY: number;
  targetZ: number;
}

const cameraStorageKey = (zoneId: string) => `instanceEditorCamera:${zoneId}`;

function loadCameraState(zoneId: string): StoredCameraState | null {
  try {
    const raw = localStorage.getItem(cameraStorageKey(zoneId));
    if (!raw) return null;
    return JSON.parse(raw) as StoredCameraState;
  } catch {
    return null;
  }
}

function saveCameraState(zoneId: string, state: StoredCameraState) {
  try {
    localStorage.setItem(cameraStorageKey(zoneId), JSON.stringify(state));
  } catch {
    // localStorage unavailable (private mode / quota) — camera position just won't persist.
  }
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
  const [tooltipAbove, setTooltipAbove] = useState(false);
  const [blockDefs, setBlockDefs] = useState<BlockInfoDto[]>([]);
  // admin.js and mc_bindings.js are separate esbuild bundles: each has its own copy of
  // blockDefs.ts module state, so getBlockOrdinalByName() from that module is always null
  // here. Ordinal is just the block's index in the raw registry array we sent to window.mc.
  const [ordinalByName, setOrdinalByName] = useState<Map<string, number>>(new Map());
  const [selectedType, setSelectedType] = useState<string | null>(null);
  const [mode, setMode] = useState<"place" | "break">("place");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const actionErrorTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [search, setSearch] = useState("");
  const modeRef = useRef(mode);
  const selectedTypeRef = useRef(selectedType);
  const ordinalByNameRef = useRef(ordinalByName);
  // PUT /blocks resolves normally even on a 4xx (fetch only rejects on network errors), so a
  // rejected placement/break (e.g. slot full, zone disabled, occupied by a different block) would
  // otherwise fail silently — the ghost just vanishes with no feedback. Surfaces the server's
  // rejection reason briefly instead.
  function flashActionError(message: string) {
    setActionError(message);
    if (actionErrorTimeout.current) clearTimeout(actionErrorTimeout.current);
    actionErrorTimeout.current = setTimeout(() => setActionError(null), 4000);
  }

  useEffect(() => {
    return () => {
      if (actionErrorTimeout.current) clearTimeout(actionErrorTimeout.current);
    };
  }, []);

  useEffect(() => {
    modeRef.current = mode;
  }, [mode]);
  useEffect(() => {
    selectedTypeRef.current = selectedType;
  }, [selectedType]);
  useEffect(() => {
    ordinalByNameRef.current = ordinalByName;
  }, [ordinalByName]);

  const blockDefsReady = useBlockDefsReady();

  const shortcutBar = useInstanceShortcutBar({
    onSelectBreak: () => setMode("break"),
    onSelectBlock: (blockName) => {
      setMode("place");
      setSelectedType(blockName);
    },
  });

  // Tracks which camera-drag modifier is currently held, to highlight the matching badge in the
  // legend overlay — mirrors the shiftKey/metaKey/altKey precedence the pointer handler below uses.
  const [modKeys, setModKeys] = useState({ shift: false, meta: false, alt: false, ctrl: false });
  useEffect(() => {
    const update = (e: KeyboardEvent) =>
      setModKeys({ shift: e.shiftKey, meta: e.metaKey, alt: e.altKey, ctrl: e.ctrlKey });
    const reset = () => setModKeys({ shift: false, meta: false, alt: false, ctrl: false });
    window.addEventListener("keydown", update);
    window.addEventListener("keyup", update);
    window.addEventListener("blur", reset);
    return () => {
      window.removeEventListener("keydown", update);
      window.removeEventListener("keyup", update);
      window.removeEventListener("blur", reset);
    };
  }, []);
  const activeDragMode = modKeys.meta ? "rotate" : modKeys.alt ? "pan" : modKeys.ctrl ? "zoom" : "place";

  useEffect(() => {
    api.blocks
      .list()
      .then((defs) => {
        const withoutAir = defs.filter((b) => b.name !== "AIR");
        setBlockDefs(withoutAir);
        setOrdinalByName(new Map(defs.map((b, i) => [b.name, i])));
        window.mc.setBlockRegistry?.(JSON.stringify(defs));
        window.webApp?.then((exports) => exports.mcAdminSetBlockRegistry(JSON.stringify(defs)));
      })
      .catch(console.error);
  }, []);

  const getOrdinal = (name: string): number | null => ordinalByName.get(name) ?? null;

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

    const savedCamera = loadCameraState(zone.id);
    const camera = new B.ArcRotateCamera(
      "cam",
      savedCamera?.alpha ?? -Math.PI * 0.35,
      savedCamera?.beta ?? Math.PI / 3,
      savedCamera?.radius ?? span * 1.8,
      savedCamera
        ? new B.Vector3(savedCamera.targetX, savedCamera.targetY, savedCamera.targetZ)
        : new B.Vector3(centerX, centerY, centerZ),
      scene,
    );
    camera.attachControl(canvas, true);
    // Default pointer input (left-drag rotate, right-drag pan) is replaced below by a modifier-key
    // driven scheme (Shift=place/break, Cmd=rotate, Option=pan, Ctrl=zoom, none=place), so the built-in
    // pointers input would just fight the custom one for the same drag.
    camera.inputs.removeByType("ArcRotateCameraPointersInput");
    camera.wheelPrecision = 20;
    camera.lowerRadiusLimit = 2;

    const light = new B.HemisphericLight("light", new B.Vector3(0.5, 1, 0.3), scene);
    light.intensity = 1.0;
    const sun = new B.DirectionalLight("sun", new B.Vector3(-0.3, -1, -0.2), scene);
    sun.intensity = 0.6;

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
    // around them (see loadChunk below) — populated by showGhostAndOutline/disposeGhost/disposeOutline.
    const overlayMeshes = new Set<InstanceType<typeof BABYLON.Mesh>>();
    let disposed = false;

    async function loadChunk(cx: number, cz: number) {
      const exports = await window.webApp!;
      const res = await fetch(`/api/chunks/${cx}/${cz}`);
      if (!res.ok) throw new Error(`chunk fetch failed: ${res.status}`);
      const bytes = new Uint8Array(await res.arrayBuffer());
      exports.mcAdminLoadChunk(scene, bytes, zone.yMin, zone.yMax);
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
      saveCameraState(zone.id, {
        alpha: camera.alpha,
        beta: camera.beta,
        radius: camera.radius,
        targetX: camera.target.x,
        targetY: camera.target.y,
        targetZ: camera.target.z,
      });
    });

    canvas.addEventListener("contextmenu", (e) => e.preventDefault());

    // Real chunk meshes are merged per-material geometry, not one pickable object per block —
    // the targeted block coordinate is derived from the hit point nudged across the face along
    // its normal (standard voxel-picking technique), same idea the in-game block targeting uses.
    //
    // Drag mode is decided by whichever modifier key was held at POINTERDOWN: no modifier =
    // place/break (picking must happen on POINTERUP so a drag-start doesn't fire it early — a
    // move threshold distinguishes an actual click from the start of a drag), Cmd/Meta = rotate,
    // Option/Alt = pan, Ctrl = zoom. Shift held during that click breaks instead of placing,
    // overriding the persistent place/break mode for just that click. camera.getWorldMatrix()
    // gives the camera's current screen-space right/up axes so panning tracks the drag direction
    // regardless of orbit angle.
    let downX = 0;
    let downY = 0;
    let lastX = 0;
    let lastY = 0;
    let downShift = false;
    let dragMode: "rotate" | "pan" | "zoom" | "place" | null = null;
    const CLICK_MOVE_THRESHOLD = 5;
    const ROTATE_SENSITIVITY = 0.005;
    const PAN_SENSITIVITY = 0.0015;
    const ZOOM_SENSITIVITY = 0.01;

    function panCamera(dx: number, dy: number) {
      const m = camera.getWorldMatrix();
      const right = B!.Vector3.TransformNormal(new B!.Vector3(1, 0, 0), m).normalize();
      const up = B!.Vector3.TransformNormal(new B!.Vector3(0, 1, 0), m).normalize();
      const scale = camera.radius * PAN_SENSITIVITY;
      camera.target.addInPlace(right.scale(-dx * scale)).addInPlace(up.scale(dy * scale));
    }

    // Placement ghost + target outline, mirroring the in-game preview (ghostBlock.ts /
    // targeting.ts) but driven by scene.pick() hover instead of a first-person raycast, and
    // rebuilt only when the block type or rotation actually changes (geo key match) rather than
    // every pointer move.
    let placementRotation = 0;
    let ghostMeshes: ReturnType<typeof buildBlockPreviewMeshes> = [];
    let ghostGeoKey: string | null = null;
    let outlineMesh: InstanceType<typeof BABYLON.LinesMesh> | null = null;

    function disposeGhost() {
      ghostMeshes.forEach((m) => {
        overlayMeshes.delete(m);
        m.dispose();
      });
      ghostMeshes = [];
      ghostGeoKey = null;
    }

    function disposeOutline() {
      if (outlineMesh) overlayMeshes.delete(outlineMesh);
      outlineMesh?.dispose();
      outlineMesh = null;
    }

    let breakMesh: InstanceType<typeof BABYLON.Mesh> | null = null;
    let breakMeshKey: string | null = null;

    function disposeBreakOverlay() {
      if (breakMesh) overlayMeshes.delete(breakMesh);
      breakMesh?.dispose();
      breakMesh = null;
      breakMeshKey = null;
    }

    // In break mode the ghost is replaced by a solid red box over the targeted block, instead of
    // the placement preview — same idea as the in-game break overlay (targeting.ts), but a filled
    // box rather than a wireframe pulse since there's no mining-progress value to animate here.
    function showBreakOverlay(x: number, y: number, z: number) {
      const key = `${x},${y},${z}`;
      if (breakMeshKey === key) return;
      disposeBreakOverlay();
      const box = B!.MeshBuilder.CreateBox("breakOverlay", { size: 1 }, scene);
      box.position = new B!.Vector3(x + 0.5, y + 0.5, z + 0.5);
      const mat = new B!.StandardMaterial("breakOverlayMat", scene);
      mat.diffuseColor = new B!.Color3(1, 0, 0);
      mat.emissiveColor = new B!.Color3(1, 0, 0);
      mat.alpha = 0.3;
      mat.disableDepthWrite = true;
      mat.backFaceCulling = false;
      box.material = mat;
      box.isPickable = false;
      box.renderingGroupId = 1;
      breakMesh = box;
      breakMeshKey = key;
      overlayMeshes.add(box);
    }

    function showGhostAndOutline(x: number, y: number, z: number, ordinal: number, xOffset = 0, zOffset = 0) {
      const geoKey = `${ordinal},${placementRotation}`;
      if (ghostGeoKey !== geoKey) {
        disposeGhost();
        ghostMeshes = buildBlockPreviewMeshes(scene, ordinal, placementRotation);
        ghostGeoKey = geoKey;
        // The ghost shader (see getOrCreateGhostMat in chunkBuilder.ts) has backFaceCulling=false
        // and a zOffset/zOffsetUnits bias tuned for the in-game FPS-scale render distance. Both
        // are harmless up close but misbehave at this editor's much larger orbit-camera distances:
        // the depth bias becomes a huge world-space displacement (depth precision is non-linear),
        // and because the alpha-blended mesh still writes depth, its own back faces and front
        // faces fight over which "wins" per pixel — which reads as the ghost smearing/thickening
        // toward the camera the more of the scene is in view. Neutralize the bias and stop it from
        // writing depth — standard practice for a translucent overlay. The material instance is
        // cached per admin.js's own module copy, so this doesn't touch the in-game renderer.
        for (const m of ghostMeshes) {
          if (m.material) {
            m.material.zOffset = 0;
            m.material.zOffsetUnits = 0;
            m.material.disableDepthWrite = true;
          }
          m.renderingGroupId = 1;
          overlayMeshes.add(m);
        }
      }
      // Multi-voxel props (brickSize > 1 on an axis) have a real footprint bigger than one block —
      // a hardcoded 1×1×1 outline would look like a mismatched sliver stuck in the corner of a much
      // bigger ghost mesh. Same rotation-aware sizing as the in-game target outline (targeting.ts).
      const blockDef = window.mc.getBlockDef(ordinal);
      const bs = blockDef?.brickSize ?? [1, 1, 1];

      // Sub-voxel position offset from brickSize fractions, mirroring ghostBlock.ts.
      const fracX = bs[0] < 1 ? bs[0] : bs[0] > 1 ? 0.5 : 0;
      const fracZ = bs[2] < 1 ? bs[2] : bs[2] > 1 ? 0.5 : 0;
      const pos = new B!.Vector3(x + xOffset * fracX, y, z + zOffset * fracZ);
      for (const m of ghostMeshes) m.position = pos;

      const rot90 = placementRotation === 1 || placementRotation === 3;
      const worldSizeX = rot90 ? (bs[2] < 1 ? bs[2] : Math.ceil(bs[2])) : bs[0] < 1 ? bs[0] : Math.ceil(bs[0]);
      const worldSizeY = bs[1] < 1 ? bs[1] : Math.ceil(bs[1]);
      const worldSizeZ = rot90 ? (bs[0] < 1 ? bs[0] : Math.ceil(bs[0])) : bs[2] < 1 ? bs[2] : Math.ceil(bs[2]);

      disposeOutline();
      const ox = x + xOffset * fracX;
      const oz = z + zOffset * fracZ;
      const ls = B!.MeshBuilder.CreateLineSystem(
        "placeOutline",
        { lines: boxLines(ox, y, oz, ox + worldSizeX, y + worldSizeY, oz + worldSizeZ) },
        scene,
      );
      ls.color = new B!.Color3(1, 1, 1);
      ls.isPickable = false;
      ls.renderingGroupId = 1;
      outlineMesh = ls;
      overlayMeshes.add(ls);
    }

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
      const effectiveMode = evt.shiftKey ? "break" : modeRef.current;
      const pick = scene.pick(scene.pointerX, scene.pointerY);
      if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) {
        disposeGhost();
        disposeOutline();
        disposeBreakOverlay();
        return;
      }
      const normal = pick.getNormal(true);
      const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);

      if (effectiveMode === "break") {
        disposeGhost();
        disposeOutline();
        if (onGround || !normal) {
          disposeBreakOverlay();
          return;
        }
        const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
        const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
        const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
        showBreakOverlay(bx, by, bz);
        return;
      }
      disposeBreakOverlay();

      const type = selectedTypeRef.current;
      const ordinal = type ? ordinalByNameRef.current.get(type) : undefined;
      if (ordinal == null) {
        disposeGhost();
        disposeOutline();
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
        disposeGhost();
        disposeOutline();
        return;
      }
      if (ty < zone.yMin || ty > zone.yMax || !inZone(tx, tz)) {
        disposeGhost();
        disposeOutline();
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
        placementRotation,
      );
      showGhostAndOutline(tx, ty, tz, ordinal, xOffset, zOffset);
    }

    function onKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) return;
      if (e.code !== "KeyR") return;
      placementRotation = (placementRotation + 1) % 4;
      // Force a rebuild at the last known cursor position so the rotation is visible immediately,
      // instead of waiting for the next mouse move.
      ghostGeoKey = null;
      updateHoverPreview({ shiftKey: e.shiftKey } as PointerEvent);
    }
    window.addEventListener("keydown", onKeyDown);

    const clickObserver = scene.onPointerObservable.add((pointerInfo) => {
      if (pointerInfo.type === B.PointerEventTypes.POINTERDOWN) {
        const evt = pointerInfo.event as PointerEvent;
        downX = lastX = scene.pointerX;
        downY = lastY = scene.pointerY;
        downShift = evt.shiftKey;
        dragMode = evt.metaKey ? "rotate" : evt.altKey ? "pan" : evt.ctrlKey ? "zoom" : "place";
        // Re-pivot the orbit around whatever was under the cursor at the start of the rotation
        // drag, instead of always orbiting around the current (possibly stale, panned-away-from)
        // camera.target. setTarget() recomputes alpha/beta/radius from the camera's current
        // position so the view doesn't jump — only the pivot point changes.
        if (dragMode === "rotate") {
          const pick = scene.pick(scene.pointerX, scene.pointerY);
          if (pick?.hit && pick.pickedPoint) camera.setTarget(pick.pickedPoint);
        }
        return;
      }
      if (pointerInfo.type === B.PointerEventTypes.POINTERMOVE) {
        if (!dragMode || dragMode === "place") {
          updateHoverPreview(pointerInfo.event as PointerEvent);
          return;
        }
        const dx = scene.pointerX - lastX;
        const dy = scene.pointerY - lastY;
        lastX = scene.pointerX;
        lastY = scene.pointerY;
        if (dragMode === "rotate") {
          camera.alpha -= dx * ROTATE_SENSITIVITY;
          camera.beta = Math.min(Math.PI - 0.01, Math.max(0.01, camera.beta - dy * ROTATE_SENSITIVITY));
        } else if (dragMode === "pan") {
          panCamera(dx, dy);
        } else if (dragMode === "zoom") {
          camera.radius = Math.max(camera.lowerRadiusLimit ?? 2, camera.radius + dy * camera.radius * ZOOM_SENSITIVITY);
        }
        return;
      }
      if (pointerInfo.type !== B.PointerEventTypes.POINTERUP) return;
      const wasPlaceDrag = dragMode === "place";
      dragMode = null;
      if (!wasPlaceDrag) return;
      const dx = scene.pointerX - downX;
      const dy = scene.pointerY - downY;
      if (dx * dx + dy * dy > CLICK_MOVE_THRESHOLD * CLICK_MOVE_THRESHOLD) return;
      const pick = scene.pick(scene.pointerX, scene.pointerY);
      if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) return;
      const normal = pick.getNormal(true);
      const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);
      const currentMode = downShift ? "break" : modeRef.current;

      if (currentMode === "break") {
        if (onGround || !normal) return;
        const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
        const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
        const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
        // Resolve the targeted block's own brickSize so the removal hits the exact XZ sub-slot
        // aimed at (e.g. one LEGO_PIECE among several stacked/slotted in the same cell), same
        // idea as the in-game precise break (LocalPlayerController.kt).
        let breakXOffset = 0;
        let breakZOffset = 0;
        if (wasmExports) {
          const targetOrdinal = wasmExports.mcAdminGetBlockOrdinalAt(scene, bx, by, bz);
          const targetDef = window.mc.getBlockDef(targetOrdinal);
          const bs = targetDef?.brickSize ?? [1, 1, 1];
          [breakXOffset, breakZOffset] = computeSlotOffset(
            pick.pickedPoint.x,
            pick.pickedPoint.z,
            bx,
            bz,
            bs[0],
            bs[2],
            0,
          );
        }
        api.instances
          .setBlock(zone.id, {
            x: bx,
            y: by,
            z: bz,
            type: "AIR",
            state: 0,
            xOffset: breakXOffset,
            zOffset: breakZOffset,
          })
          .then((res) => {
            if (!res.ok) return res.text().then((msg) => flashActionError(msg || `Break failed (${res.status})`));
            return reloadChunk(Math.floor(bx / CHUNK_SIZE), Math.floor(bz / CHUNK_SIZE));
          })
          .catch((e) => flashActionError(String(e)));
        return;
      }

      // place mode
      const type = selectedTypeRef.current;
      if (!type) return;
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
        placementRotation,
      );
      api.instances
        .setBlock(zone.id, { x: tx, y: ty, z: tz, type, state: placementRotation, xOffset, zOffset })
        .then((res) => {
          if (!res.ok) return res.text().then((msg) => flashActionError(msg || `Place failed (${res.status})`));
          return reloadChunk(Math.floor(tx / CHUNK_SIZE), Math.floor(tz / CHUNK_SIZE));
        })
        .catch((e) => flashActionError(String(e)));
    });

    engine.runRenderLoop(() => scene.render());
    const onResize = () => engine.resize();
    window.addEventListener("resize", onResize);

    return () => {
      disposed = true;
      camera.onViewMatrixChangedObservable.remove(viewMatrixObserver);
      scene.onPointerObservable.remove(clickObserver);
      window.removeEventListener("resize", onResize);
      window.removeEventListener("keydown", onKeyDown);
      disposeGhost();
      disposeOutline();
      disposeBreakOverlay();
      engine.dispose();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- zone.id identity is the intended re-mount trigger
  }, [zone.id, blockDefsReady]);

  return (
    <div className="flex-1 flex overflow-hidden">
      <div className="flex-[4] relative">
        <div className="absolute top-2 left-1/2 -translate-x-1/2 flex gap-1.5 pointer-events-none z-10">
          {(
            [
              { key: "place", label: modKeys.shift || mode === "break" ? "Break" : "Place", hint: "⇧" },
              { key: "zoom", label: "Zoom", hint: "⌃" },
              { key: "pan", label: "Pan", hint: "⌥" },
              { key: "rotate", label: "Rotate", hint: "⌘" },
            ] as const
          ).map((m) => (
            <div
              key={m.key}
              className={`px-2 py-1 rounded text-[10px] font-medium transition-colors ${
                activeDragMode === m.key ? "bg-[#3C50E0] text-white" : "bg-black/50 text-[#8A99AF]"
              }`}
            >
              {m.hint && <span className="mr-1 font-mono">{m.hint}</span>}
              {m.label}
            </div>
          ))}
        </div>
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
      <aside className="flex-[2] shrink-0 border-l border-[#2E3A4E] overflow-y-auto flex flex-col">
        <div className="shrink-0 border-b border-[#2E3A4E] flex flex-col items-center justify-center gap-1 py-3">
          {selectedType && blockDefsReady && getOrdinal(selectedType) !== null ? (
            <Block3DPreview ordinal={getOrdinal(selectedType)!} size={96} />
          ) : (
            <div className="h-24 w-24 flex items-center justify-center text-[#8A99AF] text-xs">
              {selectedType ? "Loading…" : "No block selected"}
            </div>
          )}
          {selectedType && <span className="text-white text-xs font-medium">{selectedType.replace(/_/g, " ")}</span>}
        </div>
        <div className="shrink-0 border-b border-[#2E3A4E] p-2">
          <div className="flex flex-wrap gap-1 justify-center">
            {shortcutBar.slots.map((slotBlock, idx) => {
              const isBreakSlot = idx === 0;
              const isSelected = shortcutBar.selectedSlot === idx;
              const isDropTarget = shortcutBar.dragOver === idx;
              const ordinal = !isBreakSlot && slotBlock ? getOrdinal(slotBlock) : null;
              return (
                <div
                  key={idx}
                  onClick={() => shortcutBar.selectSlot(idx)}
                  onDragOver={(e) => shortcutBar.handleDragOver(e, idx)}
                  onDragLeave={shortcutBar.handleDragLeave}
                  onDrop={(e) => shortcutBar.handleDrop(e, idx)}
                  onContextMenu={(e) => shortcutBar.handleContextMenu(e, idx)}
                  title={isBreakSlot ? "Break" : (slotBlock ?? undefined)}
                  className={`relative w-9 h-9 shrink-0 flex items-center justify-center rounded border-2 cursor-pointer transition-colors ${
                    isDropTarget ? "bg-white/20" : "bg-black/30"
                  } ${isSelected ? "border-[#3C50E0]" : "border-transparent hover:border-white/20"}`}
                >
                  <div className="absolute top-0 left-0.5 text-[#8A99AF] font-mono text-[7px]">
                    {idx === 9 ? "0" : String(idx + 1)}
                  </div>
                  {isBreakSlot ? (
                    <div className="text-sm">⛏</div>
                  ) : slotBlock && blockDefsReady && ordinal !== null ? (
                    <CssBlockCube ordinal={ordinal} size={16} />
                  ) : null}
                </div>
              );
            })}
          </div>
          {shortcutBar.pageCount > 1 && (
            <div className="flex gap-1 justify-center mt-1.5">
              {Array.from({ length: shortcutBar.pageCount }, (_, p) => (
                <button
                  key={p}
                  onClick={() => shortcutBar.goToPage(p)}
                  className={`w-1.5 h-1.5 rounded-full ${p === shortcutBar.currentPage ? "bg-[#3C50E0]" : "bg-white/25"}`}
                />
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
        <div ref={paletteRef} className="flex flex-wrap gap-1 p-2 overflow-y-auto content-start">
          {blockDefs
            .filter((b) => b.name.toLowerCase().includes(search.toLowerCase()))
            .map((b) => (
              <button
                key={b.name}
                draggable
                onDragStart={(e) => e.dataTransfer.setData("text/plain", b.name)}
                onClick={() => setSelectedType(b.name)}
                onMouseEnter={(e) => {
                  // Tooltip defaults to below the item; flip it above only when there isn't room
                  // below inside the scroll container (i.e. hovering the last row) so it doesn't
                  // get clipped by the container's overflow-y-auto.
                  const btnRect = e.currentTarget.getBoundingClientRect();
                  const containerRect = paletteRef.current?.getBoundingClientRect();
                  const spaceBelow = containerRect ? containerRect.bottom - btnRect.bottom : Infinity;
                  setTooltipAbove(spaceBelow < 40);
                  setHoveredBlockName(b.name);
                }}
                onMouseLeave={() => setHoveredBlockName(null)}
                className={`relative flex flex-col items-center gap-0.5 rounded border-2 p-1 w-14 ${
                  selectedType === b.name ? "border-[#3C50E0]" : "border-transparent hover:border-white/20"
                }`}
              >
                {blockDefsReady && getOrdinal(b.name) !== null ? (
                  <CssBlockCube ordinal={getOrdinal(b.name)!} size={20} />
                ) : (
                  <div className="w-5 h-5 rounded" style={{ background: rgbToHex(b.minimapColor) }} />
                )}
                <span className="text-[9px] leading-tight text-[#8A99AF] text-center truncate w-full">
                  {b.name.replace(/_/g, " ")}
                </span>
                {hoveredBlockName === b.name && (
                  <div
                    className={`pointer-events-none absolute z-30 left-1/2 -translate-x-1/2 flex flex-col gap-0.5 whitespace-nowrap rounded border border-[#2E3A4E] bg-[#0B1220] px-2 py-1 text-[10px] shadow-lg ${
                      tooltipAbove ? "bottom-full mb-1" : "top-full mt-1"
                    }`}
                  >
                    <span className="font-semibold text-white">{b.name.replace(/_/g, " ")}</span>
                    <span className="text-[#8A99AF]">
                      {b.hardness < 0 ? "Unbreakable" : `Hardness ${b.hardness}`} · {b.solid ? "Solid" : "Non-solid"}
                      {b.transparent ? " · Transparent" : ""}
                      {b.liquid ? " · Liquid" : ""}
                    </span>
                  </div>
                )}
              </button>
            ))}
        </div>
      </aside>
    </div>
  );
}
