import type { MutableRefObject } from "react";
import { packState } from "./blockState";
import type { SelectionBounds, SelectionBox, SelectionShape } from "./selectionGizmo";
import type { PasteOrigin } from "./pasteGizmo";
import type { RailSwitchMarkers } from "./railSwitchMarkers";
import type { RailTestCart } from "./railTestCart";
import { applyPasteTransform, type PasteTransform } from "./pasteTransform";
import { buildBlockPreviewMeshes } from "../../../../game/lib/chunkBuilder";
import { makeUndoRedoController, type UndoGroup } from "./undoRedoStack";
import { computeSelectionVoxels, resolvePatternBlock, MAX_SELECTION_OP_VOXELS, type Pattern } from "./selectionVoxels";
import { computeSlotOffset, resolvePlacementCell, PICK_EPSILON } from "./fractionalPlacement";
import type { VoxelBlockLike, VoxelVolumeAdapter } from "./voxelVolumeAdapter";
import type { createOverlayController } from "./overlayController";
import type { OrbitPointerControllerOptions } from "./orbitPointerController";

export interface VoxelEditorSceneControllerParams<TBlockDto extends VoxelBlockLike> {
  B: typeof BABYLON;
  scene: InstanceType<typeof BABYLON.Scene>;
  overlay: ReturnType<typeof createOverlayController>;
  groundMeshes: Set<InstanceType<typeof BABYLON.Mesh>>;
  selectionGizmo: { setSelection: (box: SelectionBox | null) => void };
  pasteGizmo: { setOrigin: (origin: PasteOrigin | null) => void };
  railSwitchMarkers: RailSwitchMarkers;
  clipBounds: SelectionBounds;
  adapter: VoxelVolumeAdapter<TBlockDto>;
  // Bridges a plain VoxelBlockLike edit literal to the caller's concrete DTO type (InstanceBlockDto
  // / SceneBlockDto) — both are structurally identical to VoxelBlockLike today, so callers just
  // pass an identity cast.
  makeEdit: (fields: VoxelBlockLike) => TBlockDto;

  // Refs/setters pulled straight from useVoxelEditorViewport's return value.
  selectionRef: MutableRefObject<SelectionBox | null>;
  selectionShapeRef: MutableRefObject<SelectionShape>;
  patternBlocksRef: MutableRefObject<[string | null, string | null]>;
  clipboardRef: MutableRefObject<{
    entries: { relX: number; relY: number; relZ: number; type: string; state: number }[];
  } | null>;
  pasteOriginRef: MutableRefObject<PasteOrigin | null>;
  pasteTransformRef: MutableRefObject<PasteTransform>;
  isPastingRef: MutableRefObject<boolean>;
  ordinalByNameRef: MutableRefObject<Map<string, number>>;
  nameByOrdinalRef: MutableRefObject<string[]>;
  selectedTypeRef: MutableRefObject<string | null>;
  selectedColorIndexRef: MutableRefObject<number>;
  modeRef: MutableRefObject<"place" | "break" | "select">;
  testRailStateRef: MutableRefObject<"idle" | "picking" | "running">;
  railTestCartRef: MutableRefObject<RailTestCart | null>;
  undoStackRef: MutableRefObject<UndoGroup<VoxelBlockLike>[]>;
  redoStackRef: MutableRefObject<UndoGroup<VoxelBlockLike>[]>;
  pastePreviewMeshesRef: MutableRefObject<ReturnType<typeof buildBlockPreviewMeshes>>;

  setSelection: (box: SelectionBox | null) => void;
  setPasteOrigin: (origin: PasteOrigin | null) => void;
  setIsPasting: (v: boolean) => void;
  setPasteTransform: (t: PasteTransform) => void;
  setClipboardCount: (n: number | null) => void;
  setTestRailState: (
    updater: "idle" | "picking" | "running" | ((s: "idle" | "picking" | "running") => "idle" | "picking" | "running"),
  ) => void;
  setHoveredVoxelName: (name: string | null) => void;
  flashActionError: (message: string) => void;
  shortcutBarRef: MutableRefObject<{ slots: (string | null)[]; selectSlot: (idx: number) => void }>;
  selectBlockType: (name: string) => void;
  toggleTestRail: () => void;
}

// Handlers shared verbatim between InstanceEditorViewport and SceneEditorViewport that DO need
// Babylon/wasm objects created in each component's own scene-setup effect: paste, Fill/Shell/Cut,
// undo/redo, hover preview, keyboard shortcuts, and the main pointer click dispatcher. Every place
// these differ between the two editors (bounds check, wasm export names, chunk-reload vs
// entity-reload, editSocket nullability) is routed through `adapter` — see voxelVolumeAdapter.ts.
export function createVoxelEditorSceneController<TBlockDto extends VoxelBlockLike>(
  params: VoxelEditorSceneControllerParams<TBlockDto>,
) {
  const {
    B,
    scene,
    overlay,
    groundMeshes,
    selectionGizmo,
    pasteGizmo,
    railSwitchMarkers,
    clipBounds,
    adapter,
    makeEdit,
    selectionRef,
    selectionShapeRef,
    patternBlocksRef,
    clipboardRef,
    pasteOriginRef,
    pasteTransformRef,
    isPastingRef,
    ordinalByNameRef,
    nameByOrdinalRef,
    selectedTypeRef,
    selectedColorIndexRef,
    modeRef,
    testRailStateRef,
    railTestCartRef,
    undoStackRef,
    redoStackRef,
    pastePreviewMeshesRef,
    setSelection,
    setPasteOrigin,
    setIsPasting,
    setPasteTransform,
    setClipboardCount,
    setTestRailState,
    setHoveredVoxelName,
    flashActionError,
    shortcutBarRef,
    selectBlockType,
    toggleTestRail,
  } = params;

  // Paste preview: one buildBlockPreviewMeshes call per clipboard entry (each call renders a
  // single voxel at local origin — see chunkBuilder.ts), positioned at pasteOrigin + rel and left
  // at the ghost shader's built-in 0.5 alpha (BLOCK_GHOST_FRAG in game/lib/block.ts).
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
      for (const mesh of buildBlockPreviewMeshes(scene, ordinal, entry.state & 0x03)) {
        mesh.position = new B.Vector3(origin.x + entry.relX, origin.y + entry.relY, origin.z + entry.relZ);
        mesh.isPickable = false;
        // Same depth-bias neutralization as overlayController.ts's showGhostAndOutline — the ghost
        // shader's zOffset bias is tuned for FPS-scale render distance and misbehaves at this
        // editor's much larger orbit-camera distances (see that file's comment).
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

  function usedSlotAtLocal(wx: number, wy: number, wz: number): [number, number] | null {
    if (!adapter.isReady()) return null;
    return adapter.getUsedXZOffsetAt(wx, wy, wz);
  }

  function resolvePlacementCellLocal(
    pickedPoint: InstanceType<typeof BABYLON.Vector3>,
    normal: InstanceType<typeof BABYLON.Vector3>,
  ): [number, number, number] {
    if (!adapter.isReady()) {
      return [
        Math.floor(pickedPoint.x + normal.x * PICK_EPSILON),
        Math.floor(pickedPoint.y + normal.y * PICK_EPSILON),
        Math.floor(pickedPoint.z + normal.z * PICK_EPSILON),
      ];
    }
    return resolvePlacementCell(pickedPoint, normal, adapter.getBlockOrdinalAt, (ordinal) =>
      window.mc.getBlockDef(ordinal),
    );
  }

  const pasteActions = {
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
      pasteTransformRef.current = { rotation: 0, flipX: false, flipY: false, flipZ: false };
      setPasteTransform(pasteTransformRef.current);
      // Hides the selection wireframe while the paste gizmo is active — the two overlap and fight
      // for visual attention at the same spot. Selection state itself is untouched, so it
      // reappears once paste ends (see cancel below).
      selectionGizmo.setSelection(null);
      pasteGizmo.setOrigin(origin);
      renderPastePreview(origin);
    },
    move: (origin: PasteOrigin) => {
      if (!pasteOriginRef.current) return;
      pasteOriginRef.current = origin;
      setPasteOrigin(origin);
      pasteGizmo.setOrigin(origin);
      renderPastePreview(origin);
    },
    rotate: (dir: -1 | 1) => {
      if (!pasteOriginRef.current) return;
      const rotation = (((pasteTransformRef.current.rotation + dir) % 4) + 4) % 4;
      const next: PasteTransform = { ...pasteTransformRef.current, rotation: rotation as 0 | 1 | 2 | 3 };
      pasteTransformRef.current = next;
      setPasteTransform(next);
      renderPastePreview(pasteOriginRef.current);
    },
    flip: (axis: "x" | "y" | "z") => {
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
        pasteActions.cancel();
        return;
      }
      const edits: TBlockDto[] = [];
      const undoGroup: VoxelBlockLike[] = [];
      const touched: { x: number; z: number }[] = [];
      for (const entry of entries) {
        const x = origin.x + entry.relX;
        const y = origin.y + entry.relY;
        const z = origin.z + entry.relZ;
        if (!adapter.inBounds(x, y, z)) continue;
        const prevOrdinal = adapter.isReady() ? adapter.getBlockOrdinalAt(x, y, z) : 0;
        const prevState = adapter.isReady() ? adapter.getBlockStateAt(x, y, z) : 0;
        const prevType = adapter.isReady() ? (nameByOrdinalRef.current[prevOrdinal] ?? "AIR") : "AIR";
        undoGroup.push({ x, y, z, type: prevType, state: prevState, xOffset: 0, zOffset: 0 });
        const edit = makeEdit({ x, y, z, type: entry.type, state: entry.state, xOffset: 0, zOffset: 0 });
        edits.push(edit);
        touched.push({ x, z });
      }
      if (edits.length > 0) {
        adapter.getEditSocket()?.sendBatch(edits);
        pushUndo(undoGroup);
        for (const edit of edits) adapter.applyLocal(edit);
        adapter.afterEdit(touched);
      }
      pasteActions.cancel();
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

  // Debug helper (KeyV): logs every non-air block in the current selection as JSON — blocktype,
  // position, rotation (bits 0-1 of the state byte, mirrors BlockState.rotation() in Kotlin).
  function dumpSelectionToConsole() {
    const box = selectionRef.current;
    if (!box || !adapter.isReady()) return;
    const voxels = computeSelectionVoxels(box, selectionShapeRef.current, "fill");
    if (!voxels) {
      flashActionError(`Selection too large (max ${MAX_SELECTION_OP_VOXELS} voxels)`);
      return;
    }
    const blocks = voxels
      .map(({ x, y, z }) => {
        const ordinal = adapter.getBlockOrdinalAt(x, y, z);
        const state = adapter.getBlockStateAt(x, y, z);
        const type = nameByOrdinalRef.current[ordinal] ?? "AIR";
        return { type, x, y, z, rotation: state & 0x03 };
      })
      .filter((b) => b.type !== "AIR");
    console.log(JSON.stringify(blocks, null, 2));
  }

  function captureBlock(at: VoxelBlockLike): VoxelBlockLike {
    if (!adapter.isReady())
      return { x: at.x, y: at.y, z: at.z, type: "AIR", state: 0, xOffset: at.xOffset, zOffset: at.zOffset };
    const ordinal = adapter.getBlockOrdinalAt(at.x, at.y, at.z);
    const state = adapter.getBlockStateAt(at.x, at.y, at.z);
    const type = nameByOrdinalRef.current[ordinal] ?? "AIR";
    return { x: at.x, y: at.y, z: at.z, type, state, xOffset: at.xOffset, zOffset: at.zOffset };
  }

  function applyEntry(entry: VoxelBlockLike, onSuccess: () => void) {
    const edit = makeEdit(entry);
    adapter.getEditSocket()?.send(edit);
    onSuccess();
    adapter.applyLocal(edit);
    adapter.afterEdit([{ x: entry.x, z: entry.z }]);
  }

  const { pushUndo, performUndo, performRedo } = makeUndoRedoController<VoxelBlockLike>(
    undoStackRef,
    redoStackRef,
    captureBlock,
    applyEntry,
  );

  // Fill/Shell/Cut on the current selection — one WS batch frame (editSocket.sendBatch) instead of
  // N individual sends, one undo-stack group instead of N slots, one afterEdit call instead of per
  // voxel. See selectionVoxels.ts for the voxel enumeration/cap.
  function runSelectionOp(kind: "fill" | "shell" | "cut" | "copy") {
    const box = selectionRef.current;
    if (!box) return;
    if (kind !== "cut" && kind !== "copy" && !patternBlocksRef.current[0]) return;
    const shapeMode = kind === "cut" || kind === "copy" ? "fill" : kind;
    const voxels = computeSelectionVoxels(box, selectionShapeRef.current, shapeMode);
    if (!voxels) {
      flashActionError(`Selection too large (max ${MAX_SELECTION_OP_VOXELS} voxels)`);
      return;
    }
    const valid = voxels.filter((v) => adapter.inBounds(v.x, v.y, v.z));
    if (valid.length === 0) return;

    const pattern: Pattern = { a: patternBlocksRef.current[0] ?? "AIR", b: patternBlocksRef.current[1] ?? undefined };
    const originX = Math.floor(box.minX);
    const originY = Math.floor(box.minY);
    const originZ = Math.floor(box.minZ);
    const edits: TBlockDto[] = [];
    const undoGroup: VoxelBlockLike[] = [];
    const clipEntries: { relX: number; relY: number; relZ: number; type: string; state: number }[] = [];
    const touched: { x: number; z: number }[] = [];

    for (const v of valid) {
      const prevOrdinal = adapter.isReady() ? adapter.getBlockOrdinalAt(v.x, v.y, v.z) : 0;
      const prevState = adapter.isReady() ? adapter.getBlockStateAt(v.x, v.y, v.z) : 0;
      const prevType = adapter.isReady() ? (nameByOrdinalRef.current[prevOrdinal] ?? "AIR") : "AIR";

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
      edits.push(makeEdit({ x: v.x, y: v.y, z: v.z, type, state: 0, xOffset: 0, zOffset: 0 }));
      touched.push({ x: v.x, z: v.z });
    }

    if (kind !== "copy") {
      adapter.getEditSocket()?.sendBatch(edits);
      pushUndo(undoGroup);
      for (const edit of edits) adapter.applyLocal(edit);
      adapter.afterEdit(touched);
    }
    if (kind === "cut" || kind === "copy") {
      clipboardRef.current = { entries: clipEntries };
      setClipboardCount(clipEntries.length);
    }
  }

  // Block name shown top-right whenever a voxel is under the cursor — independent of edit mode, so
  // it also updates while in select mode or mid-rail-test-picking (unlike the ghost/break overlay
  // below, which those modes suppress).
  function updateHoveredVoxelName(
    pick: ReturnType<InstanceType<typeof BABYLON.Scene>["pick"]>,
    normal: InstanceType<typeof BABYLON.Vector3> | null,
    onGround: boolean,
  ) {
    if (!pick?.hit || !pick.pickedPoint || onGround || !normal || !adapter.isReady()) {
      setHoveredVoxelName(null);
      return;
    }
    const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
    const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
    const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
    if (!adapter.inBounds(bx, by, bz)) {
      setHoveredVoxelName(null);
      return;
    }
    const ordinal = adapter.getBlockOrdinalAt(bx, by, bz);
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
      if (onGround || !normal || !adapter.isReady()) {
        overlay.disposeBreakOverlay();
        return;
      }
      const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
      const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
      const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
      const targetOrdinal = adapter.getBlockOrdinalAt(bx, by, bz);
      const targetState = adapter.getBlockStateAt(bx, by, bz);
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
      ty = Math.floor(clipBounds.y[0]);
      tz = Math.floor(pick.pickedPoint.z);
    } else if (normal) {
      [tx, ty, tz] = resolvePlacementCellLocal(pick.pickedPoint, normal);
    } else {
      overlay.disposeGhost();
      overlay.disposeOutline();
      return;
    }
    if (!adapter.inBounds(tx, ty, tz)) {
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
        pasteActions.confirm();
        return;
      }
      if (e.code === "Escape") {
        pasteActions.cancel();
        return;
      }
    }
    if (e.code === "Escape" && testRailStateRef.current !== "idle") {
      railTestCartRef.current?.stop();
      railSwitchMarkers.clear();
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

  const onCtrlClick: NonNullable<OrbitPointerControllerOptions["onCtrlClick"]> = (pick) => {
    const normal = pick.getNormal(true);
    const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);
    if (onGround || !normal || !pick.pickedPoint || !adapter.isReady()) return;
    const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
    const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
    const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
    if (!adapter.inBounds(bx, by, bz)) return;
    const ordinal = adapter.getBlockOrdinalAt(bx, by, bz);
    const name = nameByOrdinalRef.current[ordinal];
    if (!name || name === "AIR") return;
    const slotIdx = shortcutBarRef.current.slots.findIndex((s) => s === name);
    if (slotIdx !== -1) {
      shortcutBarRef.current.selectSlot(slotIdx);
    } else {
      selectBlockType(name);
    }
  };

  const onClick: OrbitPointerControllerOptions["onClick"] = ({ pick, normal, mode: currentMode, shiftKey }) => {
    const onGround = groundMeshes.has(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh>);

    if (testRailStateRef.current !== "idle") {
      const junction = railSwitchMarkers.hitTest(pick.pickedMesh as InstanceType<typeof BABYLON.Mesh> | null);
      if (junction) {
        adapter.getEditSocket()?.sendRaw({ x: junction.wx, y: junction.wy, z: junction.wz });
        adapter.afterRailSwitchToggle(junction);
        return;
      }
    }

    if (testRailStateRef.current === "picking") {
      if (onGround || !normal || !pick.pickedPoint) return;
      const bx = Math.floor(pick.pickedPoint.x - normal.x * PICK_EPSILON);
      const by = Math.floor(pick.pickedPoint.y - normal.y * PICK_EPSILON);
      const bz = Math.floor(pick.pickedPoint.z - normal.z * PICK_EPSILON);
      if (adapter.railPickInBounds && !adapter.railPickInBounds(bx, by, bz)) return;
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
      // aimed at (e.g. one LEGO_PIECE among several stacked/slotted in the same cell), same idea
      // as the in-game precise break (LocalPlayerController.kt).
      let breakXOffset = 0;
      let breakZOffset = 0;
      let prevType = "AIR";
      let prevState = 0;
      if (adapter.isReady()) {
        const targetOrdinal = adapter.getBlockOrdinalAt(bx, by, bz);
        const targetState = adapter.getBlockStateAt(bx, by, bz);
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
      const edit = makeEdit({
        x: bx,
        y: by,
        z: bz,
        type: "AIR",
        state: 0,
        xOffset: breakXOffset,
        zOffset: breakZOffset,
      });
      adapter.getEditSocket()?.send(edit);
      pushUndo({ x: bx, y: by, z: bz, type: prevType, state: prevState, xOffset: breakXOffset, zOffset: breakZOffset });
      adapter.applyLocal(edit);
      adapter.afterEdit([{ x: bx, z: bz }]);
      return;
    }

    // place mode
    const type = selectedTypeRef.current;
    if (!type || !pick.pickedPoint) return;
    let tx: number, ty: number, tz: number;
    if (onGround) {
      tx = Math.floor(pick.pickedPoint.x);
      ty = Math.floor(clipBounds.y[0]);
      tz = Math.floor(pick.pickedPoint.z);
    } else if (normal) {
      [tx, ty, tz] = resolvePlacementCellLocal(pick.pickedPoint, normal);
    } else {
      return;
    }
    if (!adapter.inBounds(tx, ty, tz)) return;
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
    if (adapter.isReady()) {
      const prevOrdinal = adapter.getBlockOrdinalAt(tx, ty, tz);
      // Full state byte (rotation + color, see BlockState.kt) so undo restores the exact previous
      // look, not just its rotation.
      prevState = adapter.getBlockStateAt(tx, ty, tz);
      prevType = nameByOrdinalRef.current[prevOrdinal] ?? "AIR";
    }
    const state = packState(overlay.getPlacementRotation(), selectedColorIndexRef.current);
    if (ordinal == null) return;
    const edit = makeEdit({ x: tx, y: ty, z: tz, type, state, xOffset, zOffset });
    adapter.getEditSocket()?.send(edit);
    pushUndo({ x: tx, y: ty, z: tz, type: prevType, state: prevState, xOffset, zOffset });
    adapter.applyLocal(edit);
    adapter.afterEdit([{ x: tx, z: tz }]);
  };

  return {
    pasteActions,
    dumpSelectionToConsole,
    pushUndo,
    performUndo,
    performRedo,
    runSelectionOp,
    updateHoverPreview,
    onKeyDown,
    onCtrlClick,
    onClick,
    disposePastePreview,
    renderPastePreview,
  };
}
