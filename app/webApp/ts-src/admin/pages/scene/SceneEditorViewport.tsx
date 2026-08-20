import { useEffect, useMemo, useRef } from "react";
import { putApiAdminScenesByIdLayout } from "../../../generated/api/requests";
import type { SceneDto, SceneBlockDto } from "../../apiTypes";
import { useVoxelEditorViewport } from "../shared/voxelEditor/useVoxelEditorViewport";
import { createVoxelEditorSceneController } from "../shared/voxelEditor/voxelEditorSceneController";
import type { VoxelVolumeAdapter } from "../shared/voxelEditor/voxelVolumeAdapter";
import { createAxesGizmo } from "../shared/voxelEditor/axesGizmo";
import { applyClipPlanes } from "../shared/voxelEditor/clipAxis";
import { saveCameraState } from "../shared/voxelEditor/cameraStorage";
import { createOrbitCamera, setupBasicLighting } from "../shared/voxelEditor/orbitCamera";
import { setupOrbitPointerController } from "../shared/voxelEditor/orbitPointerController";
import { createOverlayController } from "../shared/voxelEditor/overlayController";
import { createSelectionGizmo } from "../shared/voxelEditor/selectionGizmo";
import { createPasteGizmo, type PasteOrigin } from "../shared/voxelEditor/pasteGizmo";
import { createRailTestCart } from "../shared/voxelEditor/railTestCart";
import { createRailSwitchMarkers, type RailJunction } from "../shared/voxelEditor/railSwitchMarkers";
import { VoxelEditorSidebar } from "../shared/voxelEditor/VoxelEditorSidebar";
import { ViewportCameraHud } from "../shared/voxelEditor/ViewportCameraHud";
import { HoveredVoxelNameHud } from "../shared/voxelEditor/HoveredVoxelNameHud";
import { TestRailButton } from "../shared/voxelEditor/TestRailButton";
import { connectEditSocket, type BlockEditSocket } from "../shared/voxelEditor/editSocket";
import { usedSlotAt } from "../shared/voxelEditor/fractionalPlacement";

const cameraStorageKey = (sceneId: string) => `sceneEditorCamera:${sceneId}`;

// Parses GET /api/admin/scenes/{id}/blocks/raw's binary layout: 3 big-endian Int32 header fields
// (width, height, depth) followed by two equal-length byte arrays (blocks, then states), flat
// index = x*height*depth + y*depth + z — see AdminScenePreview.kt's mcSceneLoad.
function parseSceneRaw(buf: ArrayBuffer): {
  width: number;
  height: number;
  depth: number;
  blocks: Uint8Array;
  states: Uint8Array;
  extraStates: Uint8Array;
} {
  const view = new DataView(buf);
  const width = view.getInt32(0, false);
  const height = view.getInt32(4, false);
  const depth = view.getInt32(8, false);
  const cellCount = width * height * depth;
  const blocksStart = 12;
  const statesStart = blocksStart + cellCount;
  const extraStatesStart = statesStart + cellCount;
  const blocks = new Uint8Array(buf, blocksStart, cellCount);
  const states = new Uint8Array(buf, statesStart, cellCount);
  const extraStates = new Uint8Array(buf, extraStatesStart, cellCount);
  return { width, height, depth, blocks, states, extraStates };
}

export function SceneEditorViewport({ scene }: { scene: SceneDto }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const clipBounds = useMemo(
    () => ({
      x: [0, scene.width] as const,
      y: [0, scene.height] as const,
      z: [0, scene.depth] as const,
    }),
    [scene.width, scene.height, scene.depth],
  );

  // Not persisted server-side — the Scene HTTP contract has no layout/clipPlanes endpoint (unlike
  // InstanceZoneDto.clipPlanes), so this resets on remount like the geometry itself.
  const initialClipPlanes = useMemo(
    () => ({
      x: { enabled: false, flipped: false, pos: (clipBounds.x[0] + clipBounds.x[1]) / 2 },
      y: { enabled: false, flipped: false, pos: (clipBounds.y[0] + clipBounds.y[1]) / 2 },
      z: { enabled: false, flipped: false, pos: (clipBounds.z[0] + clipBounds.z[1]) / 2 },
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only the seed matters, this feeds a useState initializer
    [],
  );

  const v = useVoxelEditorViewport({
    volumeId: scene.id,
    clipBounds,
    initialClipPlanes,
    initialShortcutBarPages: scene.shortcutBarPages,
  });

  const inBounds = (x: number, y: number, z: number) =>
    x >= 0 && x < scene.width && y >= 0 && y < scene.height && z >= 0 && z < scene.depth;

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
        body: { shortcutBarPages: v.shortcutBar.pages as string[][] },
        throwOnError: true,
      }).catch(console.error);
    }, 500);
    return () => clearTimeout(timeout);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- scene.id intentionally excluded, only its identity matters via the closure
  }, [v.shortcutBar.pages]);

  useEffect(() => {
    v.setLoadError(null);
    if (!window.webApp) {
      v.setLoadError("WASM module not loaded (webApp.js missing).");
      return;
    }
    // SceneMesher silently no-ops until block defs are ready (mirrors ChunkManager's behavior).
    if (!v.blockDefsReady) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const babylonScene = new B.Scene(engine);
    babylonScene.clearColor = new B.Color4(0.06, 0.06, 0.08, 1);
    v.setScene(babylonScene);
    v.setClipMeshes({});

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
    v.setOverlayMeshes(overlayMeshes);
    const overlay = createOverlayController(B, babylonScene, overlayMeshes);
    const selectionGizmo = createSelectionGizmo(
      B,
      babylonScene,
      clipBounds,
      (box) => v.setSelection(box),
      v.flashActionError,
    );
    selectionGizmo.setShape(v.selectionShapeRef.current);
    selectionGizmo.setSnap(v.selectionSnapRef.current);
    v.setSelectionGizmoInstance(selectionGizmo);

    // The paste gizmo's own drag callback needs the controller's renderPastePreview, but the
    // controller itself needs `pasteGizmo` already built (to wire pasteActions.start/move/cancel)
    // — this indirection breaks that circular dependency, rebound to the real handler right after
    // the controller is created below.
    let onPasteGizmoDrag: (origin: PasteOrigin) => void = () => {};
    const pasteGizmo = createPasteGizmo(B, babylonScene, (origin) => onPasteGizmoDrag(origin));
    v.setPasteGizmoInstance(pasteGizmo);

    // mcScene* rail-test exports operate on the mesher singleton, no scene param — see
    // AdminScenePreview.kt's mcSceneRailTest* trio.
    const railTestCart = createRailTestCart(
      B,
      babylonScene,
      (wx, wy, wz) => (wasmExports ? wasmExports.mcSceneRailTestStart(wx, wy, wz) : 0),
      (deltaSeconds) => (wasmExports ? wasmExports.mcSceneRailTestTick(deltaSeconds) : ""),
      () => wasmExports?.mcSceneRailTestStop(),
    );
    v.setRailTestCartInstance(railTestCart);

    const railSwitchMarkers = createRailSwitchMarkers(B, babylonScene);
    v.setRailSwitchMarkersInstance(railSwitchMarkers);
    function refreshJunctions() {
      if (!wasmExports) return;
      const csv = wasmExports.mcSceneListJunctions();
      const junctions: RailJunction[] = csv
        ? csv.split(";").map((row) => {
            const [wx, wy, wz, branchCount, currentBranch] = row.split(",").map(Number);
            return { wx, wy, wz, branchCount, currentBranch };
          })
        : [];
      railSwitchMarkers.update(junctions);
    }
    v.setRefreshJunctionsImpl(refreshJunctions);

    let disposed = false;

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

    // Refetches the scene's fractional/lego entity list (GET .../entities) and re-meshes — the
    // server (BlockPlacer.placeAt/BlockBreaker.removeAt via ScenePlacementTarget) is the only
    // source of truth for slot/offset resolution, so this is called after every edit that could
    // touch entities, mirroring InstanceEditorViewport's reloadChunk (which re-fetches the whole
    // chunk, entities included, from the server after every edit).
    async function reloadEntities() {
      if (!wasmExports || disposed) return;
      const json = await fetch(`/api/admin/scenes/${encodeURIComponent(scene.id)}/entities`).then((r) => r.text());
      if (disposed) return;
      wasmExports.mcSceneLoadEntities(babylonScene, json);
      // mcSceneLoadEntities re-meshes the buffer, and chunkBuilder.ts defaults every new mesh to
      // isPickable=false — without re-enabling it here, the whole terrain goes unpickable after
      // every load/edit (this runs on scene load and after every edit). Mirrors exportsSetBlock's
      // re-enable loop.
      for (const m of babylonScene.meshes) {
        const mesh = m as InstanceType<typeof BABYLON.Mesh>;
        if (!groundMeshes.has(mesh) && !overlayMeshes.has(mesh)) mesh.isPickable = true;
      }
    }

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
      const { width, height, depth, blocks, states, extraStates } = parseSceneRaw(buf);
      exports.mcSceneLoad(babylonScene, width, height, depth, blocks, states, extraStates);
      applyClipPlanes(B!, babylonScene, v.clipPlanesRef.current, clipBounds, overlayMeshes, v.clipMeshesRef.current);
      for (const m of babylonScene.meshes) {
        const mesh = m as InstanceType<typeof BABYLON.Mesh>;
        if (!groundMeshes.has(mesh) && !overlayMeshes.has(mesh)) mesh.isPickable = true;
      }
      if (disposed) return;
      // Fractional/lego entities (see Scene.entities) aren't carried by the blocks/raw binary
      // blob above — loaded separately so LEGO_PIECE etc. render with their correct sub-voxel
      // geometry from the start, not just as a plain cube.
      await reloadEntities();
      if (disposed) return;
      editSocket = connectEditSocket<SceneBlockDto>(
        "scenes",
        scene.id,
        (edit) => {
          const ordinal = v.ordinalByNameRef.current.get(edit.type) ?? (edit.type === "AIR" ? 0 : null);
          if (ordinal != null) exportsSetBlock(edit.x, edit.y, edit.z, ordinal, edit.state);
          reloadEntities();
        },
        (message) => v.flashActionError(message),
        // A batch broadcast from another admin tab (e.g. its own Fill/Shell/Cut). No batch variant
        // of mcSceneSetBlock exists — each voxel still remeshes the whole buffer individually, a
        // known cost bounded by MAX_SELECTION_OP_VOXELS.
        (edits) => {
          for (const edit of edits) {
            const ordinal = v.ordinalByNameRef.current.get(edit.type) ?? (edit.type === "AIR" ? 0 : null);
            if (ordinal != null) exportsSetBlock(edit.x, edit.y, edit.z, ordinal, edit.state);
          }
          reloadEntities();
        },
      );
    }

    loadScene().catch((e) => {
      console.error("Failed to load scene", e);
      if (!disposed) v.setLoadError(String(e));
    });

    // Bridges this editor's buffer/wasm specifics to the shared handler logic in
    // voxelEditorSceneController.ts — see voxelVolumeAdapter.ts for what each method means.
    const adapter: VoxelVolumeAdapter<SceneBlockDto> = {
      isReady: () => !!wasmExports,
      inBounds,
      getBlockOrdinalAt: (x, y, z) => (wasmExports ? wasmExports.mcSceneGetBlockOrdinalAt(x, y, z) : 0),
      getBlockStateAt: (x, y, z) => (wasmExports ? wasmExports.mcSceneGetBlockStateAt(x, y, z) : 0),
      getUsedXZOffsetAt: (x, y, z) =>
        wasmExports ? usedSlotAt((wx, wy, wz) => wasmExports!.mcSceneGetUsedXZOffsetAt(wx, wy, wz), x, y, z) : null,
      getEditSocket: () => editSocket,
      applyLocal: (edit) => {
        const ordinal = v.ordinalByNameRef.current.get(edit.type) ?? (edit.type === "AIR" ? 0 : null);
        if (ordinal != null) exportsSetBlock(edit.x, edit.y, edit.z, ordinal, edit.state);
      },
      afterEdit: () => {
        reloadEntities();
      },
      afterRailSwitchToggle: (junction) => {
        wasmExports?.mcSceneSetExtraState(
          junction.wx,
          junction.wy,
          junction.wz,
          (junction.currentBranch + 1) % junction.branchCount,
        );
        refreshJunctions();
      },
      railPickInBounds: inBounds,
    };

    const controller = createVoxelEditorSceneController({
      B,
      scene: babylonScene,
      overlay,
      groundMeshes,
      selectionGizmo,
      pasteGizmo,
      railSwitchMarkers,
      clipBounds,
      adapter,
      makeEdit: (fields) => fields as SceneBlockDto,
      selectionRef: v.selectionRef,
      selectionShapeRef: v.selectionShapeRef,
      patternBlocksRef: v.patternBlocksRef,
      clipboardRef: v.clipboardRef,
      pasteOriginRef: v.pasteOriginRef,
      pasteTransformRef: v.pasteTransformRef,
      isPastingRef: v.isPastingRef,
      ordinalByNameRef: v.ordinalByNameRef,
      nameByOrdinalRef: v.nameByOrdinalRef,
      selectedTypeRef: v.selectedTypeRef,
      selectedColorIndexRef: v.selectedColorIndexRef,
      modeRef: v.modeRef,
      testRailStateRef: v.testRailStateRef,
      railTestCartRef: v.railTestCartRef,
      undoStackRef: v.undoStackRef,
      redoStackRef: v.redoStackRef,
      pastePreviewMeshesRef: v.pastePreviewMeshesRef,
      setSelection: v.setSelection,
      setPasteOrigin: v.setPasteOrigin,
      setIsPasting: v.setIsPasting,
      setPasteTransform: v.setPasteTransform,
      setClipboardCount: v.setClipboardCount,
      setTestRailState: v.setTestRailState,
      setHoveredVoxelName: v.setHoveredVoxelName,
      flashActionError: v.flashActionError,
      shortcutBarRef: v.shortcutBarRef,
      selectBlockType: v.selectBlockType,
      toggleTestRail: v.toggleTestRail,
    });
    v.setPasteActionsImpl(controller.pasteActions);
    v.setRunSelectionOpImpl(controller.runSelectionOp);
    onPasteGizmoDrag = (origin) => {
      v.setPasteOriginRefValue(origin);
      v.setPasteOrigin(origin);
      controller.renderPastePreview(origin);
    };

    window.addEventListener("keydown", controller.onKeyDown);

    const disposePointerController = setupOrbitPointerController({
      B,
      scene: babylonScene,
      camera,
      canvas,
      getMode: () => v.modeRef.current,
      onHoverMove: controller.updateHoverPreview,
      onCtrlClick: controller.onCtrlClick,
      onClick: controller.onClick,
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
      window.removeEventListener("keydown", controller.onKeyDown);
      overlay.disposeAll();
      selectionGizmo.dispose();
      controller.disposePastePreview();
      pasteGizmo.dispose();
      railTestCart.dispose();
      v.setRailTestCartInstance(null);
      railSwitchMarkers.dispose();
      v.setRailSwitchMarkersInstance(null);
      editSocket?.close();
      window.webApp?.then((exports) => exports.mcSceneDispose()).catch(() => {});
      axesGizmo.dispose();
      engine.dispose();
      v.setScene(null);
      v.setOverlayMeshes(null);
      v.setSelectionGizmoInstance(null);
      v.setPasteGizmoInstance(null);
      v.setClipMeshes({});
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- scene.id identity is the intended re-mount trigger
  }, [scene.id, v.blockDefsReady]);

  return (
    <div className="flex-1 flex overflow-hidden">
      <div className="flex-[4] relative">
        <ViewportCameraHud activeDragMode={v.activeDragMode} />
        <HoveredVoxelNameHud name={v.hoveredVoxelName} />
        <TestRailButton testRailState={v.testRailState} onToggle={v.toggleTestRail} />
        {v.actionError && (
          <div className="absolute bottom-2 left-1/2 -translate-x-1/2 max-w-[80%] px-3 py-1.5 rounded bg-red-900/90 text-red-100 text-xs z-10 pointer-events-none">
            {v.actionError}
          </div>
        )}
        {v.loadError ? (
          <div className="w-full h-full flex items-center justify-center text-red-400 text-sm p-6 text-center">
            {v.loadError}
          </div>
        ) : (
          <canvas ref={canvasRef} className="w-full h-full block" />
        )}
      </div>
      <VoxelEditorSidebar
        modKeys={v.modKeys}
        mode={v.mode}
        onToggleSelect={v.toggleSelectMode}
        selectionShape={v.selectionShape}
        onSelectShape={v.setSelectionShape}
        selectionSnap={v.selectionSnap}
        onSelectSnap={v.setSelectionSnap}
        hasSelection={v.selection !== null}
        selection={v.selection}
        onSelectionFieldChange={v.setSelectionField}
        patternBlocks={v.patternBlocks}
        activePatternSlot={v.activePatternSlot}
        onSelectPatternSlot={v.setActivePatternSlot}
        onClearPatternSlot={v.clearPatternSlot}
        onFill={() => v.runSelectionOp("fill")}
        onShell={() => v.runSelectionOp("shell")}
        onCut={() => v.runSelectionOp("cut")}
        onCopy={() => v.runSelectionOp("copy")}
        clipboardCount={v.clipboardCount}
        onPaste={v.startPaste}
        isPasting={v.isPasting}
        onConfirmPaste={v.confirmPaste}
        onCancelPaste={v.cancelPaste}
        onRotatePaste={v.rotatePaste}
        onFlipPaste={v.flipPaste}
        pasteTransform={v.pasteTransform}
        pasteOrigin={v.pasteOrigin}
        onMovePasteOrigin={v.movePasteOrigin}
        savedSelections={v.savedSelections}
        onAddSelectionToMemory={v.addSelectionToMemory}
        onSelectSavedSelection={v.selectSavedSelection}
        onRemoveSavedSelection={v.removeSavedSelection}
        resizeStep={v.resizeStep}
        onSelectResizeStep={v.setResizeStep}
        onExpandSelection={v.expandSelection}
        onContractSelection={v.contractSelection}
        activeDragMode={v.activeDragMode}
        selectedType={v.selectedType}
        blockDefsReady={v.blockDefsReady}
        previewsReady={v.previewsReady}
        previewProgress={v.previewProgress}
        getOrdinal={v.getOrdinal}
        blockDefs={v.blockDefs}
        plainColors={v.plainColors}
        selectedColorIndex={v.selectedColorIndex}
        setSelectedColorIndex={v.setSelectedColorIndex}
        clipPlanes={v.clipPlanes}
        clipBounds={clipBounds}
        setClipPlanes={v.setClipPlanes}
        shortcutBar={v.shortcutBar}
        hoveredShortcutSlot={v.hoveredShortcutSlot}
        setHoveredShortcutSlot={v.setHoveredShortcutSlot}
        getPreview={v.getPreview}
        search={v.search}
        setSearch={v.setSearch}
        hoveredBlockName={v.hoveredBlockName}
        hoveredRect={v.hoveredRect}
        setHoveredBlockName={v.setHoveredBlockName}
        setHoveredRect={v.setHoveredRect}
        selectBlockType={v.selectBlockType}
      />
    </div>
  );
}
