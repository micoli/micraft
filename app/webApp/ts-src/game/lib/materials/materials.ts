import type { Scene, ShaderMaterial, StandardMaterial } from "@babylonjs/core";
import { BLOCK_ARRAY_FRAG, BLOCK_ARRAY_VERT, BLOCK_VERT, BLOCK_FRAG } from "../block";
import { createTerrainTextureArray, TERRAIN_ARRAY_MAT_KEY } from "./terrainTextureArray";
import { WHITE_PIXEL_URL } from "./whitePixel";

export function registerMaterials(): Pick<
  McBindings,
  | "createTextureMaterial"
  | "createLeavesMaterial"
  | "createCrossSpriteMaterial"
  | "createBlockMaterials"
  | "setGrassTint"
> {
  // Updated by createBlockMaterials once mats are available
  let setGrassTintImpl: McBindings["setGrassTint"] = () => {};

  return {
    createTextureMaterial: (name: string, url: string, scene: Scene): StandardMaterial => {
      const mat = new BABYLON.StandardMaterial(name, scene);
      mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      mat.diffuseTexture.hasAlpha = false;
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      mat.backFaceCulling = false;
      // mat.useVertexColors = true;
      return mat;
    },

    createLeavesMaterial: (
      name: string,
      url: string,
      scene: Scene,
      r?: number,
      g?: number,
      b?: number,
    ): StandardMaterial => {
      const mat = new BABYLON.StandardMaterial(name, scene);
      mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      mat.diffuseTexture.hasAlpha = true;
      mat.useAlphaFromDiffuseTexture = true;
      mat.backFaceCulling = false;
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      if (r !== undefined) mat.diffuseColor = new BABYLON.Color3(r, g!, b!);
      // mat.useVertexColors = true;
      return mat;
    },

    createCrossSpriteMaterial: (name: string, url: string, scene: Scene): StandardMaterial => {
      const mat = new BABYLON.StandardMaterial(name, scene);
      mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      mat.diffuseTexture.hasAlpha = true;
      mat.useAlphaFromDiffuseTexture = true;
      mat.backFaceCulling = false;
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      // mat.useVertexColors = true;
      return mat;
    },

    // Creates a ShaderMaterial for each block texture defined in blocks.bbmodel.
    // Returns a Record<matKey, Material> used by chunkEnd.
    // The special key "<name>:biome_tint" is created for biome-tinted faces (e.g. grass_top).
    createBlockMaterials: (scene: Scene): Record<string, ShaderMaterial | StandardMaterial> => {
      const textures: McBlockTextureDef[] = window.mc.getBlockTextures();
      const mats: Record<string, ShaderMaterial | StandardMaterial> = {};

      const fogColor = scene.fogColor ?? { r: 0.53, g: 0.81, b: 0.98 };
      const fogStart: number = scene.fogStart ?? 24;
      const fogEnd: number = scene.fogEnd ?? 40;

      // Uniforms every terrain shader shares; the per-texture variant adds "tint", the texture-array
      // variant "biomeTint" (its tint travels per vertex).
      const sharedUniforms = [
        "worldViewProjection",
        "view",
        "world",
        "fogColor",
        "fogStart",
        "fogEnd",
        "fogZoneCx",
        "fogZoneCz",
        "fogZoneRadius",
        "fogZoneStart",
        "fogZoneEnd",
        "shadersEnabled",
        "ambient",
        "playerLightIntensity",
        "playerPos",
        "lightWVP",
        "shadowDarkness",
        "sunDir",
        "clipPlaneX",
        "clipPlaneY",
        "clipPlaneZ",
      ];

      const configure = (mat: ShaderMaterial): ShaderMaterial => {
        mat.setVector3("fogColor", new BABYLON.Vector3(fogColor.r, fogColor.g, fogColor.b));
        mat.setFloat("fogStart", fogStart);
        mat.setFloat("fogEnd", fogEnd);
        mat.setFloat("shadersEnabled", 1.0);
        mat.setFloat("ambient", 1.0);
        mat.setFloat("playerLightIntensity", 0.0);
        mat.setVector3("playerPos", new BABYLON.Vector3(0, 0, 0));
        mat.setMatrix("lightWVP", BABYLON.Matrix.Identity());
        mat.setFloat("shadowDarkness", 0.0);
        mat.setVector3("sunDir", new BABYLON.Vector3(0, 1, 0));
        mat.setFloat("fogZoneCx", 0.0);
        mat.setFloat("fogZoneCz", 0.0);
        mat.setFloat("fogZoneRadius", 0.0);
        mat.setFloat("fogZoneStart", 8.0);
        mat.setFloat("fogZoneEnd", 40.0);
        const inertClip = new BABYLON.Vector4(0, 0, 0, -1);
        mat.setVector4("clipPlaneX", inertClip);
        mat.setVector4("clipPlaneY", inertClip);
        mat.setVector4("clipPlaneZ", inertClip);
        mat.backFaceCulling = false;
        mat.forceDepthWrite = true;
        // Frozen: once compiled, isReady() returns early instead of rebuilding the defines and
        // attribute lists for every terrain submesh every frame. Uniforms still rebind normally.
        mat.freeze();
        return mat;
      };

      const makeMat = (name: string, url: string, tintR: number, tintG: number, tintB: number): ShaderMaterial => {
        const mat = new BABYLON.ShaderMaterial(
          name,
          scene,
          { vertexSource: BLOCK_VERT, fragmentSource: BLOCK_FRAG },
          {
            attributes: ["position", "normal", "uv", "color"],
            uniforms: [...sharedUniforms, "tint"],
            samplers: ["textureSampler", "shadowSampler"],
          },
        );
        const tex = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
        mat.setTexture("textureSampler", tex);
        mat.setVector3("tint", new BABYLON.Vector3(tintR, tintG, tintB));
        return configure(mat);
      };

      // Plain colors reuse the block shader with a 1×1 white texture: the `tint` uniform
      // (already multiplied into texColor) paints every face a flat color, so studs still get
      // AO, face shading and the plastic highlight from the vertex colors.
      for (const color of window.mc.getPlainColors()) {
        const key = "plain:" + color.hex;
        if (mats[key]) continue;
        mats[key] = makeMat(key, WHITE_PIXEL_URL, color.r / 255, color.g / 255, color.b / 255);
      }

      for (const t of textures) {
        const [tr, tg, tb] = t.tint ?? [1, 1, 1];
        mats[t.name] = makeMat(t.name, t.url, tr, tg, tb);

        if (t.biomeTint) {
          // Separate instance for biome-tinted variant; tint updated via setGrassTint
          mats[t.name + ":biome_tint"] = makeMat(t.name + ":biome_tint", t.url, 0.47, 0.75, 0.35);
        }
      }

      // One material for every terrain texture when the texture array is active (in game, WebGL2):
      // the per-texture materials above stay for the main-thread meshing path and the admin editors.
      const textureArray = createTerrainTextureArray(scene);
      if (textureArray) {
        const arrayMat = new BABYLON.ShaderMaterial(
          TERRAIN_ARRAY_MAT_KEY,
          scene,
          { vertexSource: BLOCK_ARRAY_VERT, fragmentSource: BLOCK_ARRAY_FRAG },
          {
            attributes: ["position", "normal", "uv", "color", "tintLayer"],
            uniforms: [...sharedUniforms, "biomeTint"],
            samplers: ["textureArray", "shadowSampler"],
          },
        );
        arrayMat.setTexture("textureArray", textureArray);
        arrayMat.setVector3("biomeTint", new BABYLON.Vector3(0.47, 0.75, 0.35));
        mats[TERRAIN_ARRAY_MAT_KEY] = configure(arrayMat);
      }

      // Wire setGrassTint now that mats are available
      setGrassTintImpl = (r: number, g: number, b: number) => {
        for (const key of Object.keys(mats)) {
          if (key.endsWith(":biome_tint")) {
            (mats[key] as ShaderMaterial).setVector3("tint", new BABYLON.Vector3(r, g, b));
          }
        }
        (mats[TERRAIN_ARRAY_MAT_KEY] as ShaderMaterial | undefined)?.setVector3(
          "biomeTint",
          new BABYLON.Vector3(r, g, b),
        );
      };

      const waterMat = new BABYLON.StandardMaterial("water", scene);
      waterMat.diffuseColor = new BABYLON.Color3(0.2, 0.47, 0.78);
      waterMat.alpha = 0.7;
      waterMat.backFaceCulling = false;
      waterMat.specularColor = new BABYLON.Color3(0.1, 0.1, 0.2);
      mats["water"] = waterMat;

      // Wire shadow RTT if already created
      const shadowRTT = window.mcState.sunShadowRTT;
      if (shadowRTT) {
        for (const mat of Object.values(mats))
          if (mat instanceof BABYLON.ShaderMaterial) mat.setTexture("shadowSampler", shadowRTT);
      }

      window.mcState.blockMaterials = mats;
      return mats;
    },

    // Wrapper delegates to setGrassTintImpl — captures var by ref so update from createBlockMaterials is visible
    setGrassTint: (r: number, g: number, b: number) => setGrassTintImpl(r, g, b),
  };
}
