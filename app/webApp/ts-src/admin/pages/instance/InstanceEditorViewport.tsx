import { useEffect, useMemo, useRef } from "react";
import { putApiAdminInstancesByIdLayout } from "../../../generated/api/requests";
import type { InstanceZoneDto, InstanceBlockDto } from "../../apiTypes";
import { useVoxelEditorViewport } from "../shared/voxelEditor/useVoxelEditorViewport";
import { createVoxelEditorSceneController } from "../shared/voxelEditor/voxelEditorSceneController";
import type { VoxelVolumeAdapter } from "../shared/voxelEditor/voxelVolumeAdapter";
import { applyClipPlanes } from "../shared/voxelEditor/clipAxis";
import { saveCameraState } from "../shared/voxelEditor/cameraStorage";
import { createOrbitCamera, setupBasicLighting } from "../shared/voxelEditor/orbitCamera";
import { createAxesGizmo } from "../shared/voxelEditor/axesGizmo";
import { setupOrbitPointerController } from "../shared/voxelEditor/orbitPointerController";
import { createOverlayController } from "../shared/voxelEditor/overlayController";
import { createSelectionGizmo } from "../shared/voxelEditor/selectionGizmo";
import { createPasteGizmo } from "../shared/voxelEditor/pasteGizmo";
import { createRailTestCart } from "../shared/voxelEditor/railTestCart";
import { createRailSwitchMarkers, type RailJunction } from "../shared/voxelEditor/railSwitchMarkers";
import { VoxelEditorSidebar } from "../shared/voxelEditor/VoxelEditorSidebar";
import { ViewportCameraHud } from "../shared/voxelEditor/ViewportCameraHud";
import { HoveredVoxelNameHud } from "../shared/voxelEditor/HoveredVoxelNameHud";
import { TestRailButton } from "../shared/voxelEditor/TestRailButton";
import { connectEditSocket } from "../shared/voxelEditor/editSocket";
import { usedSlotAt } from "../shared/voxelEditor/fractionalPlacement";
import type { PasteOrigin } from "../shared/voxelEditor/pasteGizmo";

const CHUNK_SIZE = 16;
// How far (in chunks) around the camera target to keep block geometry loaded. Scales with
// zoom (camera.radius) so zooming out pulls in more chunks, capped so a huge zone can't force
// thousands of simultaneous chunk fetches.
const MAX_VIEW_RADIUS_CHUNKS = 8;
// Hysteresis margin: a chunk is only unloaded once it's this far past the load radius, so
// camera jitter right at the boundary doesn't thrash fetch/hide every frame.
const VIEW_RADIUS_UNLOAD_MARGIN = 1;

const cameraStorageKey = (zoneId: string) => `instanceEditorCamera:${zoneId}`;

export function InstanceEditorViewport({ zone }: { zone: InstanceZoneDto }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const paletteRef = useRef<HTMLDivElement>(null);

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

  const initialClipPlanes = useMemo(() => {
    const saved = zone.clipPlanes;
    return {
      x: saved?.x ?? { enabled: false, flipped: false, pos: (clipBounds.x[0] + clipBounds.x[1]) / 2 },
      y: saved?.y ?? { enabled: false, flipped: false, pos: (clipBounds.y[0] + clipBounds.y[1]) / 2 },
      z: saved?.z ?? { enabled: false, flipped: false, pos: (clipBounds.z[0] + clipBounds.z[1]) / 2 },
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only the seed matters, this feeds a useState initializer
  }, []);

  const v = useVoxelEditorViewport({
    volumeId: zone.id,
    clipBounds,
    initialClipPlanes,
    initialShortcutBarPages: zone.shortcutBarPages,
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
        body: { clipPlanes: v.clipPlanes, shortcutBarPages: v.shortcutBar.pages as string[][] },
        throwOnError: true,
      }).catch(console.error);
    }, 500);
    return () => clearTimeout(timeout);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- zone.id intentionally excluded, only its identity matters via the closure
  }, [v.clipPlanes, v.shortcutBar.pages]);

  useEffect(() => {
    v.setLoadError(null);
    if (zone.chunks.length === 0) {
      v.setLoadError("Zone has no chunks.");
      return;
    }
    if (!window.webApp) {
      v.setLoadError("WASM module not loaded (webApp.js missing).");
      return;
    }
    // ChunkManager.renderChunk() silently no-ops (enqueues without draining) until block defs
    // are ready — starting chunk loads before that would appear to do nothing at all.
    if (!v.blockDefsReady) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.06, 0.06, 0.08, 1);
    v.setScene(scene);
    v.setClipMeshes({});

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
    v.setOverlayMeshes(overlayMeshes);
    const overlay = createOverlayController(B, scene, overlayMeshes);
    const selectionGizmo = createSelectionGizmo(B, scene, clipBounds, (box) => v.setSelection(box), v.flashActionError);
    selectionGizmo.setShape(v.selectionShapeRef.current);
    selectionGizmo.setSnap(v.selectionSnapRef.current);
    v.setSelectionGizmoInstance(selectionGizmo);

    // The paste gizmo's own drag callback needs the controller's renderPastePreview, but the
    // controller itself needs `pasteGizmo` already built (to wire pasteActions.start/move/cancel)
    // — this indirection breaks that circular dependency, rebound to the real handler right after
    // the controller is created below.
    let onPasteGizmoDrag: (origin: PasteOrigin) => void = () => {};
    const pasteGizmo = createPasteGizmo(B, scene, (origin) => onPasteGizmoDrag(origin));
    v.setPasteGizmoInstance(pasteGizmo);

    const railTestCart = createRailTestCart(
      B,
      scene,
      (wx, wy, wz) => (wasmExports ? wasmExports.mcAdminRailTestStart(scene, wx, wy, wz) : 0),
      (deltaSeconds) => (wasmExports ? wasmExports.mcAdminRailTestTick(scene, deltaSeconds) : ""),
      () => wasmExports?.mcAdminRailTestStop(),
    );
    v.setRailTestCartInstance(railTestCart);

    const railSwitchMarkers = createRailSwitchMarkers(B, scene);
    v.setRailSwitchMarkersInstance(railSwitchMarkers);
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
    v.setRefreshJunctionsImpl(refreshJunctions);

    let disposed = false;

    async function loadChunk(cx: number, cz: number) {
      const exports = await window.webApp!;
      const res = await fetch(`/api/chunks/${cx}/${cz}`);
      if (!res.ok) throw new Error(`chunk fetch failed: ${res.status}`);
      const bytes = new Uint8Array(await res.arrayBuffer());
      exports.mcAdminLoadChunk(scene, bytes, zone.yMin, zone.yMax);
      // Block materials are created lazily by ChunkManager.getBlockMaterials() on first chunk mesh —
      // re-apply the current clip-plane state so freshly created materials pick up an already-toggled plane.
      applyClipPlanes(B!, scene, v.clipPlanesRef.current, clipBounds, overlayMeshes, v.clipMeshesRef.current);
      // chunkBuilder.ts sets isPickable=false on chunk meshes — the live game targets blocks via
      // a custom voxel raycast, not scene.pick(). The editor relies on scene.pick() for place/
      // break, so re-enable picking on whatever meshes this call just (re)built. Every reload (a
      // place/break, or just streaming newly-visible chunks while panning) walks ALL scene meshes,
      // so the ghost/outline window must be explicitly excluded or they'd become pickable again —
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
          if (v.testRailStateRef.current !== "idle") refreshJunctions();
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
      (message) => v.flashActionError(message),
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

    // Bridges this editor's chunk/wasm specifics to the shared handler logic in
    // voxelEditorSceneController.ts — see voxelVolumeAdapter.ts for what each method means.
    const adapter: VoxelVolumeAdapter<InstanceBlockDto> = {
      isReady: () => !!wasmExports,
      inBounds: (x, y, z) => y >= zone.yMin && y <= zone.yMax && inZone(x, z),
      getBlockOrdinalAt: (x, y, z) => (wasmExports ? wasmExports.mcAdminGetBlockOrdinalAt(scene, x, y, z) : 0),
      getBlockStateAt: (x, y, z) => (wasmExports ? wasmExports.mcAdminGetBlockStateAt(scene, x, y, z) : 0),
      getUsedXZOffsetAt: (x, y, z) =>
        wasmExports
          ? usedSlotAt((wx, wy, wz) => wasmExports!.mcAdminGetUsedXZOffsetAt(scene, wx, wy, wz), x, y, z)
          : null,
      getEditSocket: () => editSocket,
      // No local-apply step — reloadChunk below re-fetches the freshly-edited chunk from the
      // server instead.
      applyLocal: () => {},
      afterEdit: (touched) => {
        const chunks = new Set<string>();
        for (const { x, z } of touched) chunks.add(chunkKeyOf(Math.floor(x / CHUNK_SIZE), Math.floor(z / CHUNK_SIZE)));
        for (const key of chunks) {
          const [cx, cz] = key.split(",").map(Number);
          reloadChunk(cx, cz);
        }
      },
      afterRailSwitchToggle: (junction) => {
        reloadChunk(Math.floor(junction.wx / CHUNK_SIZE), Math.floor(junction.wz / CHUNK_SIZE));
      },
      // Picks only ever land on real meshes, already inside the zone — never needed an extra
      // bounds check here, unlike Scene.
      railPickInBounds: () => true,
    };

    const controller = createVoxelEditorSceneController({
      B,
      scene,
      overlay,
      groundMeshes,
      selectionGizmo,
      pasteGizmo,
      railSwitchMarkers,
      clipBounds,
      adapter,
      makeEdit: (fields) => fields as InstanceBlockDto,
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
      scene,
      camera,
      canvas,
      getMode: () => v.modeRef.current,
      onHoverMove: controller.updateHoverPreview,
      onCtrlClick: controller.onCtrlClick,
      onClick: controller.onClick,
    });

    const axesGizmo = createAxesGizmo(B, engine, camera);
    engine.runRenderLoop(() => {
      scene.render();
      axesGizmo.render();
    });
    const onResize = () => engine.resize();
    window.addEventListener("resize", onResize);

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
      editSocket.close();
      axesGizmo.dispose();
      engine.dispose();
      v.setScene(null);
      v.setOverlayMeshes(null);
      v.setSelectionGizmoInstance(null);
      v.setPasteGizmoInstance(null);
      v.setClipMeshes({});
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- zone.id identity is the intended re-mount trigger
  }, [zone.id, v.blockDefsReady]);

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
        paletteRef={paletteRef}
      />
    </div>
  );
}
