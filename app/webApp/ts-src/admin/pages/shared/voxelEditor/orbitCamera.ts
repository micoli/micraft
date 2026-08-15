import { loadCameraState } from "./cameraStorage";

export function setupBasicLighting(B: typeof BABYLON, scene: InstanceType<typeof BABYLON.Scene>) {
  const light = new B.HemisphericLight("light", new B.Vector3(0.5, 1, 0.3), scene);
  light.intensity = 1.0;
  const sun = new B.DirectionalLight("sun", new B.Vector3(-0.3, -1, -0.2), scene);
  sun.intensity = 0.6;
}

// Orbit camera restored from localStorage if a previous session left one (see cameraStorage.ts),
// otherwise centered on/framing the given volume. Default pointer input (left-drag rotate,
// right-drag pan) is removed — replaced by the modifier-key driven scheme wired up by
// orbitPointerController.ts (none=place/break, Cmd=rotate, Option=pan, Ctrl=zoom).
export function createOrbitCamera(
  B: typeof BABYLON,
  scene: InstanceType<typeof BABYLON.Scene>,
  canvas: HTMLCanvasElement,
  cameraStorageKey: string,
  center: { x: number; y: number; z: number },
  span: number,
  keepSavedTarget = true,
): InstanceType<typeof BABYLON.ArcRotateCamera> {
  const savedCamera = loadCameraState(cameraStorageKey);
  const camera = new B.ArcRotateCamera(
    "cam",
    savedCamera?.alpha ?? -Math.PI * 0.35,
    savedCamera?.beta ?? Math.PI / 3,
    savedCamera?.radius ?? span * 1.8,
    savedCamera && keepSavedTarget
      ? new B.Vector3(savedCamera.targetX, savedCamera.targetY, savedCamera.targetZ)
      : new B.Vector3(center.x, center.y, center.z),
    scene,
  );
  camera.attachControl(canvas, true);
  camera.inputs.removeByType("ArcRotateCameraPointersInput");
  camera.wheelPrecision = 20;
  camera.lowerRadiusLimit = 2;
  return camera;
}
