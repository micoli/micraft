export type ClipAxis = "x" | "y" | "z";
export interface ClipPlaneState {
  enabled: boolean;
  flipped: boolean;
  pos: number;
}
export const CLIP_AXES = ["x", "y", "z"] as const;
export const CLIP_COLORS: Record<ClipAxis, [number, number, number]> = {
  x: [0.85, 0.2, 0.2],
  y: [0.2, 0.75, 0.25],
  z: [0.2, 0.4, 0.9],
};

// Updates the clip-plane uniforms on every live block ShaderMaterial (window.mcState.blockMaterials)
// and (re)builds the translucent guide rectangle per active axis. Called both reactively (sidebar
// toggles) and after each chunk (re)load, since block materials are created lazily by
// ChunkManager.getBlockMaterials() on first chunk mesh and may not exist yet at toggle time.
export function applyClipPlanes(
  B: typeof BABYLON,
  scene: InstanceType<typeof BABYLON.Scene>,
  clipPlanes: Record<ClipAxis, ClipPlaneState>,
  clipBounds: Record<ClipAxis, readonly [number, number]>,
  overlayMeshes: Set<InstanceType<typeof BABYLON.Mesh>>,
  clipMeshes: Partial<Record<ClipAxis, InstanceType<typeof BABYLON.Mesh>>>,
) {
  const midX = (clipBounds.x[0] + clipBounds.x[1]) / 2;
  const midY = (clipBounds.y[0] + clipBounds.y[1]) / 2;
  const midZ = (clipBounds.z[0] + clipBounds.z[1]) / 2;
  const mats = window.mcState?.blockMaterials;

  for (const axis of CLIP_AXES) {
    const cp = clipPlanes[axis];
    // Disabled → zero normal (never discards) regardless of flip/pos; the -1 offset is just belt-and-braces.
    const sign = cp.flipped ? -1 : 1;
    const nx = cp.enabled && axis === "x" ? sign : 0;
    const ny = cp.enabled && axis === "y" ? sign : 0;
    const nz = cp.enabled && axis === "z" ? sign : 0;
    const d = cp.enabled ? -(sign * cp.pos) : -1;
    const value = new B.Vector4(nx, ny, nz, d);
    if (mats) {
      const uniformName = `clipPlane${axis.toUpperCase()}` as "clipPlaneX" | "clipPlaneY" | "clipPlaneZ";
      for (const m of Object.values(mats)) if (m instanceof B.ShaderMaterial) m.setVector4(uniformName, value);
    }

    const existing = clipMeshes[axis];
    if (existing) {
      overlayMeshes.delete(existing);
      existing.dispose();
      delete clipMeshes[axis];
    }
    if (!cp.enabled) continue;

    let width: number;
    let height: number;
    if (axis === "x") {
      width = clipBounds.z[1] - clipBounds.z[0];
      height = clipBounds.y[1] - clipBounds.y[0];
    } else if (axis === "y") {
      width = clipBounds.x[1] - clipBounds.x[0];
      height = clipBounds.z[1] - clipBounds.z[0];
    } else {
      width = clipBounds.x[1] - clipBounds.x[0];
      height = clipBounds.y[1] - clipBounds.y[0];
    }

    const mesh = B.MeshBuilder.CreatePlane(
      `clipPlane-${axis}`,
      { width, height, sideOrientation: B.Mesh.DOUBLESIDE },
      scene,
    );
    if (axis === "x") {
      mesh.rotation.y = Math.PI / 2;
      mesh.position.set(cp.pos, midY, midZ);
    } else if (axis === "y") {
      mesh.rotation.x = Math.PI / 2;
      mesh.position.set(midX, cp.pos, midZ);
    } else {
      mesh.position.set(midX, midY, cp.pos);
    }
    const mat = new B.StandardMaterial(`clipPlaneMat-${axis}`, scene);
    const [r, g, b] = CLIP_COLORS[axis];
    mat.diffuseColor = new B.Color3(r, g, b);
    mat.emissiveColor = new B.Color3(r * 0.5, g * 0.5, b * 0.5);
    mat.alpha = 0.05;
    mat.backFaceCulling = false;
    mesh.material = mat;
    mesh.isPickable = false;
    overlayMeshes.add(mesh);
    clipMeshes[axis] = mesh;
  }
}
