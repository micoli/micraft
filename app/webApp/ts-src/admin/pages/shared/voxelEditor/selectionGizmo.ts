import { boxLines } from "../../../../game/lib/targeting/targeting";

// Axis-aligned block-coordinate bounding box — max* is exclusive (a single voxel at (x,y,z) is
// {minX:x, minY:y, minZ:z, maxX:x+1, maxY:y+1, maxZ:z+1}), same convention as chunk/scene block
// storage. Every selection shape (box/sphere/spheroid/cylinder) is stored as this same bounding
// box — only which handles are active and how the box is rendered differ per shape; sphere and
// spheroid read as an ellipsoid inscribed in the box, cylinder as an elliptical-cylinder inscribed
// in the box (Y = height range, X/Z = per-direction radius).
export interface SelectionBox {
  minX: number;
  minY: number;
  minZ: number;
  maxX: number;
  maxY: number;
  maxZ: number;
}

export interface SelectionBounds {
  x: readonly [number, number];
  y: readonly [number, number];
  z: readonly [number, number];
}

export type SelectionShape = "box" | "sphere" | "spheroid" | "cylinder";

// Grid-snapped voxel preview drawn inside the shape ("none" = no preview, just the wireframe).
export type SelectionSnap = "none" | "voxel" | "half" | "quarter";
const SNAP_STEP: Record<Exclude<SelectionSnap, "none">, number> = { voxel: 1, half: 0.5, quarter: 0.25 };
const MAX_PREVIEW_CELLS = 2_000_000;
const MAX_PREVIEW_EDGES = 2_000_000;

// Same per-axis colors as axesGizmo.ts (X/Y/Z = red/green/blue) — kept as a separate literal here
// rather than importing, since axesGizmo.ts doesn't export its AXES table.
const AXIS_HEX = { x: "#e5484d", y: "#46a758", z: "#3d63dd" };
const HANDLE_OFFSET = 0.35;
// setCustomMesh reparents a handle mesh directly under the gizmo's root, skipping AxisDragGizmo's
// own child transform (_gizmoMesh) that bakes in a 1/3 scale-down on the stock arrow mesh — a mesh
// sized in ordinary world units reads roughly 3x too large there, so both the resize and move
// handles' plates are sized down to compensate. Thin along the drag axis, wide on the other two so
// a plate reads as flat against the face rather than a floating cube.
const PLATE_WIDE = (1 / 3) * 0.05;
const PLATE_THIN = (1 / 8) * 0.05;
function plateDimsForAxis(axis: Axis): { width: number; height: number; depth: number } {
  return axis === "x"
    ? { width: PLATE_THIN, height: PLATE_WIDE, depth: PLATE_WIDE }
    : axis === "y"
      ? { width: PLATE_WIDE, height: PLATE_THIN, depth: PLATE_WIDE }
      : { width: PLATE_WIDE, height: PLATE_WIDE, depth: PLATE_THIN };
}
const AXIS_LINE_HALF_LENGTH = 64;
// Handle drag snaps to quarter-voxel increments rather than whole voxels — finer selections (e.g.
// around sub-voxel LEGO_PIECE slots) are common enough to be worth the extra precision.
const QUARTER_STEPS_PER_VOXEL = 4;
const MIN_EXTENT = 1 / QUARTER_STEPS_PER_VOXEL;
const RING_SEGMENTS = 32;

type Axis = "x" | "y" | "z";
interface HandleSpec {
  axis: Axis;
  sign: 1 | -1;
}
const HANDLES: HandleSpec[] = [
  { axis: "x", sign: 1 },
  { axis: "x", sign: -1 },
  { axis: "y", sign: 1 },
  { axis: "y", sign: -1 },
  { axis: "z", sign: 1 },
  { axis: "z", sign: -1 },
];

// Which of the 6 face handles are active per shape. box/spheroid/cylinder all expose the full set
// (each independently draggable, exactly like the box today) — only sphere restricts to a single
// handle, since dragging it resizes all three axes together (see applySymmetricDelta). Corners are
// box-only (handled separately from this table, see isCornerActive).
const SPHERE_HANDLES: HandleSpec[] = [{ axis: "x", sign: 1 }];
function activeHandles(shape: SelectionShape): HandleSpec[] {
  return shape === "sphere" ? SPHERE_HANDLES : HANDLES;
}
function isHandleActive(shape: SelectionShape, spec: HandleSpec): boolean {
  return activeHandles(shape).some((h) => h.axis === spec.axis && h.sign === spec.sign);
}

interface CornerSpec {
  sx: 1 | -1;
  sy: 1 | -1;
  sz: 1 | -1;
}
const CORNERS: CornerSpec[] = [];
for (const sx of [1, -1] as const)
  for (const sy of [1, -1] as const) for (const sz of [1, -1] as const) CORNERS.push({ sx, sy, sz });
const CORNER_SIZE = 0.15;
const CORNER_OFFSET = 0.06;
// Move gizmo: small cube at the selection's centroid + 6 translate handles sitting just outside
// its faces (flat plates, same dimensions as the resize handles — see PLATE_WIDE/PLATE_THIN at
// their definition site). Fixed offset from the centroid (not scaled to the box, unlike the resize
// handles sitting on the box's own faces) so it stays reachable at any selection size.
const CENTER_CUBE_SIZE = 0.2;
const MOVE_HANDLE_OFFSET = CENTER_CUBE_SIZE / 2 + 0.05;
const MOVE_HANDLE_COLOR = "#cccccc";

function clamp(v: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, v));
}

// Blockbench-style selection gizmo: a persistent wireframe over the current selection, plus a
// per-shape set of colored handles that extend/contract the underlying bounding box a
// quarter-voxel at a time when dragged along their axis, plus decorative (non-interactive)
// infinite axis lines.
//
// Handles are BABYLON.AxisDragGizmo instances (Babylon's own official single-axis drag gizmo —
// the same building block BoundingBoxGizmo/PositionGizmo use) rather than a hand-rolled mesh +
// PointerDragBehavior: it already handles per-browser pointer-event quirks and comes with
// constant on-screen sizing regardless of camera distance, both of which a from-scratch
// implementation had to fight (a flat world-space handle size shrinks to a few unclickable pixels
// once zoomed out, and raw PointerDragBehavior wiring saw inconsistent drag-start behavior in
// Firefox). Each gizmo tracks an invisible per-handle TransformNode anchor that setSelection
// repositions on every change — the gizmo mesh follows the anchor automatically every frame.
//
// The whole thing lives in its own BABYLON.UtilityLayerRenderer scene rather than the main scene:
// a selection is routinely made against/inside dense terrain (walls, tree trunks…), and plain
// scene.pick() always returns whichever pickable mesh is nearest the camera along the ray — a
// handle floating just outside the selection box would frequently lose that race to solid terrain
// sitting in front of it, making it unclickable. The utility layer renders as a separate
// always-on-top pass and picks independently, so handles are never occluded and a hit on one
// pre-empts the main scene's own pointer handling for that event.
export function createSelectionGizmo(
  B: typeof BABYLON,
  scene: InstanceType<typeof BABYLON.Scene>,
  bounds: SelectionBounds,
  onSelectionChange: (box: SelectionBox) => void,
  onPreviewTooLarge?: (message: string) => void,
) {
  const utilityLayer = new B.UtilityLayerRenderer(scene);
  const gizmoScene = utilityLayer.utilityLayerScene;
  const CORNER_DRAG_AXIS = new B.Vector3(1, 1, 1).normalize();

  let current: SelectionBox | null = null;
  let shape: SelectionShape = "box";
  let snap: SelectionSnap = "none";
  let outlineMeshes: InstanceType<typeof BABYLON.LinesMesh>[] = [];
  let axisLines: InstanceType<typeof BABYLON.LinesMesh> | null = null;
  const handles: {
    spec: HandleSpec;
    anchor: InstanceType<typeof BABYLON.TransformNode>;
    gizmo: InstanceType<typeof BABYLON.AxisDragGizmo>;
    attached: boolean;
  }[] = [];
  const corners: {
    spec: CornerSpec;
    mesh: InstanceType<typeof BABYLON.Mesh>;
  }[] = [];
  let cornersActive = false;
  const moveHandles: {
    spec: HandleSpec;
    anchor: InstanceType<typeof BABYLON.TransformNode>;
    gizmo: InstanceType<typeof BABYLON.AxisDragGizmo>;
    attached: boolean;
  }[] = [];
  let centerCube: InstanceType<typeof BABYLON.Mesh> | null = null;

  // clampToBounds is false for spheroid/cylinder (like sphere's applySymmetricDelta, these are
  // pure selection markers, not directly bound to editable voxel content) — only box stays
  // clamped to the zone edges, since a box selection maps 1:1 onto actual blocks to edit.
  function applyAxisDelta(
    box: SelectionBox,
    axis: Axis,
    sign: 1 | -1,
    voxelDelta: number,
    clampToBounds: boolean = true,
  ): SelectionBox {
    const next = { ...box };
    const [boundLo, boundHi] = bounds[axis];
    const lo = clampToBounds ? boundLo : -Infinity;
    const hi = clampToBounds ? boundHi : Infinity;
    if (axis === "x") {
      if (sign === 1) next.maxX = clamp(box.maxX + voxelDelta, box.minX + MIN_EXTENT, hi);
      else next.minX = clamp(box.minX + voxelDelta, lo, box.maxX - MIN_EXTENT);
    } else if (axis === "y") {
      if (sign === 1) next.maxY = clamp(box.maxY + voxelDelta, box.minY + MIN_EXTENT, hi);
      else next.minY = clamp(box.minY + voxelDelta, lo, box.maxY - MIN_EXTENT);
    } else {
      if (sign === 1) next.maxZ = clamp(box.maxZ + voxelDelta, box.minZ + MIN_EXTENT, hi);
      else next.minZ = clamp(box.minZ + voxelDelta, lo, box.maxZ - MIN_EXTENT);
    }
    return next;
  }

  // Sphere mode has a single handle: dragging it grows/shrinks the box symmetrically around its
  // own center on all three axes at once, keeping it cubic (a sphere is rendered as the ellipsoid
  // inscribed in the box, so a cubic box is what makes it read as a true sphere rather than an
  // ellipsoid). Deliberately NOT clamped to `bounds` (unlike every other shape/handle) — clamping
  // any one axis (e.g. Y against the ground) would force that axis's half-extent below the other
  // two, breaking the sphere into an ellipsoid. A sphere selection is allowed to extend past the
  // zone edges (e.g. partway under the ground) rather than deform to stay inside them.
  // Shifts the box uniformly along one axis by voxelDelta, keeping its size fixed — used by the
  // move gizmo. clampToBounds (box shape only, same convention as applyAxisDelta) slides the whole
  // box back inside bounds rather than clipping it, so its size never changes from a move.
  function translateAxis(box: SelectionBox, axis: Axis, voxelDelta: number, clampToBounds: boolean): SelectionBox {
    const next = { ...box };
    if (axis === "x") {
      next.minX += voxelDelta;
      next.maxX += voxelDelta;
    } else if (axis === "y") {
      next.minY += voxelDelta;
      next.maxY += voxelDelta;
    } else {
      next.minZ += voxelDelta;
      next.maxZ += voxelDelta;
    }
    if (!clampToBounds) return next;
    const [lo, hi] = bounds[axis];
    if (axis === "x") {
      const size = next.maxX - next.minX;
      if (next.minX < lo) {
        next.minX = lo;
        next.maxX = lo + size;
      } else if (next.maxX > hi) {
        next.maxX = hi;
        next.minX = hi - size;
      }
    } else if (axis === "y") {
      const size = next.maxY - next.minY;
      if (next.minY < lo) {
        next.minY = lo;
        next.maxY = lo + size;
      } else if (next.maxY > hi) {
        next.maxY = hi;
        next.minY = hi - size;
      }
    } else {
      const size = next.maxZ - next.minZ;
      if (next.minZ < lo) {
        next.minZ = lo;
        next.maxZ = lo + size;
      } else if (next.maxZ > hi) {
        next.maxZ = hi;
        next.minZ = hi - size;
      }
    }
    return next;
  }

  function applySymmetricDelta(box: SelectionBox, voxelDelta: number): SelectionBox {
    const cx = (box.minX + box.maxX) / 2;
    const cy = (box.minY + box.maxY) / 2;
    const cz = (box.minZ + box.maxZ) / 2;
    const currentRadius = (box.maxX - box.minX) / 2;
    const r = Math.max(MIN_EXTENT, currentRadius + voxelDelta);
    return { minX: cx - r, maxX: cx + r, minY: cy - r, maxY: cy + r, minZ: cz - r, maxZ: cz + r };
  }

  // Lit material (uses the utility layer's shared gizmo light) so handle plate faces shade darker
  // on their unlit sides — reads as a solid plate with depth rather than a flat emissive swatch.
  function handleMat(hex: string): InstanceType<typeof BABYLON.StandardMaterial> {
    utilityLayer._getSharedGizmoLight();
    const mat = new B.StandardMaterial(`selectionHandleMat${hex}`, gizmoScene);
    const color = B.Color3.FromHexString(hex);
    mat.diffuseColor = color;
    mat.emissiveColor = color.scale(0.35);
    mat.specularColor = B.Color3.Black();
    return mat;
  }

  for (const spec of HANDLES) {
    const anchor = new B.TransformNode(`selectionHandleAnchor${spec.axis}${spec.sign}`, gizmoScene);

    const dragAxis =
      spec.axis === "x" ? new B.Vector3(1, 0, 0) : spec.axis === "y" ? new B.Vector3(0, 1, 0) : new B.Vector3(0, 0, 1);
    const gizmo = new B.AxisDragGizmo(dragAxis, B.Color3.FromHexString(AXIS_HEX[spec.axis]), utilityLayer);
    gizmo.updateGizmoRotationToMatchAttachedMesh = false;
    gizmo.dragBehavior.moveAttached = false;
    // Swap the default arrow mesh for a flat rectangular plate lying against the selection face
    // (Blockbench-style handle) while keeping AxisDragGizmo's drag mechanics/on-screen sizing —
    // setCustomMesh reparents it directly under the gizmo's root, which the gizmo keeps positioned
    // at the attached anchor and scaled for a constant screen size every frame. See
    // plateDimsForAxis's doc comment for why the dims are scaled down.
    const handleBox = B.MeshBuilder.CreateBox(
      `selectionHandle${spec.axis}${spec.sign}`,
      plateDimsForAxis(spec.axis),
      gizmoScene,
    );
    handleBox.material = handleMat(AXIS_HEX[spec.axis]);
    gizmo.setCustomMesh(handleBox);
    let dragStart: SelectionBox | null = null;
    let dragTotal = 0;
    gizmo.dragBehavior.onDragStartObservable.add(() => {
      dragStart = current;
      dragTotal = 0;
    });
    gizmo.dragBehavior.onDragObservable.add((event) => {
      if (!dragStart) return;
      // event.dragDistance is the incremental delta since the previous drag event, not the total
      // offset since drag start (PointerDragBehavior recomputes it from lastDragPosition every
      // event) — must accumulate ourselves before snapping, otherwise each tiny per-frame delta
      // rounds to 0 and the box never appears to change.
      dragTotal += event.dragDistance;
      const voxelDelta = Math.round(dragTotal * QUARTER_STEPS_PER_VOXEL) / QUARTER_STEPS_PER_VOXEL;
      const next =
        shape === "sphere"
          ? applySymmetricDelta(dragStart, voxelDelta)
          : applyAxisDelta(dragStart, spec.axis, spec.sign, voxelDelta, shape === "box");
      if (current && sameBox(current, next)) return;
      onSelectionChange(next);
    });
    // Forces a resync to the authoritative box the instant the drag ends: the last onDragObservable
    // event before release is often filtered out by the sameBox check above (the raw pointer delta
    // rounds to the same quarter-voxel step as the previous event), leaving whichever visual
    // position Babylon's gizmo drag left the handle mesh at — this re-renders from `current`
    // unconditionally so the handle always ends up exactly back on the box, not wherever the
    // pointer happened to release.
    gizmo.dragBehavior.onDragEndObservable.add(() => {
      dragStart = null;
      // disposeVisuals first — render() only reassigns outlineMeshes/axisLines to new meshes, it
      // never disposes what was there before (every other call site disposes right before calling
      // it — setSelection, setShape); skipping that here left the previous outline/rings/axis-lines
      // mesh orphaned in the scene on every drag release, visible as stacked leftover selection
      // wireframes.
      if (current) {
        disposeVisuals();
        render(current);
      }
    });
    handles.push({ spec, anchor, gizmo, attached: false });
  }

  // Corner handles resize all three axes at once but only on the dragged corner's own side of each
  // axis (min or max, per spec.sx/sy/sz) — the opposite corners stay put. Plain PointerDragBehavior
  // (not AxisDragGizmo) since there's no stock diagonal-axis gizmo to reuse; the mesh IS the dragged
  // node here rather than a separate anchor+gizmo pair. Box-shape only — hidden for every other
  // selection shape (see cornersActive).
  for (const spec of CORNERS) {
    const mesh = B.MeshBuilder.CreateBox(
      `selectionCorner${spec.sx}${spec.sy}${spec.sz}`,
      { size: CORNER_SIZE },
      gizmoScene,
    );
    mesh.material = handleMat("#ffffff");
    mesh.isPickable = true;
    mesh.setEnabled(false);
    // Fixed (1,1,1) drag axis for every corner — not each corner's own outward diagonal — to match
    // applyAxisDelta's convention (also used by the face handles, whose AxisDragGizmo is likewise
    // always the fixed +axis regardless of min/max side): a positive voxelDelta grows the box on
    // sign===1 sides and shrinks it on sign===-1 sides. A per-corner outward diagonal would flip
    // that convention on whichever axes disagree with (1,1,1), which is what made exactly the
    // corners with an odd number of -1 components drag backwards.
    const behavior = new B.PointerDragBehavior({ dragAxis: CORNER_DRAG_AXIS });
    behavior.moveAttached = false;
    mesh.addBehavior(behavior);
    let dragStart: SelectionBox | null = null;
    let dragTotal = 0;
    behavior.onDragStartObservable.add(() => {
      dragStart = current;
      dragTotal = 0;
    });
    behavior.onDragObservable.add((event) => {
      if (!dragStart) return;
      dragTotal += event.dragDistance;
      const voxelDelta = Math.round(dragTotal * QUARTER_STEPS_PER_VOXEL) / QUARTER_STEPS_PER_VOXEL;
      let next = applyAxisDelta(dragStart, "x", spec.sx, voxelDelta);
      next = applyAxisDelta(next, "y", spec.sy, voxelDelta);
      next = applyAxisDelta(next, "z", spec.sz, voxelDelta);
      if (current && sameBox(current, next)) return;
      onSelectionChange(next);
    });
    // See the resize handle loop above for why this resync is needed at drag end.
    behavior.onDragEndObservable.add(() => {
      dragStart = null;
      // disposeVisuals first — render() only reassigns outlineMeshes/axisLines to new meshes, it
      // never disposes what was there before (every other call site disposes right before calling
      // it — setSelection, setShape); skipping that here left the previous outline/rings/axis-lines
      // mesh orphaned in the scene on every drag release, visible as stacked leftover selection
      // wireframes.
      if (current) {
        disposeVisuals();
        render(current);
      }
    });
    corners.push({ spec, mesh });
  }

  // Move gizmo: 6 handles at a fixed offset from the selection's centroid (reusing the same 6
  // axis/sign specs as the resize handles, purely for visual symmetry — either handle on an axis
  // does the same thing) that translate the whole box along one axis by the drag distance, size
  // unchanged. Always active for every shape (unlike the resize handles, which restrict to one
  // handle for "sphere") since moving is meaningful regardless of shape. Colored per-axis (red/
  // green/blue), same as the resize handles on that axis, so a handle's color always tells you
  // which axis it drags regardless of which gizmo (resize vs move) it belongs to.
  const moveAxisMat = { x: handleMat(AXIS_HEX.x), y: handleMat(AXIS_HEX.y), z: handleMat(AXIS_HEX.z) };
  for (const spec of HANDLES) {
    const anchor = new B.TransformNode(`selectionMoveHandleAnchor${spec.axis}${spec.sign}`, gizmoScene);
    const dragAxis =
      spec.axis === "x" ? new B.Vector3(1, 0, 0) : spec.axis === "y" ? new B.Vector3(0, 1, 0) : new B.Vector3(0, 0, 1);
    const gizmo = new B.AxisDragGizmo(dragAxis, B.Color3.FromHexString(AXIS_HEX[spec.axis]), utilityLayer);
    gizmo.updateGizmoRotationToMatchAttachedMesh = false;
    gizmo.dragBehavior.moveAttached = false;
    const handleMesh = B.MeshBuilder.CreateBox(
      `selectionMoveHandle${spec.axis}${spec.sign}`,
      plateDimsForAxis(spec.axis),
      gizmoScene,
    );
    handleMesh.material = moveAxisMat[spec.axis];
    gizmo.setCustomMesh(handleMesh);
    let dragStart: SelectionBox | null = null;
    let dragTotal = 0;
    gizmo.dragBehavior.onDragStartObservable.add(() => {
      dragStart = current;
      dragTotal = 0;
    });
    gizmo.dragBehavior.onDragObservable.add((event) => {
      if (!dragStart) return;
      dragTotal += event.dragDistance;
      const voxelDelta = Math.round(dragTotal * QUARTER_STEPS_PER_VOXEL) / QUARTER_STEPS_PER_VOXEL;
      const next = translateAxis(dragStart, spec.axis, voxelDelta, shape === "box");
      if (current && sameBox(current, next)) return;
      onSelectionChange(next);
    });
    // See the resize handle loop above for why this resync is needed at drag end.
    gizmo.dragBehavior.onDragEndObservable.add(() => {
      dragStart = null;
      // disposeVisuals first — render() only reassigns outlineMeshes/axisLines to new meshes, it
      // never disposes what was there before (every other call site disposes right before calling
      // it — setSelection, setShape); skipping that here left the previous outline/rings/axis-lines
      // mesh orphaned in the scene on every drag release, visible as stacked leftover selection
      // wireframes.
      if (current) {
        disposeVisuals();
        render(current);
      }
    });
    moveHandles.push({ spec, anchor, gizmo, attached: false });
  }
  centerCube = B.MeshBuilder.CreateBox("selectionCenterCube", { size: CENTER_CUBE_SIZE }, gizmoScene);
  centerCube.material = handleMat(MOVE_HANDLE_COLOR);
  centerCube.isPickable = false;
  centerCube.setEnabled(false);

  // Grid-snap voxel preview: solid purple outline of the voxelized surface of the shape at the
  // chosen step (which grid cells fall inside the shape, then only the faces exposed to an
  // outside/excluded neighbor) — the outer silhouette stair-stepped to the grid, not a filled
  // block of cubes.
  let snapMeshes: InstanceType<typeof BABYLON.LinesMesh>[] = [];
  const SNAP_PREVIEW_COLOR = B.Color3.FromHexString("#a855f7");
  const SNAP_PREVIEW_ALPHA = 0.1;

  function insideShape(
    cx: number,
    cy: number,
    cz: number,
    rx: number,
    ry: number,
    rz: number,
    x: number,
    y: number,
    z: number,
  ): boolean {
    if (shape === "box") return true;
    if (shape === "cylinder") {
      const dx = (x - cx) / rx;
      const dz = (z - cz) / rz;
      return dx * dx + dz * dz <= 1;
    }
    const dx = (x - cx) / rx;
    const dy = (y - cy) / ry;
    const dz = (z - cz) / rz;
    return dx * dx + dy * dy + dz * dz <= 1;
  }

  // Edges (as point pairs) of the exposed face of cell (ix,iy,iz) facing `axis`/`sign` — corner
  // coordinates come straight from the grid indices, no shape math needed here.
  function faceEdges(
    box: SelectionBox,
    step: number,
    ix: number,
    iy: number,
    iz: number,
    axis: Axis,
    sign: 1 | -1,
  ): [InstanceType<typeof BABYLON.Vector3>, InstanceType<typeof BABYLON.Vector3>][] {
    const corner = (di: number, dj: number, dk: number) =>
      new B.Vector3(box.minX + (ix + di) * step, box.minY + (iy + dj) * step, box.minZ + (iz + dk) * step);
    let p00, p10, p11, p01;
    if (axis === "x") {
      const i = sign === 1 ? 1 : 0;
      [p00, p10, p11, p01] = [corner(i, 0, 0), corner(i, 1, 0), corner(i, 1, 1), corner(i, 0, 1)];
    } else if (axis === "y") {
      const j = sign === 1 ? 1 : 0;
      [p00, p10, p11, p01] = [corner(0, j, 0), corner(1, j, 0), corner(1, j, 1), corner(0, j, 1)];
    } else {
      const k = sign === 1 ? 1 : 0;
      [p00, p10, p11, p01] = [corner(0, 0, k), corner(1, 0, k), corner(1, 1, k), corner(0, 1, k)];
    }
    return [
      [p00, p10],
      [p10, p11],
      [p11, p01],
      [p01, p00],
    ];
  }

  // Bailing out (returning null) rather than truncating avoids showing a partial/misleading
  // silhouette for shapes too large for this resolution — same policy as the old cell cap.
  function buildSnapEdges(
    box: SelectionBox,
    step: number,
  ): [InstanceType<typeof BABYLON.Vector3>, InstanceType<typeof BABYLON.Vector3>][] | null {
    const cx = (box.minX + box.maxX) / 2;
    const cy = (box.minY + box.maxY) / 2;
    const cz = (box.minZ + box.maxZ) / 2;
    const rx = box.maxX - cx;
    const ry = box.maxY - cy;
    const rz = box.maxZ - cz;
    const nx = Math.max(1, Math.round((box.maxX - box.minX) / step));
    const ny = Math.max(1, Math.round((box.maxY - box.minY) / step));
    const nz = Math.max(1, Math.round((box.maxZ - box.minZ) / step));
    if (nx * ny * nz > MAX_PREVIEW_CELLS) {
      onPreviewTooLarge?.("Selection too large to preview at this snap resolution");
      return null;
    }

    const inside = new Uint8Array(nx * ny * nz);
    const at = (ix: number, iy: number, iz: number) => (ix * ny + iy) * nz + iz;
    for (let ix = 0; ix < nx; ix++) {
      const x = box.minX + (ix + 0.5) * step;
      for (let iy = 0; iy < ny; iy++) {
        const y = box.minY + (iy + 0.5) * step;
        for (let iz = 0; iz < nz; iz++) {
          const z = box.minZ + (iz + 0.5) * step;
          inside[at(ix, iy, iz)] = insideShape(cx, cy, cz, rx, ry, rz, x, y, z) ? 1 : 0;
        }
      }
    }

    const NEIGHBORS: [Axis, 1 | -1, number, number, number][] = [
      ["x", 1, 1, 0, 0],
      ["x", -1, -1, 0, 0],
      ["y", 1, 0, 1, 0],
      ["y", -1, 0, -1, 0],
      ["z", 1, 0, 0, 1],
      ["z", -1, 0, 0, -1],
    ];
    const edges: [InstanceType<typeof BABYLON.Vector3>, InstanceType<typeof BABYLON.Vector3>][] = [];
    for (let ix = 0; ix < nx; ix++) {
      for (let iy = 0; iy < ny; iy++) {
        for (let iz = 0; iz < nz; iz++) {
          if (!inside[at(ix, iy, iz)]) continue;
          for (const [axis, sign, dx, dy, dz] of NEIGHBORS) {
            const nix = ix + dx;
            const niy = iy + dy;
            const niz = iz + dz;
            const neighborInside =
              nix >= 0 && nix < nx && niy >= 0 && niy < ny && niz >= 0 && niz < nz && inside[at(nix, niy, niz)];
            if (neighborInside) continue;
            edges.push(...faceEdges(box, step, ix, iy, iz, axis, sign));
            if (edges.length > MAX_PREVIEW_EDGES) {
              onPreviewTooLarge?.("Selection too large to preview at this snap resolution");
              return null;
            }
          }
        }
      }
    }
    return edges;
  }

  function renderSnapPreview(box: SelectionBox) {
    for (const mesh of snapMeshes) mesh.dispose();
    snapMeshes = [];
    if (snap === "none") return;
    const edges = buildSnapEdges(box, SNAP_STEP[snap]);
    if (!edges || edges.length === 0) return;
    // A single LineSystem batching every edge, rather than one mesh per edge (as the dashed-line
    // box outline does) — with edge counts in the thousands at fine snap resolutions, per-edge
    // meshes would be far more expensive than this one draw call.
    const mesh = B.MeshBuilder.CreateLineSystem("selectionSnapPreview", { lines: edges }, gizmoScene);
    mesh.color = SNAP_PREVIEW_COLOR;
    mesh.alpha = SNAP_PREVIEW_ALPHA;
    mesh.isPickable = false;
    snapMeshes = [mesh];
  }

  function sameBox(a: SelectionBox, b: SelectionBox): boolean {
    return (
      a.minX === b.minX &&
      a.minY === b.minY &&
      a.minZ === b.minZ &&
      a.maxX === b.maxX &&
      a.maxY === b.maxY &&
      a.maxZ === b.maxZ
    );
  }

  function disposeVisuals() {
    for (const mesh of outlineMeshes) mesh.dispose();
    outlineMeshes = [];
    axisLines?.dispose();
    axisLines = null;
    for (const mesh of snapMeshes) mesh.dispose();
    snapMeshes = [];
  }

  function ring(points: InstanceType<typeof BABYLON.Vector3>[]): InstanceType<typeof BABYLON.LinesMesh> {
    const mesh = B.MeshBuilder.CreateLines("selectionRing", { points: [...points, points[0]] }, gizmoScene);
    mesh.color = new B.Color3(1, 1, 1);
    mesh.isPickable = false;
    return mesh;
  }

  function ellipseXY(
    cx: number,
    cy: number,
    z: number,
    rx: number,
    ry: number,
  ): InstanceType<typeof BABYLON.Vector3>[] {
    const pts: InstanceType<typeof BABYLON.Vector3>[] = [];
    for (let i = 0; i < RING_SEGMENTS; i++) {
      const t = (i / RING_SEGMENTS) * Math.PI * 2;
      pts.push(new B.Vector3(cx + rx * Math.cos(t), cy + ry * Math.sin(t), z));
    }
    return pts;
  }

  function ellipseYZ(
    x: number,
    cy: number,
    cz: number,
    ry: number,
    rz: number,
  ): InstanceType<typeof BABYLON.Vector3>[] {
    const pts: InstanceType<typeof BABYLON.Vector3>[] = [];
    for (let i = 0; i < RING_SEGMENTS; i++) {
      const t = (i / RING_SEGMENTS) * Math.PI * 2;
      pts.push(new B.Vector3(x, cy + ry * Math.cos(t), cz + rz * Math.sin(t)));
    }
    return pts;
  }

  function ellipseXZ(
    cx: number,
    y: number,
    cz: number,
    rx: number,
    rz: number,
  ): InstanceType<typeof BABYLON.Vector3>[] {
    const pts: InstanceType<typeof BABYLON.Vector3>[] = [];
    for (let i = 0; i < RING_SEGMENTS; i++) {
      const t = (i / RING_SEGMENTS) * Math.PI * 2;
      pts.push(new B.Vector3(cx + rx * Math.cos(t), y, cz + rz * Math.sin(t)));
    }
    return pts;
  }

  // Applies (or reapplies) the current `shape` to the handle/corner visibility. Only touches
  // gizmo.attachedNode when a handle's visibility actually flips: AxisDragGizmo's
  // _attachedNodeChanged toggles dragBehavior.enabled off/on around every attachedNode assignment,
  // so reassigning it every call (even to the same anchor) would disable mid-drag and silently end
  // the gesture on the very next pointer event.
  function refreshHandleVisibility() {
    const show = current !== null;
    for (const h of handles) {
      const want = show && isHandleActive(shape, h.spec);
      if (want !== h.attached) {
        h.gizmo.attachedNode = want ? h.anchor : null;
        h.attached = want;
      }
    }
    const wantCorners = show && shape === "box";
    if (wantCorners !== cornersActive) {
      for (const { mesh } of corners) mesh.setEnabled(wantCorners);
      cornersActive = wantCorners;
    }
    for (const h of moveHandles) {
      if (show !== h.attached) {
        h.gizmo.attachedNode = show ? h.anchor : null;
        h.attached = show;
      }
    }
    centerCube?.setEnabled(show);
  }

  function setSelection(box: SelectionBox | null) {
    current = box;
    disposeVisuals();
    refreshHandleVisibility();
    if (!box) return;
    render(box);
  }

  function setShape(next: SelectionShape) {
    if (shape === next) return;
    shape = next;
    refreshHandleVisibility();
    disposeVisuals();
    if (current) render(current);
  }

  function setSnap(next: SelectionSnap) {
    if (snap === next) return;
    snap = next;
    if (current) renderSnapPreview(current);
  }

  function render(box: SelectionBox) {
    const cx = (box.minX + box.maxX) / 2;
    const cy = (box.minY + box.maxY) / 2;
    const cz = (box.minZ + box.maxZ) / 2;
    const rx = box.maxX - cx;
    const ry = box.maxY - cy;
    const rz = box.maxZ - cz;

    if (shape === "box") {
      // CreateDashedLines only draws a single continuous polyline, not disconnected segments, so
      // each box edge needs its own dashed mesh rather than one CreateLineSystem call.
      outlineMeshes = boxLines(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ).map(([a, b], i) => {
        const dashed = B.MeshBuilder.CreateDashedLines(
          `selectionOutline${i}`,
          { points: [a, b], dashSize: 3, gapSize: 2, dashNb: Math.max(2, Math.round(B.Vector3.Distance(a, b) * 6)) },
          gizmoScene,
        );
        dashed.color = new B.Color3(1, 1, 1);
        dashed.isPickable = false;
        return dashed;
      });
    } else if (shape === "sphere" || shape === "spheroid") {
      // Ellipsoid inscribed in the box, drawn as 3 orthogonal rings (sphere is just the case where
      // rx===ry===rz, guaranteed by applySymmetricDelta).
      outlineMeshes = [
        ring(ellipseXY(cx, cy, cz, rx, ry)),
        ring(ellipseYZ(cx, cy, cz, ry, rz)),
        ring(ellipseXZ(cx, cy, cz, rx, rz)),
      ];
    } else {
      // Elliptical cylinder inscribed in the box: circular/elliptical cross-section in XZ (radius
      // per direction, independent), full height range on Y. Top + bottom rings plus 4 cardinal
      // verticals to read as a cage rather than two disconnected ellipses.
      const top = ellipseXZ(cx, box.maxY, cz, rx, rz);
      const bottom = ellipseXZ(cx, box.minY, cz, rx, rz);
      const verticals = [0, 1, 2, 3].map((quadrant) => {
        const i = Math.round((quadrant / 4) * RING_SEGMENTS);
        return B.MeshBuilder.CreateLines(
          `selectionCylinderVertical${quadrant}`,
          { points: [bottom[i], top[i]] },
          gizmoScene,
        );
      });
      for (const v of verticals) {
        v.color = new B.Color3(1, 1, 1);
        v.isPickable = false;
      }
      outlineMeshes = [ring(top), ring(bottom), ...verticals];
    }

    const L = AXIS_LINE_HALF_LENGTH;
    const axisSegments: [InstanceType<typeof BABYLON.Vector3>, InstanceType<typeof BABYLON.Vector3>][] = [
      [new B.Vector3(cx - L, cy, cz), new B.Vector3(cx + L, cy, cz)],
      [new B.Vector3(cx, cy - L, cz), new B.Vector3(cx, cy + L, cz)],
      [new B.Vector3(cx, cy, cz - L), new B.Vector3(cx, cy, cz + L)],
    ];
    const lineColors = [
      B.Color3.FromHexString(AXIS_HEX.x),
      B.Color3.FromHexString(AXIS_HEX.y),
      B.Color3.FromHexString(AXIS_HEX.z),
    ];
    const linesMesh = B.MeshBuilder.CreateLineSystem(
      "selectionAxisLines",
      { lines: axisSegments, colors: axisSegments.map((_, i) => [lineColors[i].toColor4(), lineColors[i].toColor4()]) },
      gizmoScene,
    );
    linesMesh.isPickable = false;
    axisLines = linesMesh;

    for (const { spec, anchor } of handles) {
      const [lo, hi] =
        spec.axis === "x" ? [box.minX, box.maxX] : spec.axis === "y" ? [box.minY, box.maxY] : [box.minZ, box.maxZ];
      const facePos = spec.sign === 1 ? hi + HANDLE_OFFSET : lo - HANDLE_OFFSET;
      anchor.position =
        spec.axis === "x"
          ? new B.Vector3(facePos, cy, cz)
          : spec.axis === "y"
            ? new B.Vector3(cx, facePos, cz)
            : new B.Vector3(cx, cy, facePos);
    }

    for (const { spec, mesh } of corners) {
      const x = (spec.sx === 1 ? box.maxX : box.minX) + CORNER_OFFSET * spec.sx;
      const y = (spec.sy === 1 ? box.maxY : box.minY) + CORNER_OFFSET * spec.sy;
      const z = (spec.sz === 1 ? box.maxZ : box.minZ) + CORNER_OFFSET * spec.sz;
      mesh.position = new B.Vector3(x, y, z);
    }

    centerCube!.position = new B.Vector3(cx, cy, cz);
    for (const { spec, anchor } of moveHandles) {
      const offset = MOVE_HANDLE_OFFSET * spec.sign;
      anchor.position =
        spec.axis === "x"
          ? new B.Vector3(cx + offset, cy, cz)
          : spec.axis === "y"
            ? new B.Vector3(cx, cy + offset, cz)
            : new B.Vector3(cx, cy, cz + offset);
    }

    renderSnapPreview(box);
  }

  function dispose() {
    disposeVisuals();
    for (const { gizmo, anchor } of handles) {
      gizmo.dispose();
      anchor.dispose();
    }
    handles.length = 0;
    for (const { mesh } of corners) mesh.dispose();
    corners.length = 0;
    for (const { gizmo, anchor } of moveHandles) {
      gizmo.dispose();
      anchor.dispose();
    }
    moveHandles.length = 0;
    centerCube?.dispose();
    centerCube = null;
    utilityLayer.dispose();
  }

  return {
    setSelection,
    setShape,
    setSnap,
    dispose,
  };
}
