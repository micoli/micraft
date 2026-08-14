// Move-only gizmo for positioning a Paste preview: a center cube + 6 fixed-offset axis-drag
// handles (same plate-mesh look as selectionGizmo.ts's move handles), but translating a single
// point rather than resizing a box — paste has no shape/snap concerns, just "where do the cut
// blocks go". Snaps to whole voxels (paste always writes to integer block coords), unlike the
// selection gizmo's quarter-voxel drag snap.

export interface PasteOrigin {
  x: number;
  y: number;
  z: number;
}

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

// Same per-axis colors as selectionGizmo.ts/axesGizmo.ts (X/Y/Z = red/green/blue).
const AXIS_HEX = { x: "#e5484d", y: "#46a758", z: "#3d63dd" };
// See selectionGizmo.ts's plateDimsForAxis doc comment — setCustomMesh skips AxisDragGizmo's own
// 1/3 scale-down on the stock arrow mesh, so the plate is sized down here to compensate.
const PLATE_WIDE = (1 / 3) * 0.05;
const PLATE_THIN = (1 / 8) * 0.05;
function plateDimsForAxis(axis: Axis): { width: number; height: number; depth: number } {
  return axis === "x"
    ? { width: PLATE_THIN, height: PLATE_WIDE, depth: PLATE_WIDE }
    : axis === "y"
      ? { width: PLATE_WIDE, height: PLATE_THIN, depth: PLATE_WIDE }
      : { width: PLATE_WIDE, height: PLATE_WIDE, depth: PLATE_THIN };
}
const CENTER_CUBE_SIZE = 0.3;
const HANDLE_OFFSET = CENTER_CUBE_SIZE / 2 + 0.1;
const CENTER_CUBE_COLOR = "#cccccc";

export function createPasteGizmo(
  B: typeof BABYLON,
  scene: InstanceType<typeof BABYLON.Scene>,
  onOriginChange: (origin: PasteOrigin) => void,
) {
  const utilityLayer = new B.UtilityLayerRenderer(scene);
  const gizmoScene = utilityLayer.utilityLayerScene;

  let current: PasteOrigin | null = null;
  const handles: {
    spec: HandleSpec;
    anchor: InstanceType<typeof BABYLON.TransformNode>;
    gizmo: InstanceType<typeof BABYLON.AxisDragGizmo>;
    attached: boolean;
  }[] = [];
  let centerCube: InstanceType<typeof BABYLON.Mesh> | null = null;

  function handleMat(hex: string): InstanceType<typeof BABYLON.StandardMaterial> {
    utilityLayer._getSharedGizmoLight();
    const mat = new B.StandardMaterial(`pasteHandleMat${hex}`, gizmoScene);
    const color = B.Color3.FromHexString(hex);
    mat.diffuseColor = color;
    mat.emissiveColor = color.scale(0.35);
    mat.specularColor = B.Color3.Black();
    return mat;
  }

  function translateAxis(origin: PasteOrigin, axis: Axis, voxelDelta: number): PasteOrigin {
    return { ...origin, [axis]: origin[axis] + voxelDelta };
  }

  for (const spec of HANDLES) {
    const anchor = new B.TransformNode(`pasteHandleAnchor${spec.axis}${spec.sign}`, gizmoScene);
    const dragAxis =
      spec.axis === "x" ? new B.Vector3(1, 0, 0) : spec.axis === "y" ? new B.Vector3(0, 1, 0) : new B.Vector3(0, 0, 1);
    const gizmo = new B.AxisDragGizmo(dragAxis, B.Color3.FromHexString(AXIS_HEX[spec.axis]), utilityLayer);
    gizmo.updateGizmoRotationToMatchAttachedMesh = false;
    gizmo.dragBehavior.moveAttached = false;
    const handleMesh = B.MeshBuilder.CreateBox(
      `pasteHandle${spec.axis}${spec.sign}`,
      plateDimsForAxis(spec.axis),
      gizmoScene,
    );
    handleMesh.material = handleMat(AXIS_HEX[spec.axis]);
    gizmo.setCustomMesh(handleMesh);
    let dragStart: PasteOrigin | null = null;
    let dragTotal = 0;
    gizmo.dragBehavior.onDragStartObservable.add(() => {
      dragStart = current;
      dragTotal = 0;
    });
    gizmo.dragBehavior.onDragObservable.add((event) => {
      if (!dragStart) return;
      // See selectionGizmo.ts's onDragObservable comment — dragDistance is incremental, must
      // accumulate before snapping or tiny per-frame deltas round to 0.
      dragTotal += event.dragDistance;
      const voxelDelta = Math.round(dragTotal);
      const next = translateAxis(dragStart, spec.axis, voxelDelta);
      if (current && current.x === next.x && current.y === next.y && current.z === next.z) return;
      current = next;
      render(next);
      onOriginChange(next);
    });
    gizmo.dragBehavior.onDragEndObservable.add(() => {
      dragStart = null;
    });
    handles.push({ spec, anchor, gizmo, attached: false });
  }
  centerCube = B.MeshBuilder.CreateBox("pasteCenterCube", { size: CENTER_CUBE_SIZE }, gizmoScene);
  centerCube.material = handleMat(CENTER_CUBE_COLOR);
  centerCube.isPickable = false;
  centerCube.setEnabled(false);

  function render(origin: PasteOrigin) {
    centerCube!.position = new B.Vector3(origin.x, origin.y, origin.z);
    for (const { spec, anchor } of handles) {
      const offset = HANDLE_OFFSET * spec.sign;
      anchor.position =
        spec.axis === "x"
          ? new B.Vector3(origin.x + offset, origin.y, origin.z)
          : spec.axis === "y"
            ? new B.Vector3(origin.x, origin.y + offset, origin.z)
            : new B.Vector3(origin.x, origin.y, origin.z + offset);
    }
  }

  // Only touches gizmo.attachedNode when visibility actually flips — see selectionGizmo.ts's
  // refreshHandleVisibility for why (AxisDragGizmo disables dragBehavior around every reassignment).
  function refreshVisibility() {
    const show = current !== null;
    for (const h of handles) {
      if (show !== h.attached) {
        h.gizmo.attachedNode = show ? h.anchor : null;
        h.attached = show;
      }
    }
    centerCube?.setEnabled(show);
  }

  function setOrigin(origin: PasteOrigin | null) {
    current = origin;
    refreshVisibility();
    if (origin) render(origin);
  }

  function dispose() {
    for (const { gizmo, anchor } of handles) {
      gizmo.dispose();
      anchor.dispose();
    }
    handles.length = 0;
    centerCube?.dispose();
    centerCube = null;
    utilityLayer.dispose();
  }

  return { setOrigin, dispose };
}
