// Editor-only "test cart" — a placeholder mesh that rides a rail circuit the user just built, so
// switches/loops/dead-ends can be checked visually without leaving the editor. The actual
// traversal (topology walk, switch branch selection) runs server-side in the wasm module (see
// AdminChunkPreview.kt's mcAdminRailTest*/AdminScenePreview.kt's mcSceneRailTest* trio) — this
// module only owns the placeholder mesh and the per-frame pose polling, parameterized by
// start/tick/stop callbacks so it works unmodified against either editor's wasm binding shape.

const CART_SIZE = 0.6;
const NOSE_SIZE = 0.25;

export interface RailTestCart {
  isActive(): boolean;
  // Attempts to start the test at (wx,wy,wz); returns false (no-op) if that cell isn't usable.
  start(wx: number, wy: number, wz: number): boolean;
  stop(): void;
  dispose(): void;
}

export function createRailTestCart(
  B: typeof BABYLON,
  scene: InstanceType<typeof BABYLON.Scene>,
  startFn: (wx: number, wy: number, wz: number) => number,
  tickFn: (deltaSeconds: number) => string,
  stopFn: () => void,
): RailTestCart {
  let mesh: InstanceType<typeof BABYLON.Mesh> | null = null;
  let active = false;

  function ensureMesh(): InstanceType<typeof BABYLON.Mesh> {
    if (mesh) return mesh;
    const body = B.MeshBuilder.CreateBox("railTestCart", { size: CART_SIZE }, scene);
    const mat = new B.StandardMaterial("railTestCartMat", scene);
    mat.diffuseColor = new B.Color3(1, 0.55, 0.1);
    mat.specularColor = B.Color3.Black();
    body.material = mat;
    body.isPickable = false;
    // Small nose box on the +Z local face marks the travel direction — mesh.rotation.y (set from
    // the wasm-reported yaw each tick) then carries it to whichever way the cart is actually
    // heading, same convention as VehicleBehavior.updatePose's atan2(dx, dz).
    const nose = B.MeshBuilder.CreateBox("railTestCartNose", { size: NOSE_SIZE }, scene);
    const noseMat = new B.StandardMaterial("railTestCartNoseMat", scene);
    noseMat.diffuseColor = new B.Color3(1, 1, 1);
    noseMat.specularColor = B.Color3.Black();
    nose.material = noseMat;
    nose.isPickable = false;
    nose.parent = body;
    nose.position = new B.Vector3(0, 0, CART_SIZE / 2);
    mesh = body;
    return body;
  }

  function disposeMesh() {
    mesh?.dispose();
    mesh = null;
  }

  scene.onBeforeRenderObservable.add(() => {
    if (!active) return;
    const deltaSeconds = scene.getEngine().getDeltaTime() / 1000;
    const pose = tickFn(deltaSeconds);
    if (!pose) {
      active = false;
      disposeMesh();
      return;
    }
    const [x, y, z, yaw, pitch] = pose.split(",").map(Number);
    const m = ensureMesh();
    m.position.set(x, y, z);
    // Babylon's default Euler order (YXZ) applies rotation.y before rotation.x, so pitch tilts
    // around the cart's own already-yawed forward axis instead of the world X axis — same
    // convention as vehicleModel.ts's setVehicleTransform.
    m.rotation.y = yaw;
    m.rotation.x = pitch;
  });

  return {
    isActive: () => active,
    start(wx, wy, wz) {
      const ok = startFn(wx, wy, wz) === 1;
      if (!ok) return false;
      ensureMesh();
      active = true;
      return true;
    },
    stop() {
      stopFn();
      active = false;
      disposeMesh();
    },
    dispose() {
      if (active) stopFn();
      active = false;
      disposeMesh();
    },
  };
}
