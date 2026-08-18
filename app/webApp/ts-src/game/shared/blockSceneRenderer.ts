import type { Scene, StandardMaterial, TransformNode } from "@babylonjs/core";
import { MC_NORMS, vertsFromElement } from "../lib/chunkBuilder";

export const SLOPE_VERTS: (number[] | null)[] = [
  [0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1],
  null,
  [1, 0, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1],
  [0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 1],
  [0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0],
  [0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1],
];

export const CORNER_VERTS: (number[] | null)[] = [
  [1, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 0],
  [1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0],
  null,
  [0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0],
  [0, 1, 0, 0, 1, 1, 1, 1, 0, 1, 1, 0],
  [0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 1],
];

/**
 * Builds camera, light, and block meshes into an existing scene.
 * Returns the root TransformNode (useful for animation).
 * Caller owns engine + scene lifecycle.
 *
 * [colorHex] ("RRGGBB", no '#') renders the block in plain color instead of its texture.
 */
export function setupBlockScene(scene: Scene, ordinal: number, colorHex?: string | null): TransformNode {
  const B = window.BABYLON!;

  const blockDef = window.mc?.getBlockDef?.(ordinal) as McBlockDef | null;
  const elements = blockDef?.elements ?? [];

  let minX = 0,
    minY = 0,
    minZ = 0,
    maxX = 16,
    maxY = 16,
    maxZ = 16;
  let cx = 0.5,
    cy = 0.5,
    cz = 0.5;
  if (elements.length > 0) {
    minX = Math.min(...elements.map((e) => e.from[0]));
    minY = Math.min(...elements.map((e) => e.from[1]));
    minZ = Math.min(...elements.map((e) => e.from[2]));
    maxX = Math.max(...elements.map((e) => e.to[0]));
    maxY = Math.max(...elements.map((e) => e.to[1]));
    maxZ = Math.max(...elements.map((e) => e.to[2]));
    cx = elements.reduce((s, e) => s + (e.from[0] + e.to[0]) / 2, 0) / elements.length / 16;
    cy = elements.reduce((s, e) => s + (e.from[1] + e.to[1]) / 2, 0) / elements.length / 16;
    cz = elements.reduce((s, e) => s + (e.from[2] + e.to[2]) / 2, 0) / elements.length / 16;
  }
  const bW = (maxX - minX) / 16;
  const bH = (maxY - minY) / 16;
  const bD = (maxZ - minZ) / 16;

  const camAlpha = -Math.PI * 0.25;
  const camBeta = Math.PI / 4;
  const cam = new B.ArcRotateCamera("cam", camAlpha, camBeta, 2.5, B.Vector3.Zero(), scene);
  cam.radius = Math.max(2.5, Math.max(bW, bH, bD) * 2.2);

  const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 1), scene);
  light.intensity = 1.0;
  light.groundColor = new B.Color3(0.25, 0.25, 0.25);

  const root = new B.TransformNode("root", scene);

  const texs: McBlockTextureDef[] = window.mc?.getBlockTextures?.() ?? [];
  const matCache = new Map<string, StandardMaterial>();

  const getUrl = (matKey: string): string | null => {
    const name = matKey.replace(":biome_tint", "");
    return texs.find((t) => t.name === name)?.url ?? null;
  };

  const ensureMat = (url: string, twoSided = false) => {
    const key = twoSided ? url + "_2s" : url;
    if (!matCache.has(key)) {
      const mat = new B.StandardMaterial("m_" + key, scene);
      const tex = new B.Texture(url, scene, false, true, B.Texture.NEAREST_SAMPLINGMODE);
      tex.hasAlpha = true;
      mat.diffuseTexture = tex;
      mat.useAlphaFromDiffuseTexture = true;
      mat.specularColor = new B.Color3(0, 0, 0);
      mat.backFaceCulling = !twoSided;
      matCache.set(key, mat);
    }
    return matCache.get(key)!;
  };

  let plainMat: StandardMaterial | null = null;
  if (colorHex) {
    const n = parseInt(colorHex, 16);
    plainMat = new B.StandardMaterial("m_plain_" + colorHex, scene);
    plainMat.diffuseColor = new B.Color3(((n >> 16) & 0xff) / 255, ((n >> 8) & 0xff) / 255, (n & 0xff) / 255);
    plainMat.specularColor = new B.Color3(0.1, 0.1, 0.1);
  }

  const isCrossSprite = blockDef?.renderType === "cross_sprite";

  if (isCrossSprite) {
    const fi = blockDef!.faces[0]?.find((f) => f != null) ?? null;
    const url = fi ? getUrl(fi.matKey) : null;
    if (fi && url) {
      const CROSS_QUADS = [
        [0, 0, 0, 1, 0, 1, 1, 1, 1, 0, 1, 0],
        [1, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 0],
      ];
      for (let qi = 0; qi < CROSS_QUADS.length; qi++) {
        const q = CROSS_QUADS[qi];
        const positions = [
          q[0] - 0.5,
          q[1] - 0.5,
          q[2] - 0.5,
          q[3] - 0.5,
          q[4] - 0.5,
          q[5] - 0.5,
          q[6] - 0.5,
          q[7] - 0.5,
          q[8] - 0.5,
          q[9] - 0.5,
          q[10] - 0.5,
          q[11] - 0.5,
        ];
        const indices = [0, 1, 2, 0, 2, 3];
        const normals: number[] = [];
        B.VertexData.ComputeNormals(positions, indices, normals);
        const vd = new B.VertexData();
        vd.positions = positions;
        vd.indices = indices;
        vd.normals = normals;
        vd.uvs = fi.uv;
        const mesh = new B.Mesh(`cross${qi}`, scene);
        mesh.parent = root;
        vd.applyToMesh(mesh);
        mesh.material = ensureMat(url, true);
      }
    }
    return root;
  }

  const QUAD_INDICES = [0, 1, 2, 0, 2, 3];

  for (let ei = 0; ei < elements.length; ei++) {
    const elem = elements[ei];
    for (let fd = 0; fd < 6; fd++) {
      const fi = elem.faces[fd];
      if (!fi) continue;
      const mat = plainMat ?? (getUrl(fi.matKey) ? ensureMat(getUrl(fi.matKey)!, true) : null);
      if (!mat) continue;

      const verts = vertsFromElement(elem.from, elem.to, fd);
      const positions: number[] = [];
      for (let k = 0; k < 4; k++) {
        positions.push(verts[k * 3] - cx, verts[k * 3 + 1] - cy, verts[k * 3 + 2] - cz);
      }
      const [nx, ny, nz] = MC_NORMS[fd];
      const normals: number[] = [nx, ny, nz, nx, ny, nz, nx, ny, nz, nx, ny, nz];

      const vd = new B.VertexData();
      vd.positions = positions;
      vd.indices = QUAD_INDICES;
      vd.normals = normals;
      vd.uvs = fi.uv;
      const quad = new B.Mesh(`e${ei}f${fd}`, scene);
      quad.parent = root;
      vd.applyToMesh(quad);
      quad.material = mat;
    }
  }

  return root;
}
