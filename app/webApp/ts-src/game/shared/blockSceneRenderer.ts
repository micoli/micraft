import type { Scene, StandardMaterial, TransformNode } from "@babylonjs/core";

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
 */
export function setupBlockScene(scene: Scene, ordinal: number): TransformNode {
  const B = window.BABYLON!;

  const blockDef = window.mc?.getBlockDef?.(ordinal) as McBlockDef | null;
  const elements = blockDef?.elements ?? [];

  const body = elements[0];
  const bf = body?.from ?? ([0, 0, 0] as [number, number, number]);
  const bt = body?.to ?? ([16, 16, 16] as [number, number, number]);
  const bW = (bt[0] - bf[0]) / 16;
  const bH = (bt[1] - bf[1]) / 16;
  const bD = (bt[2] - bf[2]) / 16;
  const cx = (bf[0] + bt[0]) / 2 / 16;
  const cy = (bf[1] + bt[1]) / 2 / 16;
  const cz = (bf[2] + bt[2]) / 2 / 16;

  const cam = new B.ArcRotateCamera("cam", -Math.PI * 0.25, Math.PI / 3.5, 2.5, B.Vector3.Zero(), scene);
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

  for (let ei = 0; ei < elements.length; ei++) {
    const elem = elements[ei];
    const f = elem.from,
      t = elem.to;
    const x0 = f[0] / 16,
      y0 = f[1] / 16,
      z0 = f[2] / 16;
    const x1 = t[0] / 16,
      y1 = t[1] / 16,
      z1 = t[2] / 16;
    const W = x1 - x0,
      H = y1 - y0,
      D = z1 - z0;
    const mx = (x0 + x1) / 2,
      my = (y0 + y1) / 2,
      mz = (z0 + z1) / 2;

    const firstFace = elem.faces.find((fi) => fi != null);
    const url = firstFace ? getUrl(firstFace.matKey) : null;
    if (!url) continue;
    const box = B.MeshBuilder.CreateBox(`e${ei}`, { width: W, height: H, depth: D }, scene);
    box.parent = root;
    box.position = new B.Vector3(mx - cx, my - cy, mz - cz);
    box.material = ensureMat(url, false);
  }

  return root;
}
