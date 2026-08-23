import type { Material, Mesh, Scene, Vector4 } from "@babylonjs/core";

// bbmodel coordinates are in Blockbench pixel units, 16px = 1 block.
const BBMODEL_SCALE = 1 / 16;

// 3x3 rotation matrix (row-major, [row][col] flattened) matching Blockbench/THREE.js's Euler
// composition — THREE's default `Format.euler_order` is "ZYX" (Matrix4.makeRotationFromEuler,
// ZYX branch), which is NOT the same axis-composition order as Babylon's
// Quaternion.RotationYawPitchRoll (Y-X-Z). The two only agree when a single axis is non-zero; a
// compound rotation (e.g. [90, 90, 0]) needs the matching order to land where Blockbench puts it.
export function eulerZYXMatrix(rotation: [number, number, number]): number[] {
  const DEG = Math.PI / 180;
  const [rx, ry, rz] = rotation;
  const a = Math.cos(rx * DEG),
    b = Math.sin(rx * DEG);
  const c = Math.cos(ry * DEG),
    d = Math.sin(ry * DEG);
  const e = Math.cos(rz * DEG),
    f = Math.sin(rz * DEG);
  // prettier-ignore
  return [
    c * e,           b * e * d - a * f, a * e * d + b * f,
    c * f,           b * f * d + a * e, a * f * d - b * e,
    -d,              b * c,             a * c,
  ];
}

function applyMatrix3(m: number[], p: [number, number, number]): [number, number, number] {
  const [x, y, z] = p;
  return [m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z];
}

// A mesh element's vertices are authored local to its own `origin` (Blockbench's per-element
// pivot) — this bakes them to absolute bbmodel space, rotating around that pivot first when the
// non-destructive rotate tool was used (e.g. a crossguard duplicated and rotated from one arm).
export function applyElementPivot(
  p: [number, number, number],
  origin: [number, number, number] | undefined,
  rotation: [number, number, number] | undefined,
): [number, number, number] {
  const [ox, oy, oz] = origin ?? [0, 0, 0];
  if (!rotation || (rotation[0] === 0 && rotation[1] === 0 && rotation[2] === 0)) {
    return [p[0] + ox, p[1] + oy, p[2] + oz];
  }
  const rotated = applyMatrix3(eulerZYXMatrix(rotation), p);
  return [rotated[0] + ox, rotated[1] + oy, rotated[2] + oz];
}

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
  if (bbmodel.textures.length === 0) {
    // Untextured import (e.g. a bare Blockbench mesh never baked/UV-mapped) — fall back to a
    // flat gray material instead of leaving mesh.material null (Babylon's stark-white default).
    const cacheKey = `${cachePrefix}_fallback`;
    if (!window.mcState.skinMatCache[cacheKey]) {
      const mat = new BABYLON.StandardMaterial(`${cachePrefix}Mat_fallback`, scene);
      mat.diffuseColor = new BABYLON.Color3(0.5, 0.5, 0.5);
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      mat.backFaceCulling = false;
      mat.twoSidedLighting = true;
      window.mcState.skinMatCache[cacheKey] = mat;
    }
    return [window.mcState.skinMatCache[cacheKey]];
  }
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
      if (hasAlpha) {
        // Armor/skin textures are binary alpha (fully opaque or fully transparent, no soft
        // edges) — alpha-test keeps depth-write on, so nearby overlapping thin elements (e.g.
        // helmet horns) occlude each other correctly instead of blending through like alpha-blend
        // would (blend disables depth-write, so overlapping alpha-flagged meshes render see-through
        // regardless of their own pixel opacity).
        mat.useAlphaFromDiffuseTexture = true;
        mat.transparencyMode = BABYLON.Material.MATERIAL_ALPHATEST;
        mat.alphaCutOff = 0.5;
      }
      // Mesh-type elements (arbitrary geometry, e.g. Blender exports) aren't guaranteed
      // consistent triangle winding — backface culling would invisibly drop some of their faces.
      mat.backFaceCulling = false;
      // Without this, back faces are lit using the front-facing normal, so thin geometry
      // (blades, bowstrings) looks wrongly shaded from the far side instead of just visible.
      mat.twoSidedLighting = true;
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
        const raw = el.vertices[vid];
        if (!raw) continue;
        const p = applyElementPivot(raw, el.origin, el.rotation);
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

// BabylonJS's CreateBox ties the east(+X)/west(-X) side faces' UV assignment to the wrong local
// axis internally: their "u" ends up driven by the vertical (Y) position and "v" by the z-side,
// the opposite of south/north/top/bottom. This isn't fixable via the `faceUV` Vector4 passed to
// CreateBox — those two faces' vertices share UV parameters pairwise in a way that makes the
// correct per-corner assignment mathematically unreachable through that single rect. Instead,
// patch the UV buffer directly, per-vertex, after creation — using each vertex's own normal/position
// to identify which corner it is, so this doesn't depend on CreateBox's internal vertex ordering.
export function fixBoxSideFaceUV(mesh: Mesh, eastRect: Vector4, westRect: Vector4): void {
  const positions = mesh.getVerticesData(BABYLON.VertexBuffer.PositionKind);
  const normals = mesh.getVerticesData(BABYLON.VertexBuffer.NormalKind);
  const uvs = mesh.getVerticesData(BABYLON.VertexBuffer.UVKind);
  if (!positions || !normals || !uvs) return;
  const vertexCount = positions.length / 3;
  for (let i = 0; i < vertexCount; i++) {
    const nx = normals[i * 3];
    if (Math.abs(nx) < 0.5) continue; // not an east/west face vertex
    // west's rect content belongs on the -X side, east's on +X — matching the rig's own bone
    // positions (leftArm is at negative X, rightArm at positive X; confirmed from the bbmodel's
    // group origins), the only ground truth independent of which way a viewer navigates the camera.
    const isEast = nx > 0;
    const rect = isEast ? eastRect : westRect;
    const y = positions[i * 3 + 1];
    const z = positions[i * 3 + 2];
    // Viewing a face from outside (normal pointing at the viewer), screen-right on the east face
    // is world -Z (north) and screen-right on the west face is world +Z (south) — right-hand rule
    // on each face's own outward normal/up pair. u increases toward screen-right, so east maps
    // z<0 to max-u and west maps z>=0 to max-u.
    const zPositive = isEast ? z < 0 : z >= 0;
    uvs[i * 2] = zPositive ? rect.z : rect.x;
    uvs[i * 2 + 1] = y >= 0 ? rect.w : rect.y;
  }
  mesh.setVerticesData(BABYLON.VertexBuffer.UVKind, uvs);
}

// BabylonJS's CreateBox has the same wrong-axis problem on the top/bottom faces as it does on
// east/west (see fixBoxSideFaceUV above): u ends up driven by Z and v by X instead of u by X and
// v by Z. Patched the same way — per-vertex, from real position/normal. Both axes empirically
// verified against Blockbench (colored marker textures): top's u decreases with +X and its v
// decreases with +Z; down's u is mirrored on X (matching a camera looking up from underneath).
export function fixBoxTopBottomFaceUV(mesh: Mesh, upRect: Vector4, downRect: Vector4): void {
  const positions = mesh.getVerticesData(BABYLON.VertexBuffer.PositionKind);
  const normals = mesh.getVerticesData(BABYLON.VertexBuffer.NormalKind);
  const uvs = mesh.getVerticesData(BABYLON.VertexBuffer.UVKind);
  if (!positions || !normals || !uvs) return;
  const vertexCount = positions.length / 3;
  for (let i = 0; i < vertexCount; i++) {
    const ny = normals[i * 3 + 1];
    if (Math.abs(ny) < 0.5) continue; // not a top/bottom face vertex
    const isUp = ny > 0;
    const rect = isUp ? upRect : downRect;
    const x = positions[i * 3];
    const z = positions[i * 3 + 2];
    //const xPositive = isUp ? x >= 0 : x < 0;
    const xPositive = isUp ? x < 0 : x >= 0;
    uvs[i * 2] = xPositive ? rect.z : rect.x;
    uvs[i * 2 + 1] = z < 0 ? rect.y : rect.w;
  }
  mesh.setVerticesData(BABYLON.VertexBuffer.UVKind, uvs);
}
