import type { Camera, Engine, Scene, HemisphericLight, Mesh } from "@babylonjs/core";

export function registerEngine(): Pick<
  McBindings,
  | "createEngine"
  | "createHemisphericLight"
  | "createSunLight"
  | "createBox"
  | "createSimpleBox"
  | "freezeMesh"
  | "optimizeScene"
  | "setupFog"
  | "setShadersEnabled"
  | "setAmbient"
  | "setPlayerLight"
  | "setRemotePlayerLight"
  | "setupRenderPipeline"
> {
  return {
    createEngine: (): Engine => {
      if (window.mcState.engine) {
        try {
          (window.mcState.engine as Engine).dispose();
        } catch {
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

    createSunLight: (scene: Scene): void => {
      const ORTHO_SIZE = 52; // ~3.25 chunks of margin either side of 80-block view
      const CAM_DIST = 300;

      // Orthographic camera looking from sun direction — we control position/target fully
      const shadowCam = new BABYLON.FreeCamera("shadowCam", new BABYLON.Vector3(0, CAM_DIST, 0), scene);
      shadowCam.mode = BABYLON.Camera.ORTHOGRAPHIC_CAMERA;
      shadowCam.orthoLeft = -ORTHO_SIZE;
      shadowCam.orthoRight = ORTHO_SIZE;
      shadowCam.orthoTop = ORTHO_SIZE;
      shadowCam.orthoBottom = -ORTHO_SIZE;
      shadowCam.minZ = 1;
      shadowCam.maxZ = CAM_DIST * 2 + 100;
      shadowCam.setTarget(BABYLON.Vector3.Zero());
      // Remove from scene.cameras and clear activeCamera so the next camera created (player cam) wins
      const camIdx = scene.cameras.indexOf(shadowCam as unknown as InstanceType<typeof BABYLON.Camera>);
      if (camIdx >= 0) scene.cameras.splice(camIdx, 1);
      if ((scene.activeCamera as unknown) === shadowCam) scene.activeCamera = null;

      // Depth shader: stores (z_ndc * 0.5 + 0.5) = gl_FragCoord.z equivalent
      const depthMat = new BABYLON.ShaderMaterial(
        "shadowDepthMat",
        scene,
        {
          vertexSource: `
            precision highp float;
            attribute vec3 position;
            uniform mat4 worldViewProjection;
            varying float vDepth;
            void main() {
              gl_Position = worldViewProjection * vec4(position, 1.0);
              vDepth = gl_Position.z / gl_Position.w * 0.5 + 0.5;
            }
          `,
          fragmentSource: `
            precision highp float;
            varying float vDepth;
            void main() {
              // Pack depth into RG: 16-bit precision via high+low byte
              float hi = floor(vDepth * 255.0) / 255.0;
              float lo = fract(vDepth * 255.0);
              gl_FragColor = vec4(hi, lo, 0.0, 1.0);
            }
          `,
        },
        { attributes: ["position"], uniforms: ["worldViewProjection"] },
      );
      depthMat.backFaceCulling = true;

      // Custom RTT: renders chunk meshes with depthMat from shadow camera's POV
      // UNSIGNED_BYTE with RG-packed depth: 16-bit precision (1/65536 per step)
      // vs 8-bit single channel (1/256). 1-block NDC separation ≈ 0.00143 → 93 steps apart.
      const shadowRTT = new BABYLON.RenderTargetTexture("shadowRTT", 256, scene, false, true);
      shadowRTT.activeCamera = shadowCam;
      shadowRTT.renderList = [];
      // Only re-render when resetRefreshCounter() is explicitly called (sun ≥1° or snap changed)
      shadowRTT.refreshRate = 10000;

      // After RTT renders, capture camera's VP matrix (guaranteed fresh)
      shadowRTT.onAfterRenderObservable.add(() => {
        const vpMatrix = shadowCam.getTransformationMatrix();
        const mats = window.mcState.blockMaterials;
        if (mats)
          for (const mat of Object.values(mats))
            if (mat instanceof BABYLON.ShaderMaterial) mat.setMatrix("lightWVP", vpMatrix);
      });

      scene.customRenderTargets.push(shadowRTT);

      window.mcState.sunShadowCamera = shadowCam;
      window.mcState.sunShadowRTT = shadowRTT;
      window.mcState.sunShadowDepthMat = depthMat;

      const mats = window.mcState.blockMaterials;
      if (mats)
        for (const mat of Object.values(mats))
          if (mat instanceof BABYLON.ShaderMaterial) mat.setTexture("shadowSampler", shadowRTT);
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
      scene.skipPointerMovePicking = true;
      scene.blockMaterialDirtyMechanism = true;
    },

    setupFog: (scene: Scene, r: number, g: number, b: number): void => {
      scene.fogMode = BABYLON.Scene.FOGMODE_LINEAR;
      scene.fogStart = 24;
      scene.fogEnd = 40;
      scene.fogColor = new BABYLON.Color3(r, g, b);
      scene.clearColor = new BABYLON.Color4(r, g, b, 1.0);
      // Sync fog color to block shader materials (created after this call)
      const mats = window.mcState.blockMaterials;
      if (mats) {
        const fv = new BABYLON.Vector3(r, g, b);
        for (const mat of Object.values(mats))
          if (mat instanceof BABYLON.ShaderMaterial) mat.setVector3("fogColor", fv);
      }
    },

    setShadersEnabled: (_scene: Scene, enabled: boolean): void => {
      const mats = window.mcState.blockMaterials;
      if (mats) {
        const v = enabled ? 1.0 : 0.0;
        for (const mat of Object.values(mats))
          if (mat instanceof BABYLON.ShaderMaterial) mat.setFloat("shadersEnabled", v);
      }
    },

    setAmbient: (_scene: Scene, v: number): void => {
      const mats = window.mcState.blockMaterials;
      if (mats)
        for (const mat of Object.values(mats)) if (mat instanceof BABYLON.ShaderMaterial) mat.setFloat("ambient", v);
    },

    setPlayerLight: (_scene: Scene, x: number, y: number, z: number, intensity: number): void => {
      const mats = window.mcState.blockMaterials;
      if (!mats) return;
      const pos = new BABYLON.Vector3(x, y, z);
      for (const mat of Object.values(mats)) {
        if (mat instanceof BABYLON.ShaderMaterial) {
          mat.setVector3("playerPos", pos);
          mat.setFloat("playerLightIntensity", intensity);
        }
      }
    },

    setRemotePlayerLight: (model: McPlayerModel, _scene: Scene, enabled: boolean): void => {
      if (enabled) {
        if (model._lightBoost) return;

        const armNode = model.pivotNodes["rightArm"]?.node ?? model.root;
        // hand offset relative to rightArm pivot in scene units (bbmodel: rightItem=[6,15,1], rightArm=[5,22,0], SCALE=1/16)
        const handOffset = new BABYLON.Vector3(0.0625, -0.4375, 0.0625);

        const orb = BABYLON.MeshBuilder.CreateSphere(
          "lightOrb_" + Math.random().toString(36).slice(2),
          { diameter: 0.18, segments: 6 },
          _scene,
        );
        orb.parent = armNode;
        orb.position = handOffset.clone();
        orb.isPickable = false;
        const mat = new BABYLON.StandardMaterial("lightOrbMat_" + Math.random().toString(36).slice(2), _scene);
        mat.emissiveColor = new BABYLON.Color3(1.0, 0.75, 0.2);
        mat.disableLighting = true;
        orb.material = mat;

        const light = new BABYLON.PointLight(
          "remotePlayerPointLight_" + Math.random().toString(36).slice(2),
          BABYLON.Vector3.Zero(),
          _scene,
        );
        light.intensity = 1.5;
        light.range = 20;
        light.diffuse = new BABYLON.Color3(1.0, 0.85, 0.5);
        light.parent = armNode;
        light.position = handOffset.clone();

        model._lightBoost = { orb, light };
      } else {
        const lb = model._lightBoost;
        if (lb) {
          lb.orb?.dispose();
          lb.light?.dispose();
          model._lightBoost = null;
        }
      }
    },

    setupRenderPipeline: (scene: Scene, camera: Camera): void => {
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
