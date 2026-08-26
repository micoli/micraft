import type { Scene } from "@babylonjs/core";

// Number of sampled points along the parabola and the time step between them. Must match the
// server's tick interval (GameConstants.TICK_SECONDS = 0.05s) so the preview's shape lines up
// with the real per-tick-integrated projectile trajectory.
const SAMPLE_COUNT = 40;
const DT_SECONDS = 0.05;
const MAX_TIME_SECONDS = 5;
// Stop sampling once the parabola drops this far below the muzzle's own height — a cheap ground
// heuristic that avoids drawing a preview tail burrowing deep underground/into the void when no
// real terrain height check is done client-side.
const GROUND_MARGIN = 5;
// World-space radius of the preview tube — a LinesMesh is always hairline-thin (1px, unaffected
// by any width property in WebGL), so a thin tube is used instead to make the arc actually visible.
const TUBE_RADIUS = 0.08;

// Recreated per scene (keyed by scene instance) rather than per call — showTrajectoryPreview
// reruns every frame while a siege weapon is targeted, so the material must be cached rather than
// rebuilt each time.
let cachedMat: InstanceType<typeof BABYLON.StandardMaterial> | null = null;
let cachedMatScene: Scene | null = null;

function trajectoryMaterial(scene: Scene): InstanceType<typeof BABYLON.StandardMaterial> {
  if (cachedMat && cachedMatScene === scene) return cachedMat;
  const mat = new BABYLON.StandardMaterial("trajectoryPreviewMat", scene);
  mat.emissiveColor = new BABYLON.Color3(1, 0.75, 0.2);
  mat.diffuseColor = new BABYLON.Color3(0, 0, 0);
  mat.specularColor = new BABYLON.Color3(0, 0, 0);
  mat.disableLighting = true;
  cachedMat = mat;
  cachedMatScene = scene;
  return mat;
}

export function registerSiegeTrajectory(): Pick<McBindings, "showTrajectoryPreview" | "hideTrajectoryPreview"> {
  return {
    showTrajectoryPreview: (
      scene: Scene,
      originX: number,
      originY: number,
      originZ: number,
      velocityX: number,
      velocityY: number,
      velocityZ: number,
      gravity: number,
    ): void => {
      if (window.mcState.trajectoryMesh) {
        window.mcState.trajectoryMesh.dispose();
        window.mcState.trajectoryMesh = null;
      }

      const points: InstanceType<typeof BABYLON.Vector3>[] = [];
      for (let i = 0; i <= SAMPLE_COUNT; i++) {
        const t = Math.min(i * DT_SECONDS, MAX_TIME_SECONDS);
        const x = originX + velocityX * t;
        const y = originY + velocityY * t + 0.5 * gravity * t * t;
        const z = originZ + velocityZ * t;
        points.push(new BABYLON.Vector3(x, y, z));
        if (t >= MAX_TIME_SECONDS || y < originY - GROUND_MARGIN) break;
      }
      if (points.length < 2) return;

      const tube = BABYLON.MeshBuilder.CreateTube(
        "trajectoryPreview",
        { path: points, radius: TUBE_RADIUS, cap: BABYLON.Mesh.CAP_ALL, updatable: false },
        scene,
      );
      tube.material = trajectoryMaterial(scene);
      tube.isPickable = false;
      window.mcState.trajectoryMesh = tube;
    },

    hideTrajectoryPreview: (): void => {
      if (window.mcState.trajectoryMesh) {
        window.mcState.trajectoryMesh.dispose();
        window.mcState.trajectoryMesh = null;
      }
    },
  };
}
