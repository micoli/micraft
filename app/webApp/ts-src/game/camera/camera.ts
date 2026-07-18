import type { Camera, Scene, TargetCamera } from "@babylonjs/core";

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

export function registerCamera(): Pick<
  McBindings,
  | "getCameraPositionX"
  | "getCameraPositionY"
  | "getCameraPositionZ"
  | "getCameraDir3DX"
  | "getCameraDir3DY"
  | "getCameraDir3DZ"
  | "getCameraForwardX"
  | "getCameraForwardZ"
  | "createCrosshair"
  | "setupDebugCameraKeys"
> {
  return {
    getCameraPositionX: (camera: Camera): number => camera.position.x,
    getCameraPositionY: (camera: Camera): number => camera.position.y,
    getCameraPositionZ: (camera: Camera): number => camera.position.z,

    getCameraDir3DX: (camera: Camera): number => getCachedDir(camera).x,
    getCameraDir3DY: (camera: Camera): number => getCachedDir(camera).y,
    getCameraDir3DZ: (camera: Camera): number => getCachedDir(camera).z,

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

    /**
     * Binds keys 1-6 to camera positions facing each face of the block at (bx,by,bz).
     * Face mapping: 1=+Z, 2=-Z, 3=+X, 4=-X, 5=+Y, 6=-Y  (BabylonJS CreateBox order)
     */
    setupDebugCameraKeys: (camera: TargetCamera, scene: Scene, bx: number, by: number, bz: number): void => {
      const dist = 5;
      const faces: [number, number, number][] = [
        [bx, by, bz + dist],
        [bx, by, bz - dist],
        [bx + dist, by, bz],
        [bx - dist, by, bz],
        [bx, by + dist, bz],
        [bx, by - dist, bz],
      ];

      const lock = (px: number, py: number, pz: number): void => {
        if (window.mcState.debugCamObserver) scene.onBeforeRenderObservable.remove(window.mcState.debugCamObserver);
        window.mcState.debugCamObserver = scene.onBeforeRenderObservable.add(() => {
          camera.position = new BABYLON.Vector3(px, py, pz);
          camera.setTarget(new BABYLON.Vector3(bx, by, bz));
        });
      };

      lock(...faces[0]);

      document.addEventListener("keydown", (e: KeyboardEvent) => {
        const idx = parseInt(e.key) - 1;
        if (idx >= 0 && idx < 6) {
          e.preventDefault();
          lock(...faces[idx]);
        }
        if (e.key === "Escape") {
          if (window.mcState.debugCamObserver) {
            scene.onBeforeRenderObservable.remove(window.mcState.debugCamObserver);
            window.mcState.debugCamObserver = null;
          }
        }
      });
    },
  };
}
