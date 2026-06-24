import type { Scene } from "@babylonjs/core";

// 12 edges of a unit cube centred at origin, expanded by `h` on each side.
function cubeLines(h: number): [unknown, unknown][] {
  const V = (x: number, y: number, z: number) => new BABYLON.Vector3(x, y, z);
  return [
    [V(-h, -h, -h), V(h, -h, -h)],
    [V(h, -h, -h), V(h, -h, h)],
    [V(h, -h, h), V(-h, -h, h)],
    [V(-h, -h, h), V(-h, -h, -h)],
    [V(-h, h, -h), V(h, h, -h)],
    [V(h, h, -h), V(h, h, h)],
    [V(h, h, h), V(-h, h, h)],
    [V(-h, h, h), V(-h, h, -h)],
    [V(-h, -h, -h), V(-h, h, -h)],
    [V(h, -h, -h), V(h, h, -h)],
    [V(h, -h, h), V(h, h, h)],
    [V(-h, -h, h), V(-h, h, h)],
  ];
}

export function registerTargeting(): void {
  window.mcShowTargetOutline = (scene: Scene, x: number, y: number, z: number, breakable: boolean): void => {
    if (window.__mcTargetMesh) {
      window.__mcTargetMesh.dispose();
      window.__mcTargetMesh = null;
    }
    const ls = BABYLON.MeshBuilder.CreateLineSystem("targetOutline", { lines: cubeLines(0.502) as any }, scene);
    ls.position = new BABYLON.Vector3(x, y, z);
    (ls as any).color = breakable ? new BABYLON.Color3(0, 0, 0) : new BABYLON.Color3(0.55, 0.55, 0.55);
    ls.isPickable = false;
    window.__mcTargetMesh = ls;
  };

  window.mcHideTargetOutline = (): void => {
    if (window.__mcTargetMesh) {
      window.__mcTargetMesh.dispose();
      window.__mcTargetMesh = null;
    }
  };

  window.mcShowBreakOverlay = (scene: Scene, x: number, y: number, z: number, alpha: number): void => {
    const bpos = `${x},${y},${z}`;
    if (!window.__mcBreakMesh || window.__mcBreakMesh._bpos !== bpos) {
      if (window.__mcBreakMesh) window.__mcBreakMesh.dispose();
      const ls = BABYLON.MeshBuilder.CreateLineSystem("breakOverlay", { lines: cubeLines(0.51) as any }, scene);
      ls.position = new BABYLON.Vector3(x, y, z);
      (ls as any).color = new BABYLON.Color3(0, 0, 0);
      ls.isPickable = false;
      window.__mcBreakMesh = ls as any;
      window.__mcBreakMesh!._bpos = bpos;
    }
    window.__mcBreakMesh!.visibility = alpha;
  };

  window.mcHideBreakOverlay = (): void => {
    if (window.__mcBreakMesh) {
      window.__mcBreakMesh.dispose();
      window.__mcBreakMesh = null;
    }
  };
}
