import type { LinesMesh, Scene, Vector3 } from "@babylonjs/core";

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

      const lines: Vector3[][] = [points];
      const ls = BABYLON.MeshBuilder.CreateLineSystem("trajectoryPreview", { lines }, scene) as LinesMesh;
      ls.color = new BABYLON.Color3(1, 0.75, 0.2);
      ls.isPickable = false;
      window.mcState.trajectoryMesh = ls;
    },

    hideTrajectoryPreview: (): void => {
      if (window.mcState.trajectoryMesh) {
        window.mcState.trajectoryMesh.dispose();
        window.mcState.trajectoryMesh = null;
      }
    },
  };
}
