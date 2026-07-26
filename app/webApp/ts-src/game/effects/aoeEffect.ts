import type { Scene } from "@babylonjs/core";

export function registerAoeEffect() {
  return {
    aoeEffect(scene: Scene, x: number, y: number, z: number, radius: number) {
      const sphere = BABYLON.MeshBuilder.CreateSphere("aoeEffect", { diameter: radius * 2, segments: 12 }, scene);
      sphere.position.set(x, y, z);

      const mat = new BABYLON.StandardMaterial("aoeMat", scene);
      mat.emissiveColor = new BABYLON.Color3(0.4, 0.0, 0.8);
      mat.alpha = 0.35;
      mat.backFaceCulling = false;
      mat.fogEnabled = false;
      sphere.material = mat;

      const startTime = performance.now();
      const holdMs = 1000;
      const fadeMs = 1000;

      const tick = () => {
        const elapsed = performance.now() - startTime;
        if (elapsed < holdMs) {
          requestAnimationFrame(tick);
        } else {
          const t = Math.min((elapsed - holdMs) / fadeMs, 1);
          mat.alpha = 0.35 * (1 - t);
          if (t < 1) {
            requestAnimationFrame(tick);
          } else {
            sphere.dispose();
            mat.dispose();
          }
        }
      };
      requestAnimationFrame(tick);
    },
  };
}
