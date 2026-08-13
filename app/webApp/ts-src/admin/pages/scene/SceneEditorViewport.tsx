import { useEffect, useMemo, useRef, useState } from "react";
import { api, type SceneDto } from "../../api";
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
import { setupOrbitPointerController } from "../shared/voxelEditor/orbitPointerController";
import { createOverlayController } from "../shared/voxelEditor/overlayController";
import { useBlockRegistry } from "../shared/voxelEditor/useBlockRegistry";
import { useModifierDragMode } from "../shared/voxelEditor/useModifierDragMode";
import { useActionError } from "../shared/voxelEditor/useActionError";
import { makeUndoRedoController, type UndoEntryBase } from "../shared/voxelEditor/undoRedoStack";
import { VoxelEditorSidebar } from "../shared/voxelEditor/VoxelEditorSidebar";

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
  const [mode, setMode] = useState<"place" | "break">("place");
  const [loadError, setLoadError] = useState<string | null>(null);
  const { actionError, flashActionError } = useActionError();
  const [search, setSearch] = useState("");
  const modeRef = useRef(mode);
  const selectedTypeRef = useRef(selectedType);
  const ordinalByNameRef = useRef(ordinalByName);
  const getPreview = useBlockPreviews();
  const undoStackRef = useRef<UndoEntry[]>([]);
  const redoStackRef = useRef<UndoEntry[]>([]);

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

  useEffect(() => {
    startPreloading();
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
  useEffect(() => {
    selectedColorIndexRef.current = selectedColorIndex;
  }, [selectedColorIndex]);

  function selectBlockType(name: string) {
    setMode("place");
    setSelectedType(name);
    setSelectedColorIndex(0);
  }

  const blockDefsReady = useBlockDefsReady();
  const previewsReady = useAllBlockPreviewsReady();
  const previewProgress = useBlockPreviewProgress();

  const shortcutBar = useAdminShortcutBar({
    onSelectBreak: () => setMode("break"),
    onSelectBlock: (blockName) => selectBlockType(blockName),
  });

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
    // One grid-line square per texture tile, repeated width x depth times so each cell lines up
    // with exactly one voxel — a plain wireframe-style reference grid rather than a filled checker.
    const gridPx = 32;
    const gridTex = new B.DynamicTexture("sceneGroundGrid", { width: gridPx, height: gridPx }, babylonScene, false);
    const gridCtx = gridTex.getContext() as CanvasRenderingContext2D;
    gridCtx.fillStyle = "#26262e";
    gridCtx.fillRect(0, 0, gridPx, gridPx);
    gridCtx.strokeStyle = "#5a5a66";
    gridCtx.lineWidth = 1;
    gridCtx.strokeRect(0.5, 0.5, gridPx - 1, gridPx - 1);
    gridTex.update();
    gridTex.wrapU = B.Texture.WRAP_ADDRESSMODE;
    gridTex.wrapV = B.Texture.WRAP_ADDRESSMODE;
    gridTex.uScale = scene.width;
    gridTex.vScale = scene.depth;
    groundMat.diffuseTexture = gridTex;
    groundMat.specularColor = B.Color3.Black();
    groundMat.alpha = 0.35;
    groundMat.freeze();
    const ground = B.MeshBuilder.CreateGround("floor", { width: scene.width, height: scene.depth }, babylonScene);
    ground.position = new B.Vector3(scene.width / 2, 0, scene.depth / 2);
    ground.material = groundMat;
    ground.freezeWorldMatrix();
    const groundMeshes = new Set<InstanceType<typeof BABYLON.Mesh>>([ground]);

    const overlayMeshes = new Set<InstanceType<typeof BABYLON.Mesh>>();
    overlayMeshesRef.current = overlayMeshes;
    const overlay = createOverlayController(B, babylonScene, overlayMeshes);
    let disposed = false;

    // Whole-volume load: fetch the raw binary buffer once and mesh it in one call — no
    // chunk-streaming equivalent, since a Scene is always small enough to load in full.
    async function loadScene() {
      const exports = await window.webApp!;
      const buf = await api.scenes.getBlocksRaw(scene.id);
      const { width, height, depth, blocks, states } = parseSceneRaw(buf);
      exports.mcSceneLoad(babylonScene, width, height, depth, blocks, states);
      applyClipPlanes(B!, babylonScene, clipPlanesRef.current, clipBounds, overlayMeshes, clipMeshesRef.current);
      for (const m of babylonScene.meshes) {
        const mesh = m as InstanceType<typeof BABYLON.Mesh>;
        if (!groundMeshes.has(mesh) && !overlayMeshes.has(mesh)) mesh.isPickable = true;
      }
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

    // Applies an edit both server-side (persisted) and locally via the WASM mesher (instant
    // visual feedback) — mirrors InstanceEditorViewport's applyEntry, minus the chunk reload
    // (mcSceneSetBlock re-meshes the whole buffer synchronously, no fetch round-trip needed).
    function applyEntry(entry: UndoEntry, onSuccess: () => void, failLabel: string) {
      const ordinal = ordinalByNameRef.current.get(entry.type) ?? (entry.type === "AIR" ? 0 : null);
      api.scenes
        .setBlock(scene.id, { x: entry.x, y: entry.y, z: entry.z, type: entry.type, state: entry.state })
        .then((res) => {
          if (!res.ok) return res.text().then((msg) => flashActionError(msg || `${failLabel} failed (${res.status})`));
          onSuccess();
          if (ordinal != null) exportsSetBlock(entry.x, entry.y, entry.z, ordinal, entry.state);
        })
        .catch((e) => flashActionError(String(e)));
    }

    const { pushUndo, performUndo, performRedo } = makeUndoRedoController<UndoEntry>(
      undoStackRef,
      redoStackRef,
      captureBlock,
      applyEntry,
    );

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
      onClick: ({ pick, normal, mode: currentMode }) => {
        const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);

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
          api.scenes
            .setBlock(scene.id, { x: bx, y: by, z: bz, type: "AIR", state: 0 })
            .then((res) => {
              if (!res.ok) return res.text().then((msg) => flashActionError(msg || `Break failed (${res.status})`));
              pushUndo({ x: bx, y: by, z: bz, type: prevType, state: prevState });
              exportsSetBlock(bx, by, bz, 0, 0);
            })
            .catch((e) => flashActionError(String(e)));
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
        api.scenes
          .setBlock(scene.id, { x: tx, y: ty, z: tz, type, state })
          .then((res) => {
            if (!res.ok) return res.text().then((msg) => flashActionError(msg || `Place failed (${res.status})`));
            pushUndo({ x: tx, y: ty, z: tz, type: prevType, state: prevState });
            exportsSetBlock(tx, ty, tz, ordinal, state);
          })
          .catch((e) => flashActionError(String(e)));
      },
    });

    engine.runRenderLoop(() => babylonScene.render());
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
      window.webApp?.then((exports) => exports.mcSceneDispose()).catch(() => {});
      engine.dispose();
      sceneRef.current = null;
      overlayMeshesRef.current = null;
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
