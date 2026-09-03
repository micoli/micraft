import type { LinesMesh, Mesh, Scene } from "@babylonjs/core";

// Floating ★ marker above every named action block, plus a purple wireframe box on the
// currently targeted one. Mirrors the billboard pattern in playerModel.ts:setPlayerNameplate.

type IconEntry = { plane: Mesh; texture: { dispose(): void } };

const icons = new Map<string, IconEntry>();
let highlightMesh: LinesMesh | null = null;

function starTexture(scene: Scene) {
  const S = 128;
  const dt = new BABYLON.DynamicTexture("actionBlockStarTex", { width: S, height: S }, scene, false);
  dt.hasAlpha = true;
  const ctx = dt.getContext() as unknown as CanvasRenderingContext2D;
  ctx.clearRect(0, 0, S, S);
  ctx.font = "96px sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillStyle = "#ffd23f";
  ctx.strokeStyle = "rgba(0,0,0,0.85)";
  ctx.lineWidth = 6;
  ctx.strokeText("★", S / 2, S / 2 + 6);
  ctx.fillText("★", S / 2, S / 2 + 6);
  dt.update();
  return dt;
}

export function registerActionBlockIcons(): Pick<
  McBindings,
  "addActionBlockIcon" | "removeActionBlockIcon" | "clearActionBlockIcons" | "setActionBlockHighlight"
> {
  return {
    addActionBlockIcon: (scene: Scene, id: string, x: number, y: number, z: number): void => {
      icons.get(id)?.plane.dispose();
      icons.get(id)?.texture.dispose();
      const dt = starTexture(scene);
      const plane = BABYLON.MeshBuilder.CreatePlane("actionBlockStar", { size: 0.5 }, scene);
      plane.position.set(x + 0.5, y + 1.4, z + 0.5);
      plane.billboardMode = BABYLON.Mesh.BILLBOARDMODE_ALL;
      plane.isPickable = false;
      plane.renderingGroupId = 1;
      const mat = new BABYLON.StandardMaterial("actionBlockStarMat", scene);
      mat.diffuseTexture = dt;
      mat.opacityTexture = dt;
      mat.emissiveColor = new BABYLON.Color3(1, 1, 1);
      mat.disableLighting = true;
      mat.backFaceCulling = false;
      plane.material = mat;
      icons.set(id, { plane, texture: dt });
    },

    removeActionBlockIcon: (id: string): void => {
      const e = icons.get(id);
      if (!e) return;
      e.plane.dispose();
      e.texture.dispose();
      icons.delete(id);
    },

    clearActionBlockIcons: (): void => {
      icons.forEach((e) => {
        e.plane.dispose();
        e.texture.dispose();
      });
      icons.clear();
    },

    setActionBlockHighlight: (scene: Scene, x: number | null, y: number | null, z: number | null): void => {
      if (highlightMesh) {
        highlightMesh.dispose();
        highlightMesh = null;
      }
      if (x === null || y === null || z === null) return;
      const h = 0.52;
      const V = (dx: number, dy: number, dz: number) => new BABYLON.Vector3(x + 0.5 + dx, y + 0.5 + dy, z + 0.5 + dz);
      const c = [-h, h];
      const lines: [InstanceType<typeof BABYLON.Vector3>, InstanceType<typeof BABYLON.Vector3>][] = [];
      for (const ax of c) for (const ay of c) lines.push([V(ax, ay, -h), V(ax, ay, h)]);
      for (const ax of c) for (const az of c) lines.push([V(ax, -h, az), V(ax, h, az)]);
      for (const ay of c) for (const az of c) lines.push([V(-h, ay, az), V(h, ay, az)]);
      const ls = BABYLON.MeshBuilder.CreateLineSystem("actionBlockHighlight", { lines }, scene) as LinesMesh;
      ls.color = new BABYLON.Color3(0.6, 0.2, 1.0);
      ls.isPickable = false;
      ls.renderingGroupId = 1;
      highlightMesh = ls;
    },
  };
}
