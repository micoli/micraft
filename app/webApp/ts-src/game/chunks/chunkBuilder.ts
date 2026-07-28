import type { Scene, Mesh, Material } from "@babylonjs/core";

const MC_NORMS = [
  [0, 0, 1], // 0 south
  [0, 0, -1], // 1 north
  [1, 0, 0], // 2 east
  [-1, 0, 0], // 3 west
  [0, 1, 0], // 4 top
  [0, -1, 0], // 5 bottom
];
const FACE_SHADES = [0.88, 0.88, 0.75, 0.75, 1.0, 0.65];

// For rotation r (CW quarters), which SOURCE face provides the texture for display face fd?
// Horizontal faces 0=south,1=north,2=east,3=west rotate; top/bottom stay.
// rot=1 (90° CW): south←west(3), north←east(2), east←south(0), west←north(1)
const ROT_SOURCE: number[][] = [
  [0, 1, 2, 3, 4, 5], // rot=0: identity
  [3, 2, 0, 1, 4, 5], // rot=1 (90° CW)
  [1, 0, 3, 2, 4, 5], // rot=2 (180°)
  [2, 3, 1, 0, 4, 5], // rot=3 (270° CW)
];

// Compute 4 vertices for face fd of a bbmodel element defined by from/to (0-16 coords → 0-1).
function vertsFromElement(from: [number, number, number], to: [number, number, number], fd: number): Float32Array {
  const [fx, fy, fz] = from.map((v) => v / 16) as [number, number, number];
  const [tx, ty, tz] = to.map((v) => v / 16) as [number, number, number];
  switch (fd) {
    case 0:
      return new Float32Array([fx, fy, tz, tx, fy, tz, tx, ty, tz, fx, ty, tz]); // south z=to
    case 1:
      return new Float32Array([tx, fy, fz, fx, fy, fz, fx, ty, fz, tx, ty, fz]); // north z=from
    case 2:
      return new Float32Array([tx, fy, tz, tx, fy, fz, tx, ty, fz, tx, ty, tz]); // east  x=to
    case 3:
      return new Float32Array([fx, fy, fz, fx, fy, tz, fx, ty, tz, fx, ty, fz]); // west  x=from
    case 4:
      return new Float32Array([fx, ty, tz, tx, ty, tz, tx, ty, fz, fx, ty, fz]); // top   y=to
    default:
      return new Float32Array([fx, fy, fz, tx, fy, fz, tx, fy, tz, fx, fy, tz]); // bottom y=from
  }
}

// Rotate verts 90° CW around Y at (0.5,_,0.5): [x,y,z] → [z, y, 1-x]
function rotateVerts90CW(v: Float32Array): Float32Array {
  const r = new Float32Array(12);
  for (let k = 0; k < 4; k++) {
    r[k * 3] = v[k * 3 + 2];
    r[k * 3 + 1] = v[k * 3 + 1];
    r[k * 3 + 2] = 1 - v[k * 3];
  }
  return r;
}

function rotateVerts(v: Float32Array, rotation: number): Float32Array {
  let r = v;
  for (let i = 0; i < rotation; i++) r = rotateVerts90CW(r);
  return r;
}

// --- Pre-baked per-faceMat lookup (built once when block defs are ready) ---
// faceMat = (typeOrd * 4 + rotation) * 6 + fd

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

// faceTable[faceMat] = list of FaceInfo to emit (one per element that has this face)
let faceTable: (FaceInfo[] | null)[] = [];

const CROSS_SPRITE_VERTS = new Float32Array([0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1]);

// Slope geometry for rotation=0 (ascending toward south/+Z: y=0 at north, y=1 at south).
// Other rotations are derived by rotateVerts applied repeatedly.
// fd: 0=south(back high wall), 1=north(none/null), 2=east triangle, 3=west triangle,
//     4=slope top diagonal, 5=bottom flat
const SLOPE_BASE_VERTS: (Float32Array | null)[] = [
  new Float32Array([0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1]), // fd=0 south wall (high end)
  null, // fd=1 north: open at low end
  new Float32Array([1, 0, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1]), // fd=2 east triangle (degenerate quad)
  new Float32Array([0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 1]), // fd=3 west triangle (degenerate quad)
  new Float32Array([0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0]), // fd=4 slope top diagonal
  new Float32Array([0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1]), // fd=5 bottom flat
];

// Normals for slope faces (per fd): slope top normal is special (diagonal)
const SLOPE_TOP_NORMS = [
  [0, 0.7071, -0.7071], // rot=0: ascending south, outward normal up-north
  [-0.7071, 0.7071, 0], // rot=1: ascending east, outward normal up-west
  [0, 0.7071, 0.7071], // rot=2: ascending north, outward normal up-south
  [0.7071, 0.7071, 0], // rot=3: ascending west, outward normal up-east
];

function buildFaceTable(): void {
  faceTable = [];
  for (let typeOrd = 0; typeOrd < 512; typeOrd++) {
    const blockDef = window.mc.getBlockDef(typeOrd);
    if (!blockDef) continue;
    const isCross = blockDef.renderType === "cross_sprite";
    const isSlope = blockDef.renderType === "slope";

    for (let rotation = 0; rotation < 4; rotation++) {
      for (let fd = 0; fd < 6; fd++) {
        const faceMat = (typeOrd * 4 + rotation) * 6 + fd;
        const infos: FaceInfo[] = [];

        if (isSlope) {
          // Slope geometry: use pre-baked base verts rotated by `rotation`
          const baseVerts = SLOPE_BASE_VERTS[fd];
          if (baseVerts !== null) {
            const verts = rotation === 0 ? baseVerts : rotateVerts(baseVerts, rotation);
            // Pick texture from element 0, faces — prefer the matching faceDir
            const srcFd = ROT_SOURCE[rotation][fd];
            const fi = blockDef.elements[0]?.faces[srcFd] ?? blockDef.elements[0]?.faces.find((f) => f != null) ?? null;
            if (fi) {
              let nx: number, ny: number, nz: number;
              if (fd === 4) {
                // Slope top: special diagonal normal rotated by rotation
                [nx, ny, nz] = SLOPE_TOP_NORMS[rotation];
              } else {
                [nx, ny, nz] = MC_NORMS[fd];
              }
              infos.push({
                matKey: fi.matKey,
                uv: new Float32Array(fi.uv),
                shade: fd === 4 ? 0.9 : FACE_SHADES[fd],
                normX: nx,
                normY: ny,
                normZ: nz,
                verts,
                isCrossSprite: false,
              });
            }
          }
        } else if (isCross) {
          // Cross sprites: only fd=0 slot, emitted as two diagonal quads; rotation ignored
          if (fd === 0 && rotation === 0) {
            const fi = blockDef.faces[0]?.find((f) => f != null) ?? null;
            if (fi)
              infos.push({
                matKey: fi.matKey,
                uv: new Float32Array(fi.uv),
                shade: 0.8,
                normX: 0,
                normY: 1,
                normZ: 0,
                verts: CROSS_SPRITE_VERTS,
                isCrossSprite: true,
              });
          }
        } else {
          for (let elemIdx = 0; elemIdx < blockDef.elements.length; elemIdx++) {
            const elem = blockDef.elements[elemIdx];
            // The source face key for this slot after rotation
            const srcFd = ROT_SOURCE[rotation][fd];
            const fi = elem.faces[srcFd];
            if (!fi) continue;
            const [nx, ny, nz] = MC_NORMS[fd];
            const rawVerts = vertsFromElement(elem.from, elem.to, fd);
            const verts = rotation === 0 ? rawVerts : rotateVerts(rawVerts, rotation);
            infos.push({
              matKey: fi.matKey,
              uv: new Float32Array(fi.uv),
              shade: FACE_SHADES[fd],
              normX: nx,
              normY: ny,
              normZ: nz,
              verts,
              isCrossSprite: false,
            });
          }
        }

        if (infos.length > 0) faceTable[faceMat] = infos;
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

// --- Face buffer (shared with Kotlin via window.__mcFB / window.__mcFI) ---
// Kotlin writes face data here (jsChunkFaceAppend); chunkEnd processes the whole
// buffer in one tight loop — eliminates per-face JS function-call overhead.

const FACE_BUF_SLOTS = 600_000; // 5 ints × up to 120k faces per chunk

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

export function registerChunks(): Pick<
  McBindings,
  "disposeChunk" | "chunkBegin" | "chunkFace" | "chunkProcessFaces" | "chunkEnd"
> {
  window.mcState.chunks = {};

  return {
    disposeChunk,

    chunkBegin: (cx: number, cz: number): void => {
      if (faceTable.length === 0) buildFaceTable();
      if (__mcBuf) {
        for (const mk of Object.keys(__mcBuf.groups)) releaseGroup(__mcBuf.groups[mk]);
      }
      __mcBuf = { key: `${cx},${cz}`, groups: {} };
      if (!window.__mcFB) window.__mcFB = new Int32Array(FACE_BUF_SLOTS);
      window.__mcFI = 0;
    },

    // no-op stub — Kotlin uses jsChunkFaceAppend (writes directly to __mcFB)
    chunkFace: (_wx: number, _wy: number, _wz: number, _faceMat: number, _ao: number): void => {},

    // Process a slice of __mcFB into FaceGroups (geometry work only, no GPU upload).
    // cursor: face index (not byte offset); maxFaces: max to process this call.
    // Returns actual faces processed. Call repeatedly until return value < maxFaces
    // (or until cursor × 5 >= __mcFI) then call chunkEnd for GPU upload.
    chunkProcessFaces: (cursor: number, maxFaces: number): number => {
      const buf = __mcBuf!;
      const fb = window.__mcFB!;
      const fi = window.__mcFI ?? 0;
      const startI = cursor * 5;
      const endI = Math.min(startI + maxFaces * 5, fi);
      const grp = buf.groups;
      for (let i = startI; i < endI; i += 5) {
        const wx = fb[i],
          wy = fb[i + 1],
          wz = fb[i + 2],
          faceMat = fb[i + 3],
          ao = fb[i + 4];
        const infos = faceTable[faceMat];
        if (!infos) continue;
        for (const info of infos) {
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
        }
      }
      return ((endI - startI) / 5) | 0;
    },

    // GPU upload only — call after chunkProcessFaces has consumed all faces.
    chunkEnd: (scene: Scene, materials: Record<string, Material>): void => {
      const buf = __mcBuf!;
      __mcBuf = null;

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
        mesh.doNotSyncBoundingInfo = true;
        mesh.refreshBoundingInfo();
        mesh.freezeWorldMatrix();
        meshes.push(mesh);
        releaseGroup(g);
      }
      window.mcState.chunks[buf.key] = meshes;
    },
  };
}
