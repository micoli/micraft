import type { Material, Mesh, Scene } from "@babylonjs/core";

// bbmodel coordinates are in Blockbench pixel units, 16px = 1 block.
const BBMODEL_SCALE = 1 / 16;

export function isMeshElement(el: BbModelElement | BbModelMeshElement): el is BbModelMeshElement {
  return "vertices" in el && "faces" in el && typeof (el as BbModelMeshElement).vertices === "object";
}

// A texture without explicit width/height falls back to the model's shared resolution (the case
// for every texture on a single-material model).
export function resolveTextureDims(bbmodel: BbModel): Array<{ width: number; height: number }> {
  return bbmodel.textures.map((t) => ({
    width: t.width ?? bbmodel.resolution.width,
    height: t.height ?? bbmodel.resolution.height,
  }));
}

// One material per bbmodel texture, cached under `${cachePrefix}_${index}` so repeated
// equips/previews of the same model reuse the same GPU resource.
export function buildTextureMaterials(
  bbmodel: BbModel,
  scene: Scene,
  cachePrefix: string,
  hasAlpha: boolean,
): Material[] {
  return bbmodel.textures.map((texDef, i) => {
    const cacheKey = `${cachePrefix}_${i}`;
    if (!window.mcState.skinMatCache[cacheKey]) {
      const tex = new BABYLON.Texture(texDef.source, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      tex.hasAlpha = hasAlpha;
      tex.wrapU = BABYLON.Texture.CLAMP_ADDRESSMODE;
      tex.wrapV = BABYLON.Texture.CLAMP_ADDRESSMODE;
      const mat = new BABYLON.StandardMaterial(`${cachePrefix}Mat_${i}`, scene);
      mat.diffuseTexture = tex;
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      if (hasAlpha) mat.useAlphaFromDiffuseTexture = true;
      window.mcState.skinMatCache[cacheKey] = mat;
    }
    return window.mcState.skinMatCache[cacheKey];
  });
}

// Builds a triangulated mesh from a bbmodel mesh element, grouping faces by their texture index
// into submeshes — a single element can span several materials (e.g. the flat-color bakes
// GameAssetsController generates, one small solid-color texture per unlinked material).
export function buildMeshElement(
  name: string,
  el: BbModelMeshElement,
  scene: Scene,
  center: [number, number, number],
  materials: Material[],
  textureDims: Array<{ width: number; height: number }>,
): Mesh {
  const positions: number[] = [];
  const uvs: number[] = [];
  const indices: number[] = [];
  let vertexCount = 0;

  const facesByTexture = new Map<number, BbModelMeshFace[]>();
  for (const face of Object.values(el.faces)) {
    const texIdx = face.texture ?? 0;
    if (!facesByTexture.has(texIdx)) facesByTexture.set(texIdx, []);
    facesByTexture.get(texIdx)!.push(face);
  }

  const subMeshRanges: Array<{ materialIndex: number; texIdx: number; start: number; count: number }> = [];
  let materialIndex = 0;
  for (const [texIdx, faces] of facesByTexture) {
    const dims = textureDims[texIdx] ?? { width: 16, height: 16 };
    const start = indices.length;
    for (const face of faces) {
      const corners = face.vertices;
      if (corners.length < 3) continue;
      const base = vertexCount;
      for (const vid of corners) {
        const p = el.vertices[vid];
        if (!p) continue;
        positions.push(
          (p[0] - center[0]) * BBMODEL_SCALE,
          (p[1] - center[1]) * BBMODEL_SCALE,
          (p[2] - center[2]) * BBMODEL_SCALE,
        );
        const uv = face.uv[vid] ?? [0, 0];
        uvs.push(uv[0] / dims.width, 1 - uv[1] / dims.height);
        vertexCount++;
      }
      for (let i = 1; i < corners.length - 1; i++) indices.push(base, base + i, base + i + 1);
    }
    subMeshRanges.push({ materialIndex, texIdx, start, count: indices.length - start });
    materialIndex++;
  }

  const mesh = new BABYLON.Mesh(name, scene);
  const vertexData = new BABYLON.VertexData();
  vertexData.positions = positions;
  vertexData.uvs = uvs;
  vertexData.indices = indices;
  const normals: number[] = [];
  BABYLON.VertexData.ComputeNormals(positions, indices, normals);
  vertexData.normals = normals;
  vertexData.applyToMesh(mesh);
  mesh.isPickable = false;

  if (subMeshRanges.length <= 1) {
    mesh.material = materials[subMeshRanges[0]?.texIdx ?? 0] ?? materials[0] ?? null;
    return mesh;
  }

  const multiMat = new BABYLON.MultiMaterial(`${name}_mm`, scene);
  subMeshRanges.forEach((r) => multiMat.subMaterials.push(materials[r.texIdx] ?? materials[0] ?? null));
  mesh.subMeshes = [];
  const vertexTotal = positions.length / 3;
  subMeshRanges.forEach((r) => {
    new BABYLON.SubMesh(r.materialIndex, 0, vertexTotal, r.start, r.count, mesh);
  });
  mesh.material = multiMat;
  return mesh;
}
