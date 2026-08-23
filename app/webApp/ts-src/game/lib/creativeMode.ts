import { createOrbitCamera } from "../../admin/pages/shared/voxelEditor/orbitCamera";
import { saveCameraState } from "../../admin/pages/shared/voxelEditor/cameraStorage";
import { setupOrbitPointerController } from "../../admin/pages/shared/voxelEditor/orbitPointerController";
import { getAccountEmail } from "../../lib/authStorage";
import { showScenePreview, hideScenePreview, SceneGhostCell } from "./targeting/sceneGhost";

export interface CreativeSceneSummary {
  id: string;
  name: string;
  width: number;
  height: number;
  depth: number;
}

// Swaps the game's single shared Scene from its FPS UniversalCamera to an admin-style free orbit
// camera (pan/rotate/zoom, no player-collision) while `/mode creative` is active. The FPS camera's
// per-frame position interpolation (BabylonBindingsScene.kt's render loop) is itself guarded on
// `window.mcState.editMode !== "creative"`, so it stops fighting the orbit camera once swapped in.
let orbitCamera: InstanceType<typeof BABYLON.ArcRotateCamera> | null = null;
let previousCamera: InstanceType<typeof BABYLON.Camera> | null = null;
let teardownController: (() => void) | null = null;
let selectedItem: string | null = null;

// Scene-placement ghost state — mutually exclusive with `selectedItem` above.
let selectedScene: CreativeSceneSummary | null = null;
let sceneRotation = 0;
let scenePreviewCells: SceneGhostCell[] = [];
let lastGhostBase: { x: number; y: number; z: number } | null = null;

export function setCreativeSelectedItem(item: string | null): void {
  selectedItem = item;
  if (item) clearSceneSelection();
}

export function getCreativeSelectedItem(): string | null {
  return selectedItem;
}

function clearSceneSelection(): void {
  selectedScene = null;
  sceneRotation = 0;
  scenePreviewCells = [];
  lastGhostBase = null;
  window.mcState.sceneGhostActive = false;
  hideScenePreview();
}

export function setCreativeSelectedScene(scene: CreativeSceneSummary | null): void {
  clearSceneSelection();
  if (!scene) return;
  selectedItem = null;
  selectedScene = scene;
  window.mcState.sceneGhostActive = true;
  window.mcState.events.push(`scene_preview_request:${scene.id}`);
}

// Called from GameUI's window.mc.scenePreviewData handler once the server responds to the
// scene_preview_request pushed above. Ignores stale responses for a scene the player has since
// deselected.
export function setScenePreviewCells(sceneId: string, cells: SceneGhostCell[]): void {
  if (!selectedScene || selectedScene.id !== sceneId) return;
  scenePreviewCells = cells;
  rebuildGhostAtCursor();
}

function rotatedDims(): { width: number; depth: number } {
  if (!selectedScene) return { width: 0, depth: 0 };
  return sceneRotation % 2 === 0
    ? { width: selectedScene.width, depth: selectedScene.depth }
    : { width: selectedScene.depth, depth: selectedScene.width };
}

function updateGhostAt(bx: number, by: number, bz: number): void {
  if (!selectedScene) return;
  const { width, depth } = rotatedDims();
  const origin = { x: bx - Math.round(width / 2), y: by, z: bz - Math.round(depth / 2) };
  lastGhostBase = origin;
  const scene = window.mcState.engine?.scenes?.[0];
  if (!scene) return;
  showScenePreview(
    scene,
    scenePreviewCells,
    selectedScene.width,
    selectedScene.height,
    selectedScene.depth,
    sceneRotation,
    origin,
  );
}

// Re-picks whatever the cursor currently sits over (no fresh pointer-move event required) — used
// after a rotation or a scene_preview_request round trip, since the ghost geometry/orientation
// changed but the mouse may not have moved.
function rebuildGhostAtCursor(): void {
  if (!selectedScene) return;
  const scene = window.mcState.engine?.scenes?.[0];
  if (!scene) return;
  const pick = scene.pick(scene.pointerX, scene.pointerY);
  if (!pick?.hit || !pick.pickedPoint) return;
  const n = pick.getNormal(true) ?? new BABYLON.Vector3(0, 0, 0);
  const bx = Math.floor(pick.pickedPoint.x + n.x * 0.5);
  const by = Math.floor(pick.pickedPoint.y + n.y * 0.5);
  const bz = Math.floor(pick.pickedPoint.z + n.z * 0.5);
  updateGhostAt(bx, by, bz);
}

export function sceneRotate(): void {
  if (!selectedScene) return;
  sceneRotation = (sceneRotation + 1) % 4;
  rebuildGhostAtCursor();
}

export function sceneConfirm(): void {
  if (!selectedScene || !lastGhostBase) return;
  const { id } = selectedScene;
  const { x, y, z } = lastGhostBase;
  window.mcState.events.push(`cmd:/scene:place ${id} ${sceneRotation} ${x} ${y} ${z}`);
  setCreativeSelectedScene(null);
}

export function sceneCancel(): void {
  setCreativeSelectedScene(null);
}

function cameraStorageKey(): string {
  return `gameCreativeCamera:${getAccountEmail()}`;
}

export function enterCreativeMode(): void {
  const scene = window.mcState.engine?.scenes?.[0];
  const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
  if (!scene || !canvas) return;

  // The server re-sends EditModeUpdate("creative") on every /mode command and on reconnect, even
  // when already in creative — without this guard each redundant call would leak another
  // onPointerObservable listener (never torn down), each independently reacting to hover/click.
  if (teardownController) {
    teardownController();
    teardownController = null;
  }

  document.exitPointerLock();
  // The FPS crosshair is meaningless with the free-look orbit camera (targeting is mouse-pick
  // based, not screen-center based) — hide it so it doesn't suggest aiming that doesn't apply.
  const crosshair = document.getElementById("mc-crosshair");
  if (crosshair) crosshair.style.display = "none";

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
    getMode: () => (selectedScene ? "select" : selectedItem ? "place" : "break"),
    onHoverMove: () => {
      const pick = scene.pick(scene.pointerX, scene.pointerY);
      if (!pick?.hit || !pick.pickedPoint) {
        window.mc.hideTargetOutline();
        hideScenePreview();
        lastGhostBase = null;
        return;
      }
      const n = pick.getNormal(true) ?? new BABYLON.Vector3(0, 0, 0);
      if (selectedScene) {
        window.mc.hideTargetOutline();
        const bx = Math.floor(pick.pickedPoint.x + n.x * 0.5);
        const by = Math.floor(pick.pickedPoint.y + n.y * 0.5);
        const bz = Math.floor(pick.pickedPoint.z + n.z * 0.5);
        updateGhostAt(bx, by, bz);
        return;
      }
      // Basic block palette / break mode: highlight whatever block the mouse currently sits over,
      // same wireframe outline the FPS raycast uses (mirrors showTargetOutline's hover shape) —
      // orbit-camera creative mode has no crosshair-driven raycast, so this is the only feedback.
      const sign = selectedItem ? 0.5 : -0.5;
      const bx = Math.floor(pick.pickedPoint.x + n.x * sign);
      const by = Math.floor(pick.pickedPoint.y + n.y * sign);
      const bz = Math.floor(pick.pickedPoint.z + n.z * sign);
      window.mc.showTargetOutline(scene, bx, by, bz, !selectedItem);
    },
    continuousBreak: window.mcState.continuousBreak,
    onClick: ({ pick, normal, mode }) => {
      if (mode === "select") return; // scene placement confirms via Enter, not click
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
  clearSceneSelection();
  window.mc.hideTargetOutline();
  const crosshair = document.getElementById("mc-crosshair");
  if (crosshair) crosshair.style.display = "";
  orbitCamera?.detachControl();
  orbitCamera = null;
  if (scene && previousCamera) {
    scene.activeCamera = previousCamera;
    const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
    if (canvas) previousCamera.attachControl(canvas, true);
  }
  previousCamera = null;
}
