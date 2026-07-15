import type { Scene, Mesh, Material } from "@babylonjs/core";

// Pre-baked vertex offsets as flat Float32Array (4 verts × 3 floats = 12 values per face).
// Blocks are floor-aligned: integer (wx,wy,wz) is the block's lower corner, spanning [wx,wx+1]×[wy,wy+1]×[wz,wz+1].
const MC_VERTS: Float32Array[] = [
  new Float32Array([0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1]), // +Z south
  new Float32Array([1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0]), // -Z north
  new Float32Array([1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1]), // +X east
  new Float32Array([0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0]), // -X west
  new Float32Array([0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0]), // +Y top
  new Float32Array([0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1]), // -Y bottom
];
const MC_NORMS = [
  [0, 0, 1],
  [0, 0, -1],
  [1, 0, 0],
  [-1, 0, 0],
  [0, 1, 0],
  [0, -1, 0],
];
const FACE_SHADES = [0.88, 0.88, 0.75, 0.75, 1.0, 0.65];

// --- Pre-baked per-faceMat lookup (built once when block defs are ready) ---

interface FaceInfo {
  matKey: string;
  uv: Float32Array;
  shade: number;
  normX: number;
  normY: number;
  normZ: number;
  verts: Float32Array;
  isCrossSprite: boolean;
}

let faceTable: (FaceInfo | null)[] = [];

function buildFaceTable(): void {
  faceTable = [];
  for (let typeOrd = 0; typeOrd < 512; typeOrd++) {
    const blockDef = window.mc.getBlockDef(typeOrd);
    if (!blockDef) continue;
    const isCross = blockDef.renderType === "cross_sprite";
    for (let fd = 0; fd < 6; fd++) {
      const faceMat = typeOrd * 6 + fd;
      if (isCross) {
        if (fd === 0) {
          const fi = blockDef.faces.find((f) => f != null) ?? null;
          if (fi)
            faceTable[faceMat] = {
              matKey: fi.matKey,
              uv: new Float32Array(fi.uv),
              shade: 0.8,
              normX: 0,
              normY: 1,
              normZ: 0,
              verts: MC_VERTS[0],
              isCrossSprite: true,
            };
        }
      } else {
        const fi = blockDef.faces[fd];
        if (fi) {
          const [nx, ny, nz] = MC_NORMS[fd];
          faceTable[faceMat] = {
            matKey: fi.matKey,
            uv: new Float32Array(fi.uv),
            shade: FACE_SHADES[fd],
            normX: nx,
            normY: ny,
            normZ: nz,
            verts: MC_VERTS[fd],
            isCrossSprite: false,
          };
        }
      }
    }
  }
}

// --- TypedArray group pool ---

const GROUP_MAX_VERTS = 65_536; // covers worst-case chunk (all-same-block outer faces ~54k verts)
const GROUP_MAX_IDX = Math.ceil(GROUP_MAX_VERTS * 1.5);

interface FaceGroup {
  p: Float32Array; // positions  v*3
  n: Float32Array; // normals    v*3
  u: Float32Array; // uvs        v*2
  c: Float32Array; // colors     v*4
  i: Int32Array; // indices    faces*6
  v: number; // vertex count
  ic: number; // index count
}

const groupPool: FaceGroup[] = [];

function acquireGroup(): FaceGroup {
  if (groupPool.length > 0) {
    const g = groupPool.pop()!;
    g.v = g.ic = 0;
    return g;
  }
  return {
    p: new Float32Array(GROUP_MAX_VERTS * 3),
    n: new Float32Array(GROUP_MAX_VERTS * 3),
    u: new Float32Array(GROUP_MAX_VERTS * 2),
    c: new Float32Array(GROUP_MAX_VERTS * 4),
    i: new Int32Array(GROUP_MAX_IDX),
    v: 0,
    ic: 0,
  };
}

function releaseGroup(g: FaceGroup): void {
  if (groupPool.length < 24) groupPool.push(g);
}

// --- Emit helpers (indexed TypedArray writes, no push()) ---

function emitQuad(
  g: FaceGroup,
  wx: number,
  wy: number,
  wz: number,
  verts: Float32Array,
  nx: number,
  ny: number,
  nz: number,
  uv: Float32Array,
  shade: number,
  ao: number,
): void {
  if (g.v + 4 > GROUP_MAX_VERTS) return; // safety guard
  const baseV = g.v;
  let pi = baseV * 3;
  let ni = baseV * 3;
  let ui = baseV * 2;
  let ci = baseV * 4;
  for (let k = 0; k < 4; k++) {
    const vk = k * 3;
    g.p[pi++] = wx + verts[vk];
    g.p[pi++] = wy + verts[vk + 1];
    g.p[pi++] = wz + verts[vk + 2];
    g.n[ni++] = nx;
    g.n[ni++] = ny;
    g.n[ni++] = nz;
    g.u[ui++] = uv[k * 2];
    g.u[ui++] = uv[k * 2 + 1];
    const aoV = (ao >> (k * 4)) & 0xf;
    const b = shade * (1.0 - aoV * (0.5 / 15.0));
    g.c[ci++] = b;
    g.c[ci++] = b;
    g.c[ci++] = b;
    g.c[ci++] = 1.0;
  }
  let ii = g.ic;
  g.i[ii++] = baseV;
  g.i[ii++] = baseV + 1;
  g.i[ii++] = baseV + 2;
  g.i[ii++] = baseV;
  g.i[ii++] = baseV + 2;
  g.i[ii++] = baseV + 3;
  g.ic = ii;
  g.v = baseV + 4;
}

const CROSS_QUADS: Float32Array[] = [
  new Float32Array([0, 0, 0, 1, 0, 1, 1, 1, 1, 0, 1, 0]),
  new Float32Array([1, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 0]),
];

function emitCrossSprite(wx: number, wy: number, wz: number, g: FaceGroup, uv: Float32Array, ao: number): void {
  for (const q of CROSS_QUADS) emitQuad(g, wx, wy, wz, q, 0, 1, 0, uv, 0.8, ao);
}

// --- Chunk state ---

interface ChunkBuf {
  key: string;
  groups: Record<string, FaceGroup>;
}
let __mcBuf: ChunkBuf | null = null;

function disposeChunk(key: string): void {
  const meshes = window.mcState.chunks[key];
  if (meshes) {
    (meshes as InstanceType<typeof BABYLON.AbstractMesh>[]).forEach((m) => m.dispose());
    delete window.mcState.chunks[key];
  }
}

export function registerChunks(): Pick<McBindings, "disposeChunk" | "chunkBegin" | "chunkFace" | "chunkEnd"> {
  window.mcState.chunks = {};

  return {
    disposeChunk,

    chunkBegin: (cx: number, cz: number): void => {
      if (faceTable.length === 0) buildFaceTable();
      if (__mcBuf) {
        for (const mk of Object.keys(__mcBuf.groups)) releaseGroup(__mcBuf.groups[mk]);
      }
      __mcBuf = { key: `${cx},${cz}`, groups: {} };
    },

    chunkFace: (wx: number, wy: number, wz: number, faceMat: number, ao: number): void => {
      if (!__mcBuf) return;
      const info = faceTable[faceMat];
      if (!info) return;

      const grp = __mcBuf.groups;
      let g = grp[info.matKey];
      if (!g) {
        g = acquireGroup();
        grp[info.matKey] = g;
      }

      if (info.isCrossSprite) {
        emitCrossSprite(wx, wy, wz, g, info.uv, ao);
      } else {
        emitQuad(g, wx, wy, wz, info.verts, info.normX, info.normY, info.normZ, info.uv, info.shade, ao);
      }
    },

    chunkEnd: (scene: Scene, materials: Record<string, Material>): void => {
      const buf = __mcBuf!;
      __mcBuf = null;
      const t0 = performance.now();
      disposeChunk(buf.key);

      const meshes: Mesh[] = [];
      for (const mk of Object.keys(buf.groups)) {
        const g = buf.groups[mk];
        if (g.v === 0) {
          releaseGroup(g);
          continue;
        }
        const mesh = new BABYLON.Mesh(`ck${buf.key}${mk}`, scene);
        const vd = new BABYLON.VertexData();
        vd.positions = g.p.subarray(0, g.v * 3);
        vd.normals = g.n.subarray(0, g.v * 3);
        vd.uvs = g.u.subarray(0, g.v * 2);
        vd.colors = g.c.subarray(0, g.v * 4);
        vd.indices = g.i.subarray(0, g.ic);
        vd.applyToMesh(mesh, false);
        mesh.material = materials[mk] ?? null;
        mesh.isPickable = false;
        mesh.refreshBoundingInfo();
        mesh.freezeWorldMatrix();
        meshes.push(mesh);
        releaseGroup(g);
      }
      window.mcState.chunks[buf.key] = meshes;
      // console.log(`[mesh-ts] key=${buf.key} groups=${meshes.length} applyMs=${(performance.now() - t0).toFixed(1)}`);
    },
  };
}
