import type { Scene, Mesh, Material } from "@babylonjs/core";

// Vertex offsets for each face direction (4 vertices per face, CCW winding)
const MC_VERTS: [number, number, number][][] = [
  // 0: Front +Z / south
  [
    [-0.5, -0.5, 0.5],
    [0.5, -0.5, 0.5],
    [0.5, 0.5, 0.5],
    [-0.5, 0.5, 0.5],
  ],
  // 1: Back  -Z / north
  [
    [0.5, -0.5, -0.5],
    [-0.5, -0.5, -0.5],
    [-0.5, 0.5, -0.5],
    [0.5, 0.5, -0.5],
  ],
  // 2: Right +X / east
  [
    [0.5, -0.5, 0.5],
    [0.5, -0.5, -0.5],
    [0.5, 0.5, -0.5],
    [0.5, 0.5, 0.5],
  ],
  // 3: Left  -X / west
  [
    [-0.5, -0.5, -0.5],
    [-0.5, -0.5, 0.5],
    [-0.5, 0.5, 0.5],
    [-0.5, 0.5, -0.5],
  ],
  // 4: Top   +Y
  [
    [-0.5, 0.5, 0.5],
    [0.5, 0.5, 0.5],
    [0.5, 0.5, -0.5],
    [-0.5, 0.5, -0.5],
  ],
  // 5: Bottom-Y
  [
    [-0.5, -0.5, -0.5],
    [0.5, -0.5, -0.5],
    [0.5, -0.5, 0.5],
    [-0.5, -0.5, 0.5],
  ],
];
const MC_NORMS: [number, number, number][] = [
  [0, 0, 1],
  [0, 0, -1],
  [1, 0, 0],
  [-1, 0, 0],
  [0, 1, 0],
  [0, -1, 0],
];

// Directional face shading multipliers (fd 0-5: +Z,-Z,+X,-X,+Y,-Y)
// Applied on top of the hemispheric light, so values stay close to 1.0 (subtle contrast)
const FACE_SHADES = [0.95, 0.95, 0.85, 0.85, 1.0, 0.8];

interface FaceGroup {
  p: number[];
  n: number[];
  u: number[];
  c: number[];
  i: number[];
  v: number;
}
interface ChunkBuf {
  key: string;
  groups: Record<string, FaceGroup>;
}

let __mcBuf: ChunkBuf | null = null;

function emitCrossSprite(
  wx: number,
  wy: number,
  wz: number,
  mk: string,
  grp: Record<string, FaceGroup>,
  uv: number[],
  ao: number,
): void {
  if (!grp[mk]) grp[mk] = { p: [], n: [], u: [], c: [], i: [], v: 0 };
  const g = grp[mk];
  const QUADS: [number, number, number][][] = [
    [
      [-0.5, -0.5, -0.5],
      [0.5, -0.5, 0.5],
      [0.5, 0.5, 0.5],
      [-0.5, 0.5, -0.5],
    ],
    [
      [0.5, -0.5, -0.5],
      [-0.5, -0.5, 0.5],
      [-0.5, 0.5, 0.5],
      [0.5, 0.5, -0.5],
    ],
  ];
  const shade = 0.8;
  for (const q of QUADS) {
    for (let k = 0; k < 4; k++) {
      g.p.push(wx + q[k][0], wy + q[k][1], wz + q[k][2]);
      g.n.push(0, 1, 0);
      g.u.push(uv[k * 2], uv[k * 2 + 1]);
      const aoV = (ao >> (k * 4)) & 0xf;
      const brightness = shade * (1.0 - (aoV / 15.0) * 0.4);
      g.c.push(brightness, brightness, brightness, 1.0);
    }
    const b = g.v;
    g.i.push(b, b + 1, b + 2, b, b + 2, b + 3);
    g.v += 4;
  }
}

function disposeChunk(key: string): void {
  const meshes = window.__mcChunks[key];
  if (meshes) {
    (meshes as InstanceType<typeof BABYLON.AbstractMesh>[]).forEach((m) => m.dispose());
    delete window.__mcChunks[key];
  }
}

export function registerChunks(): void {
  window.__mcChunks = {};
  window.mcDisposeChunk = disposeChunk;

  window.mcChunkBegin = (cx: number, cz: number): void => {
    __mcBuf = { key: `${cx},${cz}`, groups: {} };
  };

  window.mcChunkFace = (wx: number, wy: number, wz: number, faceMat: number, ao: number): void => {
    if (!__mcBuf) return;
    const fd = faceMat % 6;
    const typeOrd = (faceMat - fd) / 6;
    const grp = __mcBuf.groups;

    const blockDef = window.mcGetBlockDef(typeOrd);
    if (!blockDef) return;

    if (blockDef.renderType === "cross_sprite") {
      if (fd !== 0) return;
      const faceInfo = blockDef.faces[0]; // cross-sprite uses the "south" face definition
      if (!faceInfo) return;
      emitCrossSprite(wx, wy, wz, faceInfo.matKey, grp, faceInfo.uv, ao);
      return;
    }

    const faceInfo = blockDef.faces[fd];
    if (!faceInfo) return;

    const mk = faceInfo.matKey;
    if (!grp[mk]) grp[mk] = { p: [], n: [], u: [], c: [], i: [], v: 0 };
    const g = grp[mk];
    const vt = MC_VERTS[fd];
    const nm = MC_NORMS[fd];
    const shade = FACE_SHADES[fd];
    for (let k = 0; k < 4; k++) {
      g.p.push(wx + vt[k][0], wy + vt[k][1], wz + vt[k][2]);
      g.n.push(nm[0], nm[1], nm[2]);
      g.u.push(faceInfo.uv[k * 2], faceInfo.uv[k * 2 + 1]);
      const aoV = (ao >> (k * 4)) & 0xf;
      const brightness = shade * (1.0 - (aoV / 15.0) * 0.4);
      g.c.push(brightness, brightness, brightness, 1.0);
    }
    const b = g.v;
    g.i.push(b, b + 1, b + 2, b, b + 2, b + 3);
    g.v += 4;
  };

  window.mcChunkEnd = (scene: Scene, materials: Record<string, Material>): void => {
    const buf = __mcBuf!;
    __mcBuf = null;
    const key = buf.key;
    disposeChunk(key);

    const meshes: Mesh[] = [];
    for (const mk of Object.keys(buf.groups)) {
      const g = buf.groups[mk];
      if (g.v === 0) continue;
      const mesh = new BABYLON.Mesh(`ck${key}${mk}`, scene);
      const vd = new BABYLON.VertexData();
      vd.positions = g.p;
      vd.normals = g.n;
      vd.uvs = g.u;
      vd.colors = g.c;
      vd.indices = g.i;
      vd.applyToMesh(mesh, false);
      mesh.material = materials[mk] ?? null;
      mesh.freezeWorldMatrix();
      mesh.isPickable = false;
      mesh.doNotSyncBoundingInfo = true;
      meshes.push(mesh);
    }
    window.__mcChunks[key] = meshes;
  };
}
