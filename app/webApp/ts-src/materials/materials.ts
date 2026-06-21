import type { Scene, StandardMaterial, MultiMaterial } from "@babylonjs/core";

export function registerMaterials(): void {
  window.mcCreateTextureMaterial = (name: string, url: string, scene: Scene): StandardMaterial => {
    const mat = new BABYLON.StandardMaterial(name, scene);
    mat.diffuseTexture = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
    mat.diffuseTexture.hasAlpha = false;
    mat.specularColor = new BABYLON.Color3(0, 0, 0);
    mat.backFaceCulling = false;
    return mat;
  };

  window.mcCreateGrassMaterial = (scene: Scene): MultiMaterial => {
    const texMat = (n: string, u: string, ang?: number): StandardMaterial => {
      const m = new BABYLON.StandardMaterial(n, scene);
      m.diffuseTexture = new BABYLON.Texture(u, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      if (ang !== undefined) (m.diffuseTexture as any).wAng = ang;
      m.diffuseTexture.hasAlpha = false;
      m.specularColor = new BABYLON.Color3(0, 0, 0);
      m.backFaceCulling = false;
      return m;
    };

    const top = texMat('grass_top', '/textures/blocks/grass_top.png');
    top.diffuseColor = new BABYLON.Color3(0.47, 0.75, 0.35);
    const sideFr = texMat('grass_side_fr', '/textures/blocks/grass_side.png', Math.PI);
    const sideBk = texMat('grass_side_bk', '/textures/blocks/grass_side.png', Math.PI);
    const sideX  = texMat('grass_side_x',  '/textures/blocks/grass_side.png', Math.PI / 2);
    const sideX2 = texMat('grass_side_x2', '/textures/blocks/grass_side.png', Math.PI);
    const bottom = texMat('grass_bot',    '/textures/blocks/dirt.png');

    const multi = new BABYLON.MultiMaterial('grass', scene);
    // BabylonJS CreateBox face order: 0=front(+Z), 1=back(-Z), 2=right(+X), 3=left(-X), 4=top(+Y), 5=bottom(-Y)
    multi.subMaterials = [sideFr, sideBk, sideX2, sideX, top, bottom];
    return multi;
  };
}
