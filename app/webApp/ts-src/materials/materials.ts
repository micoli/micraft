import type { Scene, StandardMaterial } from "@babylonjs/core";

export function registerMaterials(): void {
  window.mcCreateTextureMaterial = (name: string, url: string, scene: Scene): StandardMaterial => {
    const mat = new BABYLON.StandardMaterial(name, scene);
    mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
    mat.diffuseTexture.hasAlpha = false;
    mat.specularColor = new BABYLON.Color3(0, 0, 0);
    mat.backFaceCulling = false;
    return mat;
  };

  window.mcCreateLeavesMaterial = (name: string, url: string, scene: Scene, r?: number, g?: number, b?: number): StandardMaterial => {
    const mat = new BABYLON.StandardMaterial(name, scene);
    mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
    (mat.diffuseTexture as any).hasAlpha = true;
    (mat as any).useAlphaFromDiffuseTexture = true;
    mat.backFaceCulling = false;
    mat.specularColor = new BABYLON.Color3(0, 0, 0);
    if (r !== undefined) mat.diffuseColor = new BABYLON.Color3(r, g!, b!);
    return mat;
  };

  window.mcCreateCrossSpriteMaterial = (name: string, url: string, scene: Scene): StandardMaterial => {
    const mat = new BABYLON.StandardMaterial(name, scene);
    mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
    (mat.diffuseTexture as any).hasAlpha = true;
    (mat as any).useAlphaFromDiffuseTexture = true;
    mat.backFaceCulling = false;
    mat.specularColor = new BABYLON.Color3(0, 0, 0);
    return mat;
  };

  // Creates a StandardMaterial for each block texture defined in blocks.bbmodel.
  // Returns a Record<matKey, Material> used by mcChunkEnd.
  // The special key "<name>:biome_tint" is created for biome-tinted faces (e.g. grass_top).
  window.mcCreateBlockMaterials = (scene: any): Record<string, StandardMaterial> => {
    const textures: McBlockTextureDef[] = window.mcGetBlockTextures();
    const mats: Record<string, StandardMaterial> = {};

    for (const t of textures) {
      const mat = new BABYLON.StandardMaterial(t.name, scene);
      mat.diffuseTexture = new BABYLON.Texture(t.url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      mat.backFaceCulling = false;

      if (t.hasAlpha) {
        (mat.diffuseTexture as any).hasAlpha = true;
        (mat as any).useAlphaFromDiffuseTexture = true;
      }

      if (t.tint) {
        mat.diffuseColor = new BABYLON.Color3(t.tint[0], t.tint[1], t.tint[2]);
      }

      mats[t.name] = mat;

      if (t.biomeTint) {
        // Separate material instance for biome-tinted variant; color updated via mcSetGrassTint
        const tinted = new BABYLON.StandardMaterial(t.name + ':biome_tint', scene);
        tinted.diffuseTexture = new BABYLON.Texture(t.url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
        tinted.specularColor = new BABYLON.Color3(0, 0, 0);
        tinted.backFaceCulling = false;
        tinted.diffuseColor = new BABYLON.Color3(0.47, 0.75, 0.35); // default plains tint
        mats[t.name + ':biome_tint'] = tinted;
      }
    }

    // Register grass tint updater — updates diffuseColor on all biomeTint materials
    window.mcSetGrassTint = (r: number, g: number, b: number) => {
      for (const key of Object.keys(mats)) {
        if (key.endsWith(':biome_tint')) {
          (mats[key] as any).diffuseColor = new BABYLON.Color3(r, g, b);
        }
      }
    };

    return mats;
  };
}
