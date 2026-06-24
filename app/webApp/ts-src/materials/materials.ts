import type { Scene, StandardMaterial } from "@babylonjs/core";
import { BLOCK_VERT, BLOCK_FRAG } from "../shaders/block";

export function registerMaterials(): void {
  window.mcCreateTextureMaterial = (name: string, url: string, scene: Scene): StandardMaterial => {
    const mat = new BABYLON.StandardMaterial(name, scene);
    mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
    mat.diffuseTexture.hasAlpha = false;
    mat.specularColor = new BABYLON.Color3(0, 0, 0);
    mat.backFaceCulling = false;
    (mat as any).useVertexColors = true;
    return mat;
  };

  window.mcCreateLeavesMaterial = (
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
  };

  window.mcCreateCrossSpriteMaterial = (name: string, url: string, scene: Scene): StandardMaterial => {
    const mat = new BABYLON.StandardMaterial(name, scene);
    mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
    (mat.diffuseTexture as any).hasAlpha = true;
    (mat as any).useAlphaFromDiffuseTexture = true;
    mat.backFaceCulling = false;
    mat.specularColor = new BABYLON.Color3(0, 0, 0);
    (mat as any).useVertexColors = true;
    return mat;
  };

  // Creates a ShaderMaterial for each block texture defined in blocks.bbmodel.
  // Returns a Record<matKey, Material> used by mcChunkEnd.
  // The special key "<name>:biome_tint" is created for biome-tinted faces (e.g. grass_top).
  window.mcCreateBlockMaterials = (scene: any): Record<string, any> => {
    const textures: McBlockTextureDef[] = window.mcGetBlockTextures();
    const mats: Record<string, any> = {};

    const fogColor = (scene as any).fogColor ?? { r: 0.53, g: 0.81, b: 0.98 };
    const fogStart: number = (scene as any).fogStart ?? 24;
    const fogEnd: number = (scene as any).fogEnd ?? 40;

    const makeMat = (name: string, url: string, tintR: number, tintG: number, tintB: number): any => {
      const mat = new BABYLON.ShaderMaterial(
        name,
        scene,
        { vertexSource: BLOCK_VERT, fragmentSource: BLOCK_FRAG },
        {
          attributes: ["position", "normal", "uv", "color"],
          uniforms: ["worldViewProjection", "view", "world", "fogColor", "fogStart", "fogEnd", "tint", "shadersEnabled"],
          samplers: ["textureSampler"],
        },
      );
      const tex = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      mat.setTexture("textureSampler", tex);
      mat.setVector3("fogColor", new BABYLON.Vector3(fogColor.r, fogColor.g, fogColor.b));
      mat.setFloat("fogStart", fogStart);
      mat.setFloat("fogEnd", fogEnd);
      mat.setVector3("tint", new BABYLON.Vector3(tintR, tintG, tintB));
      mat.setFloat("shadersEnabled", 1.0);
      mat.backFaceCulling = false;
      mat.forceDepthWrite = true;
      return mat;
    };

    for (const t of textures) {
      const [tr, tg, tb] = t.tint ?? [1, 1, 1];
      mats[t.name] = makeMat(t.name, t.url, tr, tg, tb);

      if (t.biomeTint) {
        // Separate instance for biome-tinted variant; tint updated via mcSetGrassTint
        mats[t.name + ":biome_tint"] = makeMat(t.name + ":biome_tint", t.url, 0.47, 0.75, 0.35);
      }
    }

    // Register grass tint updater — sets tint uniform on all biomeTint materials
    window.mcSetGrassTint = (r: number, g: number, b: number) => {
      for (const key of Object.keys(mats)) {
        if (key.endsWith(":biome_tint")) {
          mats[key].setVector3("tint", new BABYLON.Vector3(r, g, b));
        }
      }
    };

    (window as any).__mcBlockMaterials = mats;
    return mats;
  };
}
