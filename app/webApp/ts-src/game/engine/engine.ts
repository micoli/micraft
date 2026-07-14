import type { Engine, Scene, HemisphericLight, Mesh } from "@babylonjs/core";

export function registerEngine(): Pick<
  McBindings,
  | "createEngine"
  | "createHemisphericLight"
  | "createBox"
  | "createSimpleBox"
  | "freezeMesh"
  | "optimizeScene"
  | "setupFog"
  | "setShadersEnabled"
  | "setAmbient"
  | "setPlayerLight"
  | "setupRenderPipeline"
> {
  return {
    createEngine: (): Engine => {
      if (window.mcState.engine) {
        try {
          (window.mcState.engine as Engine).dispose();
        } catch (_e) {
          /* ignore */
        }
        window.mcState.engine = null;
      }

      const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
      if (!canvas) throw new Error("[MiCraft] Canvas #renderCanvas not found");

      const probe = document.createElement("canvas");
      const gl = probe.getContext("webgl2") || probe.getContext("webgl");
      if (!gl) {
        console.error(
          "[MiCraft] WebGL unavailable. Open chrome://gpu and check that " +
            '"WebGL" and "Hardware-accelerated" are enabled. ' +
            "You can also try: chrome://settings/system → enable hardware acceleration.",
        );
        throw new Error("[MiCraft] WebGL not supported by this browser / GPU configuration");
      }

      let engine: Engine;
      try {
        engine = new BABYLON.Engine(canvas, false, { disableWebGL2Support: false, preserveDrawingBuffer: false });
      } catch (e) {
        console.warn("[MiCraft] WebGL2 failed (" + (e as Error).message + "), retrying with WebGL1");
        engine = new BABYLON.Engine(canvas, false, { disableWebGL2Support: true });
      }

      window.mcState.engine = engine;
      window.addEventListener("beforeunload", () => engine.dispose(), { once: true });
      console.log("[MiCraft] Engine created: " + (engine.webGLVersion === 2 ? "WebGL2" : "WebGL1"));
      return engine;
    },

    createHemisphericLight: (name: string, scene: Scene): HemisphericLight => {
      const l = new BABYLON.HemisphericLight(name, new BABYLON.Vector3(0, 1, 0), scene);
      l.groundColor = new BABYLON.Color3(0.4, 0.4, 0.4);
      window.mcState.hemiLight = l;
      return l;
    },

    // Multi-face box for GRASS (6 SubMeshes → MultiMaterial with per-face texture).
    createBox: (name: string, size: number, scene: Scene): Mesh => {
      const uv = () => new BABYLON.Vector4(0, 1, 1, 0);
      const box = BABYLON.MeshBuilder.CreateBox(
        name,
        {
          size,
          faceUV: [uv(), uv(), uv(), uv(), uv(), uv()],
        },
        scene,
      );
      box.subMeshes = [];
      const vc = box.getTotalVertices();
      for (let i = 0; i < 6; i++) {
        new BABYLON.SubMesh(i, 0, vc, i * 6, 6, box);
      }
      return box;
    },

    // Simple box for uniform-material blocks (STONE, DIRT, BEDROCK): 1 draw call vs 6.
    createSimpleBox: (name: string, size: number, scene: Scene): Mesh =>
      BABYLON.MeshBuilder.CreateBox(name, { size }, scene),

    // Freeze world matrix (static block never moves) and disable picking.
    freezeMesh: (mesh: Mesh): void => {
      mesh.freezeWorldMatrix();
      mesh.isPickable = false;
      mesh.doNotSyncBoundingInfo = true;
    },

    // One-time scene tweaks: skip per-frame pointer picking and material dirty checks.
    optimizeScene: (scene: Scene): void => {
      (scene as any).skipPointerMovePicking = true;
      (scene as any).blockMaterialDirtyMechanism = true;
    },

    setupFog: (scene: Scene, r: number, g: number, b: number): void => {
      (scene as any).fogMode = BABYLON.Scene.FOGMODE_LINEAR;
      (scene as any).fogStart = 24;
      (scene as any).fogEnd = 40;
      (scene as any).fogColor = new BABYLON.Color3(r, g, b);
      (scene as any).clearColor = new BABYLON.Color4(r, g, b, 1.0);
      // Sync fog color to block shader materials (created after this call)
      const mats = window.mcState.blockMaterials as Record<string, any> | undefined;
      if (mats) {
        const fv = new BABYLON.Vector3(r, g, b);
        for (const mat of Object.values(mats)) if (typeof mat.setVector3 === "function") mat.setVector3("fogColor", fv);
      }
    },

    setShadersEnabled: (scene: Scene, enabled: boolean): void => {
      const mats = window.mcState.blockMaterials as Record<string, any> | undefined;
      if (mats) {
        const v = enabled ? 1.0 : 0.0;
        for (const mat of Object.values(mats))
          if (typeof mat.setFloat === "function") mat.setFloat("shadersEnabled", v);
      }
    },

    setAmbient: (_scene: Scene, v: number): void => {
      const mats = window.mcState.blockMaterials as Record<string, any> | undefined;
      if (mats)
        for (const mat of Object.values(mats)) if (typeof mat.setFloat === "function") mat.setFloat("ambient", v);
    },

    setPlayerLight: (_scene: Scene, x: number, y: number, z: number, intensity: number): void => {
      const mats = window.mcState.blockMaterials as Record<string, any> | undefined;
      if (!mats) return;
      const pos = new BABYLON.Vector3(x, y, z);
      for (const mat of Object.values(mats)) {
        if (typeof mat.setVector3 === "function") mat.setVector3("playerPos", pos);
        if (typeof mat.setFloat === "function") mat.setFloat("playerLightIntensity", intensity);
      }
    },

    setupRenderPipeline: (scene: Scene, camera: any): void => {
      const pipeline = new BABYLON.DefaultRenderingPipeline("mcPipeline", true, scene, [camera]);

      pipeline.imageProcessingEnabled = false;

      // FXAA — anti-aliasing des bords de géométrie
      pipeline.fxaaEnabled = true;

      // Bloom très subtil sur les zones lumineuses (ciel, surfaces claires)
      pipeline.bloomEnabled = true;
      pipeline.bloomWeight = 0.05;
      pipeline.bloomThreshold = 0.78;
      pipeline.bloomScale = 0.5;
      pipeline.bloomKernel = 8;

      window.mcState.renderPipeline = pipeline;
    },
  };
}
