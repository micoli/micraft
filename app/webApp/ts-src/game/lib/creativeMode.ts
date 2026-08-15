import { createOrbitCamera } from "../../admin/pages/shared/voxelEditor/orbitCamera";
import { saveCameraState } from "../../admin/pages/shared/voxelEditor/cameraStorage";
import { setupOrbitPointerController } from "../../admin/pages/shared/voxelEditor/orbitPointerController";
import { getAccountEmail } from "../../lib/authStorage";

// Swaps the game's single shared Scene from its FPS UniversalCamera to an admin-style free orbit
// camera (pan/rotate/zoom, no player-collision) while `/mode creative` is active. The FPS camera's
// per-frame position interpolation (BabylonBindingsScene.kt's render loop) is itself guarded on
// `window.mcState.editMode !== "creative"`, so it stops fighting the orbit camera once swapped in.
let orbitCamera: InstanceType<typeof BABYLON.ArcRotateCamera> | null = null;
let previousCamera: InstanceType<typeof BABYLON.Camera> | null = null;
let teardownController: (() => void) | null = null;
let selectedItem: string | null = null;

export function setCreativeSelectedItem(item: string | null): void {
  selectedItem = item;
}

export function getCreativeSelectedItem(): string | null {
  return selectedItem;
}

function cameraStorageKey(): string {
  return `gameCreativeCamera:${getAccountEmail()}`;
}

export function enterCreativeMode(): void {
  const scene = window.mcState.engine?.scenes?.[0];
  const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
  if (!scene || !canvas) return;

  document.exitPointerLock();

  previousCamera = scene.activeCamera;
  previousCamera?.detachControl();

  const camState = window.mcState.camState;
  const center = camState ? { x: camState.x1, y: camState.y1, z: camState.z1 } : { x: 8, y: 10, z: 8 };
  orbitCamera = createOrbitCamera(BABYLON, scene, canvas, cameraStorageKey(), center, 10, false);
  scene.activeCamera = orbitCamera;

  let lastSave = 0;
  orbitCamera.onViewMatrixChangedObservable.add(() => {
    const now = performance.now();
    if (now - lastSave < 300 || !orbitCamera) return;
    lastSave = now;
    saveCameraState(cameraStorageKey(), {
      alpha: orbitCamera.alpha,
      beta: orbitCamera.beta,
      radius: orbitCamera.radius,
      targetX: orbitCamera.target.x,
      targetY: orbitCamera.target.y,
      targetZ: orbitCamera.target.z,
    });
    window.mcState.events.push(`creative_focus:${orbitCamera.target.x},${orbitCamera.target.z}`);
  });
  window.mcState.events.push(`creative_focus:${orbitCamera.target.x},${orbitCamera.target.z}`);

  teardownController = setupOrbitPointerController({
    B: BABYLON,
    scene,
    camera: orbitCamera,
    canvas,
    getMode: () => (selectedItem ? "place" : "break"),
    onHoverMove: () => {},
    continuousBreak: window.mcState.continuousBreak,
    onClick: ({ pick, normal, mode }) => {
      const hit = pick.pickedPoint;
      if (!hit) return;
      const n = normal ?? new BABYLON.Vector3(0, 0, 0);
      const sign = mode === "place" ? 0.5 : -0.5;
      const bx = Math.floor(hit.x + n.x * sign);
      const by = Math.floor(hit.y + n.y * sign);
      const bz = Math.floor(hit.z + n.z * sign);
      if (mode === "place") {
        if (!selectedItem) return;
        window.mcState.events.push(`creative_place:${bx},${by},${bz},${selectedItem},0`);
      } else {
        window.mcState.events.push(`creative_break:${bx},${by},${bz}`);
      }
    },
  });
}

export function exitCreativeMode(): void {
  const scene = window.mcState.engine?.scenes?.[0];
  if (teardownController) {
    teardownController();
    teardownController = null;
  }
  orbitCamera?.detachControl();
  orbitCamera = null;
  if (scene && previousCamera) {
    scene.activeCamera = previousCamera;
    const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
    if (canvas) previousCamera.attachControl(canvas, true);
  }
  previousCamera = null;
}
