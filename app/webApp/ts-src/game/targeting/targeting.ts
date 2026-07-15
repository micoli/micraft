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

export function registerTargeting(): Pick<
  McBindings,
  "showTargetOutline" | "hideTargetOutline" | "showBreakOverlay" | "hideBreakOverlay"
> {
  return {
    showTargetOutline: (scene: Scene, x: number, y: number, z: number, breakable: boolean): void => {
      if (window.mcState.targetMesh) {
        window.mcState.targetMesh.dispose();
        window.mcState.targetMesh = null;
      }
      const ls = BABYLON.MeshBuilder.CreateLineSystem("targetOutline", { lines: cubeLines(0.502) as any }, scene);
      ls.position = new BABYLON.Vector3(x + 0.5, y + 0.5, z + 0.5);
      (ls as any).color = breakable ? new BABYLON.Color3(0, 0, 0) : new BABYLON.Color3(0.55, 0.55, 0.55);
      ls.isPickable = false;
      window.mcState.targetMesh = ls;
    },

    hideTargetOutline: (): void => {
      if (window.mcState.targetMesh) {
        window.mcState.targetMesh.dispose();
        window.mcState.targetMesh = null;
      }
    },

    showBreakOverlay: (scene: Scene, x: number, y: number, z: number, alpha: number): void => {
      const bpos = `${x},${y},${z}`;
      if (!window.mcState.breakMesh || window.mcState.breakMesh._bpos !== bpos) {
        if (window.mcState.breakMesh) window.mcState.breakMesh.dispose();
        const ls = BABYLON.MeshBuilder.CreateLineSystem("breakOverlay", { lines: cubeLines(0.51) as any }, scene);
        ls.position = new BABYLON.Vector3(x + 0.5, y + 0.5, z + 0.5);
        (ls as any).color = new BABYLON.Color3(0, 0, 0);
        ls.isPickable = false;
        window.mcState.breakMesh = ls as any;
        window.mcState.breakMesh!._bpos = bpos;
      }
      window.mcState.breakMesh!.visibility = alpha;
    },

    hideBreakOverlay: (): void => {
      if (window.mcState.breakMesh) {
        window.mcState.breakMesh.dispose();
        window.mcState.breakMesh = null;
      }
    },
  };
}
