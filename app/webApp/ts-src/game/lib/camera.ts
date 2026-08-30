import type { Camera } from "@babylonjs/core";

// Cache the forward direction for the current JS task — all Dir3D/Forward calls
// within a single Kotlin tick are synchronous, so one getForwardRay() suffices.
let _dirCacheCamera: Camera | null = null;
let _dirX = 0,
  _dirY = 0,
  _dirZ = 0;

function getCachedDir(camera: Camera): { x: number; y: number; z: number } {
  if (_dirCacheCamera !== camera) {
    const d = camera.getForwardRay(1).direction;
    _dirX = d.x;
    _dirY = d.y;
    _dirZ = d.z;
    _dirCacheCamera = camera;
    queueMicrotask(() => {
      _dirCacheCamera = null;
    });
  }
  return { x: _dirX, y: _dirY, z: _dirZ };
}

// Same per-tick caching as getCachedDir, for the ray from the camera through the current mouse
// cursor — used by THIRD_PERSON_ORBIT_CURSOR block picking (no pointer lock, no screen-center aim).
let _cursorCacheCamera: Camera | null = null;
let _curX = 0,
  _curY = 0,
  _curZ = 0;

function getCachedCursorRay(camera: Camera): { x: number; y: number; z: number } {
  if (_cursorCacheCamera !== camera) {
    const scene = camera.getScene();
    const ray = scene.createPickingRay(scene.pointerX, scene.pointerY, BABYLON.Matrix.Identity(), camera);
    _curX = ray.direction.x;
    _curY = ray.direction.y;
    _curZ = ray.direction.z;
    _cursorCacheCamera = camera;
    queueMicrotask(() => {
      _cursorCacheCamera = null;
    });
  }
  return { x: _curX, y: _curY, z: _curZ };
}

export function registerCamera(): Pick<
  McBindings,
  | "getCameraPositionX"
  | "getCameraPositionY"
  | "getCameraPositionZ"
  | "getCameraDir3DX"
  | "getCameraDir3DY"
  | "getCameraDir3DZ"
  | "getCursorRayX"
  | "getCursorRayY"
  | "getCursorRayZ"
  | "getCameraForwardX"
  | "getCameraForwardZ"
  | "createCrosshair"
> {
  return {
    getCameraPositionX: (camera: Camera): number => camera.position.x,
    getCameraPositionY: (camera: Camera): number => camera.position.y,
    getCameraPositionZ: (camera: Camera): number => camera.position.z,

    getCameraDir3DX: (camera: Camera): number => getCachedDir(camera).x,
    getCameraDir3DY: (camera: Camera): number => getCachedDir(camera).y,
    getCameraDir3DZ: (camera: Camera): number => getCachedDir(camera).z,

    getCursorRayX: (camera: Camera): number => getCachedCursorRay(camera).x,
    getCursorRayY: (camera: Camera): number => getCachedCursorRay(camera).y,
    getCursorRayZ: (camera: Camera): number => getCachedCursorRay(camera).z,

    getCameraForwardX: (camera: Camera): number => {
      const d = getCachedDir(camera);
      const l = Math.sqrt(d.x * d.x + d.z * d.z) || 1;
      return d.x / l;
    },

    getCameraForwardZ: (camera: Camera): number => {
      const d = getCachedDir(camera);
      const l = Math.sqrt(d.x * d.x + d.z * d.z) || 1;
      return d.z / l;
    },

    createCrosshair: (): void => {
      const s = document.createElement("div");
      s.id = "mc-crosshair";
      s.style.cssText =
        "position:fixed;top:50%;left:50%;width:20px;height:20px;" +
        "transform:translate(-50%,-50%);pointer-events:none;z-index:100";
      s.innerHTML =
        '<div style="position:absolute;left:50%;top:0;width:2px;height:100%;' +
        'background:#fff;opacity:0.8;transform:translateX(-50%)"></div>' +
        '<div style="position:absolute;top:50%;left:0;height:2px;width:100%;' +
        'background:#fff;opacity:0.8;transform:translateY(-50%)"></div>';
      document.body.appendChild(s);
    },
  };
}
