// Off-main-thread chunk geometry builder — moves the CPU-heavy part of chunkProcessFaces
// (face-buffer → per-material typed-array geometry) out of the main thread. GPU upload
// (vd.applyToMesh) stays on the main thread by construction (see chunkWorkerPool.ts /
// jsChunkEndFromWorker in BabylonBindingsChunkWorker.kt) — a Worker has no canvas/WebGL context.
//
// This is a standalone bundle (esbuild entry "build:chunkWorker", see ts-src/package.json),
// loaded via `new Worker(url)` as a classic (non-module) script — same IIFE bundling as
// mc_bindings.js/map.js/admin.js. It has no window.mc / BABYLON access, so it keeps its own copy
// of the pure-geometry logic from chunkBuilder.ts (faceTable, group pool, emit helpers) built
// from a one-time "blockDefs" message instead of window.mc.getBlockDef().
//
// tsconfig's lib list is ["ES2020","DOM"] (no "webworker") to avoid retyping `self` project-wide,
// so the worker global is accessed through this narrow local cast instead.
import { plainMatKey } from "./blockDefs";

interface WorkerScope {
  postMessage(message: unknown, transfer?: Transferable[]): void;
  onmessage: ((ev: MessageEvent) => void) | null;
}
const ctx = self as unknown as WorkerScope;

const MC_NORMS = [
  [0, 0, 1], // 0 south
  [0, 0, -1], // 1 north
  [1, 0, 0], // 2 east
  [-1, 0, 0], // 3 west
  [0, 1, 0], // 4 top
  [0, -1, 0], // 5 bottom
];
const FACE_SHADES = [0.88, 0.88, 0.75, 0.75, 1.0, 0.65];

const ROT_SOURCE: number[][] = [
  [0, 1, 2, 3, 4, 5],
  [3, 2, 0, 1, 4, 5],
  [1, 0, 3, 2, 4, 5],
  [2, 3, 1, 0, 4, 5],
];

function vertsFromElement(from: [number, number, number], to: [number, number, number], fd: number): Float32Array {
  const [fx, fy, fz] = from.map((v) => v / 16) as [number, number, number];
  const [tx, ty, tz] = to.map((v) => v / 16) as [number, number, number];
  switch (fd) {
    case 0:
      return new Float32Array([fx, fy, tz, tx, fy, tz, tx, ty, tz, fx, ty, tz]);
    case 1:
      return new Float32Array([tx, fy, fz, fx, fy, fz, fx, ty, fz, tx, ty, fz]);
    case 2:
      return new Float32Array([tx, fy, tz, tx, fy, fz, tx, ty, fz, tx, ty, tz]);
    case 3:
      return new Float32Array([fx, fy, fz, fx, fy, tz, fx, ty, tz, fx, ty, fz]);
    case 4:
      return new Float32Array([fx, ty, tz, tx, ty, tz, tx, ty, fz, fx, ty, fz]);
    default:
      return new Float32Array([fx, fy, fz, tx, fy, fz, tx, fy, tz, fx, fy, tz]);
  }
}

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

interface FaceInfo {
  matKey: string;
  uv: Float32Array;
  shade: number;
  normX: number;
  normY: number;
  normZ: number;
  verts: Float32Array;
  isCrossSprite: boolean;
  isPlastic: boolean;
}

let faceTable: (FaceInfo[] | null)[] = [];
let brickFracTable: [number, number][] = [];
const gltfTypeTable: Record<number, string> = {};

const CROSS_SPRITE_VERTS = new Float32Array([0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1]);

// Mirrors chunkBuilder.ts's buildFaceTable(), sourced from a one-time postMessage snapshot
// (window.mc.getBlockDef(typeOrd) for typeOrd 0..511) instead of window.mc directly.
function buildFaceTable(defs: { typeOrd: number; def: McBlockDef }[]): void {
  faceTable = [];
  brickFracTable = [];
  for (const { typeOrd, def: blockDef } of defs) {
    const bs = (blockDef.brickSize ?? [2, 2, 2]).map((v) => v / 2) as [number, number, number];
    brickFracTable[typeOrd] = [bs[0] < 1 ? bs[0] : bs[0] > 1 ? 0.5 : 0, bs[2] < 1 ? bs[2] : bs[2] > 1 ? 0.5 : 0];
    if (blockDef.renderType === "gltf") {
      if (blockDef.gltfPath) gltfTypeTable[typeOrd] = blockDef.gltfPath;
      continue;
    }
    const isCross = blockDef.renderType === "cross_sprite";
    const isPlastic = blockDef.hasStuds === true;

    for (let rotation = 0; rotation < 4; rotation++) {
      for (let fd = 0; fd < 6; fd++) {
        const faceMat = (typeOrd * 4 + rotation) * 6 + fd;
        const infos: FaceInfo[] = [];

        if (isCross) {
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
                isPlastic,
              });
          }
        } else {
          for (let elemIdx = 0; elemIdx < blockDef.elements.length; elemIdx++) {
            const elem = blockDef.elements[elemIdx];
            const srcFd = ROT_SOURCE[rotation][fd];
            const fi = elem.faces[srcFd];
            if (!fi) continue;
            const [nx, ny, nz] = MC_NORMS[fd];
            const rawVerts = vertsFromElement(elem.from, elem.to, fd);
            let verts = rotation === 0 ? rawVerts : rotateVerts(rawVerts, rotation);
            if (rotation > 0 && (bs[0] !== 1 || bs[2] !== 1)) {
              const sX = bs[0],
                sZ = bs[2];
              const cx = rotation === 2 ? sX - 1 : rotation === 3 ? sZ - 1 : 0;
              const cz = rotation === 1 ? sX - 1 : rotation === 2 ? sZ - 1 : 0;
              if (cx !== 0 || cz !== 0) {
                const cv = new Float32Array(12);
                for (let k = 0; k < 4; k++) {
                  cv[k * 3] = verts[k * 3] + cx;
                  cv[k * 3 + 1] = verts[k * 3 + 1];
                  cv[k * 3 + 2] = verts[k * 3 + 2] + cz;
                }
                verts = cv;
              }
            }
            infos.push({
              matKey: fi.matKey,
              uv: new Float32Array(fi.uv),
              shade: FACE_SHADES[fd],
              normX: nx,
              normY: ny,
              normZ: nz,
              verts,
              isCrossSprite: false,
              isPlastic,
            });
          }
        }

        if (infos.length > 0) faceTable[faceMat] = infos;
      }
    }
  }
}

const GROUP_MAX_VERTS = 65_536;
const GROUP_MAX_IDX = Math.ceil(GROUP_MAX_VERTS * 1.5);

interface FaceGroup {
  p: Float32Array;
  n: Float32Array;
  u: Float32Array;
  c: Float32Array;
  i: Int32Array;
  v: number;
  ic: number;
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
  isPlastic = false,
): void {
  if (g.v + 4 > GROUP_MAX_VERTS) return;
  const baseV = g.v;
  let pi = baseV * 3;
  let ni = baseV * 3;
  let ui = baseV * 2;
  let ci = baseV * 4;
  const alphaFlag = isPlastic ? 2.0 : 1.0;
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
    g.c[ci++] = alphaFlag;
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

function stretchVertsAxis(verts: Float32Array, runLen: number, axis: number): Float32Array {
  const r = new Float32Array(verts);
  for (let k = 0; k < 4; k++) {
    if (verts[k * 3 + axis] >= 0.999) r[k * 3 + axis] = runLen;
  }
  return r;
}

function stretchUVAxis(uv: Float32Array, verts: Float32Array, runLen: number, axis: number): Float32Array {
  const highIdx: number[] = [];
  const lowIdx: number[] = [];
  for (let k = 0; k < 4; k++) (verts[k * 3 + axis] >= 0.999 ? highIdx : lowIdx).push(k);
  const r = new Float32Array(uv);
  if (highIdx.length !== 2 || lowIdx.length !== 2) return r;
  for (let uvAxis = 0; uvAxis < 2; uvAxis++) {
    const h0 = uv[highIdx[0] * 2 + uvAxis];
    const h1 = uv[highIdx[1] * 2 + uvAxis];
    const l0 = uv[lowIdx[0] * 2 + uvAxis];
    const l1 = uv[lowIdx[1] * 2 + uvAxis];
    if (Math.abs(h0 - h1) < 1e-4 && Math.abs(l0 - l1) < 1e-4 && Math.abs(h0 - l0) > 1e-4) {
      const newHigh = l0 + (h0 - l0) * runLen;
      r[highIdx[0] * 2 + uvAxis] = newHigh;
      r[highIdx[1] * 2 + uvAxis] = newHigh;
      break;
    }
  }
  return r;
}

const FACE_STRIDE = 7;
// Must match chunkBuilder.ts's SLAB_HEIGHT — see that file's comment for why 64.
const SLAB_HEIGHT = 64;

interface MeshRequest {
  type: "mesh";
  reqId: string;
  key: string;
  faceBuf: Int32Array;
  faceCount: number;
}

interface BlockDefsMessage {
  type: "blockDefs";
  defs: { typeOrd: number; def: McBlockDef }[];
}

type IncomingMessage = MeshRequest | BlockDefsMessage;

// Processes the whole transferred face buffer in one pass (no slicing/budget — the entire point
// of moving this off the main thread is that it no longer has to share a frame budget).
function processFaces(faceBuf: Int32Array, faceCount: number) {
  const groups: Record<string, FaceGroup> = {};
  const gltfPositions: Record<number, Set<string>> = {};
  const endI = faceCount * FACE_STRIDE;
  for (let i = 0; i < endI; i += FACE_STRIDE) {
    let wx = faceBuf[i],
      wz = faceBuf[i + 2];
    const faceMat = faceBuf[i + 3];
    const aoPacked = faceBuf[i + 4];
    const runLenX = faceBuf[i + 5];
    const runLenZ = faceBuf[i + 6];
    const ao = aoPacked & 0xffff;
    const yOff = (aoPacked >>> 16) & 0x3;
    const colorIdx = (aoPacked >>> 18) & 0x3f;
    const xOff = (aoPacked >>> 24) & 0x3;
    const zOff = (aoPacked >>> 26) & 0x3;
    const plainKey = colorIdx > 0 ? plainMatKey(colorIdx) : null;
    const wy = faceBuf[i + 1] + (yOff === 0 ? 0 : yOff / 3);
    if (xOff || zOff) {
      const typeOrd = (faceMat / 24) | 0;
      const frac = brickFracTable[typeOrd] ?? [1, 1];
      const rotation = (((faceMat / 6) | 0) % 4) as 0 | 1 | 2 | 3;
      const [fracX, fracZ] = rotation % 2 === 0 ? frac : [frac[1], frac[0]];
      wx += xOff * fracX;
      wz += zOff * fracZ;
    }
    const infos = faceTable[faceMat];
    if (!infos) {
      const typeOrd = (faceMat / 24) | 0;
      if (gltfTypeTable[typeOrd] !== undefined) {
        const posKey = `${faceBuf[i]},${faceBuf[i + 1]},${faceBuf[i + 2]}`;
        if (!gltfPositions[typeOrd]) gltfPositions[typeOrd] = new Set();
        gltfPositions[typeOrd].add(posKey);
      }
      continue;
    }
    const yBand = Math.floor(wy / SLAB_HEIGHT);
    for (const info of infos) {
      const groupKey = `${plainKey ?? info.matKey}|${yBand}`;
      let g = groups[groupKey];
      if (!g) {
        g = acquireGroup();
        groups[groupKey] = g;
      }
      if (info.isCrossSprite) {
        emitCrossSprite(wx, wy, wz, g, info.uv, ao);
      } else if (runLenX > 1 || runLenZ > 1) {
        let verts = info.verts;
        let uv = info.uv;
        if (runLenX > 1) {
          verts = stretchVertsAxis(verts, runLenX, 0);
          uv = stretchUVAxis(uv, info.verts, runLenX, 0);
        }
        if (runLenZ > 1) {
          verts = stretchVertsAxis(verts, runLenZ, 2);
          uv = stretchUVAxis(uv, info.verts, runLenZ, 2);
        }
        emitQuad(g, wx, wy, wz, verts, info.normX, info.normY, info.normZ, uv, info.shade, ao, info.isPlastic);
      } else {
        emitQuad(
          g,
          wx,
          wy,
          wz,
          info.verts,
          info.normX,
          info.normY,
          info.normZ,
          info.uv,
          info.shade,
          ao,
          info.isPlastic,
        );
      }
    }
  }
  return { groups, gltfPositions };
}

ctx.onmessage = (ev: MessageEvent<IncomingMessage>) => {
  const msg = ev.data;
  if (msg.type === "blockDefs") {
    buildFaceTable(msg.defs);
    return;
  }
  if (msg.type === "mesh") {
    const { groups, gltfPositions } = processFaces(msg.faceBuf, msg.faceCount);
    const outGroups: {
      key: string;
      p: Float32Array;
      n: Float32Array;
      u: Float32Array;
      c: Float32Array;
      i: Int32Array;
      v: number;
      ic: number;
    }[] = [];
    const transfer: Transferable[] = [];
    for (const groupKey of Object.keys(groups)) {
      const g = groups[groupKey];
      if (g.v === 0) {
        releaseGroup(g);
        continue;
      }
      // .slice() copies out of the pooled buffer into a fresh, exact-size, transferable one —
      // the pool buffer itself must stay owned by this worker for reuse across jobs.
      const p = g.p.slice(0, g.v * 3);
      const n = g.n.slice(0, g.v * 3);
      const u = g.u.slice(0, g.v * 2);
      const c = g.c.slice(0, g.v * 4);
      const idx = g.i.slice(0, g.ic);
      outGroups.push({ key: groupKey, p, n, u, c, i: idx, v: g.v, ic: g.ic });
      transfer.push(p.buffer, n.buffer, u.buffer, c.buffer, idx.buffer);
      releaseGroup(g);
    }
    const outGltf = Object.keys(gltfPositions).map((typeOrdStr) => ({
      typeOrd: Number(typeOrdStr),
      posKeys: Array.from(gltfPositions[Number(typeOrdStr)]),
    }));
    ctx.postMessage({ type: "meshResult", reqId: msg.reqId, key: msg.key, groups: outGroups, gltf: outGltf }, transfer);
  }
};
