import type { Scene, StandardMaterial } from "@babylonjs/core";
import { BLOCK_VERT, BLOCK_FRAG } from "../shaders/block";

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
      (mat as any).useVertexColors = true;
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
      (mat.diffuseTexture as any).hasAlpha = true;
      (mat as any).useAlphaFromDiffuseTexture = true;
      mat.backFaceCulling = false;
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      if (r !== undefined) mat.diffuseColor = new BABYLON.Color3(r, g!, b!);
      (mat as any).useVertexColors = true;
      return mat;
    },

    createCrossSpriteMaterial: (name: string, url: string, scene: Scene): StandardMaterial => {
      const mat = new BABYLON.StandardMaterial(name, scene);
      mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      (mat.diffuseTexture as any).hasAlpha = true;
      (mat as any).useAlphaFromDiffuseTexture = true;
      mat.backFaceCulling = false;
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      (mat as any).useVertexColors = true;
      return mat;
    },

    // Creates a ShaderMaterial for each block texture defined in blocks.bbmodel.
    // Returns a Record<matKey, Material> used by chunkEnd.
    // The special key "<name>:biome_tint" is created for biome-tinted faces (e.g. grass_top).
    createBlockMaterials: (scene: any): Record<string, any> => {
      const textures: McBlockTextureDef[] = window.mc.getBlockTextures();
      const mats: Record<string, any> = {};

      const fogColor = (scene as any).fogColor ?? { r: 0.53, g: 0.81, b: 0.98 };
      const fogStart: number = (scene as any).fogStart ?? 24;
      const fogEnd: number = (scene as any).fogEnd ?? 40;

      const makeMat = (name: string, url: string, tintR: number, tintG: number, tintB: number): any => {
        const mat = new BABYLON.ShaderMaterial(name, scene, { vertexSource: BLOCK_VERT, fragmentSource: BLOCK_FRAG }, {
          attributes: ["position", "normal", "uv", "color"],
          uniforms: [
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
            "tint",
            "shadersEnabled",
            "ambient",
            "playerLightIntensity",
          ],
          vectors3: ["playerPos"],
          samplers: ["textureSampler"],
        } as any);
        const tex = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
        mat.setTexture("textureSampler", tex);
        mat.setVector3("fogColor", new BABYLON.Vector3(fogColor.r, fogColor.g, fogColor.b));
        mat.setFloat("fogStart", fogStart);
        mat.setFloat("fogEnd", fogEnd);
        mat.setVector3("tint", new BABYLON.Vector3(tintR, tintG, tintB));
        mat.setFloat("shadersEnabled", 1.0);
        mat.setFloat("ambient", 1.0);
        mat.setFloat("playerLightIntensity", 0.0);
        mat.setVector3("playerPos", new BABYLON.Vector3(0, 0, 0));
        mat.setFloat("fogZoneCx", 0.0);
        mat.setFloat("fogZoneCz", 0.0);
        mat.setFloat("fogZoneRadius", 0.0);
        mat.setFloat("fogZoneStart", 8.0);
        mat.setFloat("fogZoneEnd", 40.0);
        mat.backFaceCulling = false;
        mat.forceDepthWrite = true;
        return mat;
      };

      for (const t of textures) {
        const [tr, tg, tb] = t.tint ?? [1, 1, 1];
        mats[t.name] = makeMat(t.name, t.url, tr, tg, tb);

        if (t.biomeTint) {
          // Separate instance for biome-tinted variant; tint updated via setGrassTint
          mats[t.name + ":biome_tint"] = makeMat(t.name + ":biome_tint", t.url, 0.47, 0.75, 0.35);
        }
      }

      // Wire setGrassTint now that mats are available
      setGrassTintImpl = (r: number, g: number, b: number) => {
        for (const key of Object.keys(mats)) {
          if (key.endsWith(":biome_tint")) {
            mats[key].setVector3("tint", new BABYLON.Vector3(r, g, b));
          }
        }
      };

      const waterMat = new BABYLON.StandardMaterial("water", scene);
      waterMat.diffuseColor = new BABYLON.Color3(0.2, 0.47, 0.78);
      waterMat.alpha = 0.7;
      waterMat.backFaceCulling = false;
      waterMat.specularColor = new BABYLON.Color3(0.1, 0.1, 0.2);
      mats["water"] = waterMat;

      window.mcState.blockMaterials = mats;
      return mats;
    },

    // Wrapper delegates to setGrassTintImpl — captures var by ref so update from createBlockMaterials is visible
    setGrassTint: (r: number, g: number, b: number) => setGrassTintImpl(r, g, b),
  };
}
