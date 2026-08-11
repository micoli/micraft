import type {
  Scene,
  Mesh,
  Material,
  ShaderMaterial,
  ISceneLoaderAsyncResult,
  AbstractMesh,
  TransformNode,
} from "@babylonjs/core";
import { BLOCK_VERT, BLOCK_GHOST_FRAG } from "../shaders/block";
import { plainMatKey } from "../blocks/blockDefs";
import { WHITE_PIXEL_URL } from "../materials/whitePixel";

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
  isPlastic: boolean;
}

// faceTable[faceMat] = list of FaceInfo to emit (one per element that has this face)
let faceTable: (FaceInfo[] | null)[] = [];

// brickFracTable[typeOrd] = [fracX, fracZ] — sub-voxel fractions for XZ-offset rendering
let brickFracTable: [number, number][] = [];

// gltfTypeTable[typeOrd] = gltf asset path for blocks rendered as GLTF instances
const gltfTypeTable: Record<number, string> = {};

// Cache of loaded GLTF source meshes (hidden, used for instancing). Keyed by gltfPath.
const gltfMeshCache: Map<string, InstanceType<typeof BABYLON.Mesh>[]> = new Map();

async function loadGltfSources(gltfPath: string, scene: InstanceType<typeof BABYLON.Scene>): Promise<Mesh[]> {
  if (gltfMeshCache.has(gltfPath)) return gltfMeshCache.get(gltfPath)!;
  const parts = gltfPath.split("/");
  const fileName = parts.pop()!;
  const baseUrl = `/api/game-assets/file/${parts.join("/")}/`;
  const result: ISceneLoaderAsyncResult = await BABYLON.SceneLoader.ImportMeshAsync("", baseUrl, fileName, scene);
  // BabylonJS GLTF loader creates a __root__ TransformNode with scaling.x=-1 to convert
  // from GLTF right-handed to BabylonJS left-handed coords. Instances don't inherit that
  // parent, so we bake the transform into vertex positions/normals before instancing.
  const sources = result.meshes
    .filter((m: AbstractMesh) => m.getTotalVertices() > 0)
    .map((m: AbstractMesh) => {
      const mesh = m as Mesh;
      mesh.setParent(null); // folds __root__ scaling(-1,1,1) into local transform
      mesh.bakeCurrentTransformIntoVertices();
      mesh.position.setAll(0);
      mesh.rotationQuaternion = null;
      mesh.rotation.setAll(0);
      mesh.scaling.setAll(1);
      mesh.isVisible = false;
      mesh.isPickable = false;
      return mesh;
    });
  result.transformNodes.forEach((n: TransformNode) => n.dispose());
  gltfMeshCache.set(gltfPath, sources);
  return sources;
}

const CROSS_SPRITE_VERTS = new Float32Array([0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1]);

function buildFaceTable(): void {
  faceTable = [];
  brickFracTable = [];
  for (let typeOrd = 0; typeOrd < 512; typeOrd++) {
    const blockDef = window.mc.getBlockDef(typeOrd);
    if (!blockDef) continue;
    const bs = blockDef.brickSize ?? [1, 1, 1];
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
                isPlastic,
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
            let verts = rotation === 0 ? rawVerts : rotateVerts(rawVerts, rotation);
            // rotateVerts90CW pivots around (0.5,_,0.5) — only correct for 1×1 blocks.
            // Compensate vertex positions for non-unit footprints so the rotated mesh
            // aligns with its placement voxel corner (fixes ghost and placed-block rendering).
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
  isPlastic = false,
): void {
  if (g.v + 4 > GROUP_MAX_VERTS) return; // safety guard
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

// --- Greedy-merge stretch (runLen > 1 runs from ChunkManager.renderRow) ---
// Only ever applied to top/bottom/east/west faces of simple-cube blocks (see
// mergeableByOrd in Kotlin) — always merged along local Z (index 2 of a vert triple),
// regardless of which of those 4 directions it is, per vertsFromElement's layout.
// Rebuilds a stretched copy each call rather than mutating faceTable's cached arrays,
// which are shared across every unmerged face of that faceMat.

function stretchVertsZ(verts: Float32Array, runLen: number): Float32Array {
  const r = new Float32Array(12);
  for (let k = 0; k < 4; k++) {
    r[k * 3] = verts[k * 3];
    r[k * 3 + 1] = verts[k * 3 + 1];
    r[k * 3 + 2] = verts[k * 3 + 2] >= 0.999 ? runLen : verts[k * 3 + 2];
  }
  return r;
}

// Finds the uv axis (u or v) that varies between the "high Z" and "low Z" vertex
// pairs and scales it by runLen so the texture tiles across the merged run (textures
// use WRAP address mode by default) instead of stretching.
function stretchUVZ(uv: Float32Array, verts: Float32Array, runLen: number): Float32Array {
  const highIdx: number[] = [];
  const lowIdx: number[] = [];
  for (let k = 0; k < 4; k++) (verts[k * 3 + 2] >= 0.999 ? highIdx : lowIdx).push(k);
  const r = new Float32Array(uv);
  if (highIdx.length !== 2 || lowIdx.length !== 2) return r;
  for (let axis = 0; axis < 2; axis++) {
    const h0 = uv[highIdx[0] * 2 + axis];
    const h1 = uv[highIdx[1] * 2 + axis];
    const l0 = uv[lowIdx[0] * 2 + axis];
    const l1 = uv[lowIdx[1] * 2 + axis];
    if (Math.abs(h0 - h1) < 1e-4 && Math.abs(l0 - l1) < 1e-4 && Math.abs(h0 - l0) > 1e-4) {
      const newHigh = l0 + (h0 - l0) * runLen;
      r[highIdx[0] * 2 + axis] = newHigh;
      r[highIdx[1] * 2 + axis] = newHigh;
      break;
    }
  }
  return r;
}

// --- Face buffer (shared with Kotlin via window.__mcFB / window.__mcFI) ---
// Kotlin writes face data here (jsChunkFaceAppend); chunkEnd processes the whole
// buffer in one tight loop — eliminates per-face JS function-call overhead.
// Stride is 6 ints/face — 6th is runLen (blocks merged along Z by greedy meshing
// in ChunkManager.renderRow; 1 = unmerged, see FACE_STRIDE below).

const FACE_STRIDE = 6;
const FACE_BUF_SLOTS = 720_000; // 6 ints × up to 120k faces per chunk

// Y-slab height for sub-chunk mesh splitting. Each material×slab combo becomes its own
// BabylonJS mesh, giving the engine a tight bounding box per slab for frustum culling.
// With SLAB_HEIGHT=16, a world of topY≈128 produces 8 slabs — only the 2-3 slabs in the
// camera frustum are rendered, saving 60-70% of vertex work for underground/angled views.
const SLAB_HEIGHT = 16;

// --- Chunk state ---

interface ChunkBuf {
  key: string;
  groups: Record<string, FaceGroup>;
  gltfPositions: Record<number, Set<string>>; // typeOrd → Set of "wx,wy,wz" keys
}
let __mcBuf: ChunkBuf | null = null;

function disposeChunk(key: string): void {
  const meshes = window.mcState.chunks[key];
  if (meshes) {
    const shadowRTT = window.mcState.sunShadowRTT;
    (meshes as InstanceType<typeof BABYLON.AbstractMesh>[]).forEach((m) => {
      if (shadowRTT?.renderList) {
        const idx = shadowRTT.renderList.indexOf(m);
        if (idx >= 0) shadowRTT.renderList.splice(idx, 1);
        // Prevent _materialForRendering map from growing unbounded
        shadowRTT.setMaterialForRendering(m, undefined as unknown as InstanceType<typeof BABYLON.Material>);
      }
      m.dispose();
    });
    delete window.mcState.chunks[key];
  }
}

const ghostMatCache: Record<string, ShaderMaterial> = {};

function getOrCreateGhostMat(scene: Scene, matKey: string): ShaderMaterial | null {
  if (ghostMatCache[matKey]) return ghostMatCache[matKey];
  let url: string;
  let tr: number, tg: number, tb: number;
  if (matKey.startsWith("plain:")) {
    // Plain color: flat white texture tinted by the color, same trick as createBlockMaterials
    const color = window.mc.getPlainColors().find((c) => "plain:" + c.hex === matKey);
    if (!color) return null;
    url = WHITE_PIXEL_URL;
    [tr, tg, tb] = [color.r / 255, color.g / 255, color.b / 255];
  } else {
    const textures = window.mc.getBlockTextures();
    const baseName = matKey.replace(":biome_tint", "");
    const texDef = textures.find((t) => t.name === baseName);
    if (!texDef) return null;
    url = texDef.url;
    const isBiomeTint = matKey.endsWith(":biome_tint");
    [tr, tg, tb] = isBiomeTint ? [0.47, 0.75, 0.35] : (texDef.tint ?? [1, 1, 1]);
  }
  const mat = new BABYLON.ShaderMaterial(
    "ghost_" + matKey,
    scene,
    { vertexSource: BLOCK_VERT, fragmentSource: BLOCK_GHOST_FRAG },
    {
      attributes: ["position", "normal", "uv", "color"],
      uniforms: ["worldViewProjection", "tint"],
      samplers: ["textureSampler"],
    },
  );
  const tex = new BABYLON.Texture(url, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
  mat.setTexture("textureSampler", tex);
  mat.setVector3("tint", new BABYLON.Vector3(tr, tg, tb));
  mat.backFaceCulling = false;
  mat.needAlphaBlending = () => true;
  mat.zOffset = -2;
  mat.zOffsetUnits = -4;
  ghostMatCache[matKey] = mat;
  return mat;
}

export function buildBlockPreviewMeshes(scene: Scene, typeOrd: number, rotation: number, colorIdx = 0): Mesh[] {
  if (!window.mc.isBlockDefsReady()) {
    console.warn("[MiCraft] Ghost: block defs not ready yet (typeOrd=" + typeOrd + ")");
    return [];
  }
  if (faceTable.length === 0) {
    console.warn("[MiCraft] Ghost: faceTable empty — no chunk rendered yet? (typeOrd=" + typeOrd + ")");
    buildFaceTable();
  }

  const groups: Record<string, FaceGroup> = {};
  const plainKey = colorIdx > 0 ? plainMatKey(colorIdx) : null;

  for (let fd = 0; fd < 6; fd++) {
    const faceMat = (typeOrd * 4 + rotation) * 6 + fd;
    const infos = faceTable[faceMat];
    if (!infos) continue;
    for (const info of infos) {
      const matKey = plainKey ?? info.matKey;
      if (!groups[matKey]) groups[matKey] = acquireGroup();
      const g = groups[matKey];
      if (info.isCrossSprite) {
        emitCrossSprite(0, 0, 0, g, info.uv, 0);
      } else {
        emitQuad(g, 0, 0, 0, info.verts, info.normX, info.normY, info.normZ, info.uv, info.shade, 0, info.isPlastic);
      }
    }
  }

  const meshes: Mesh[] = [];
  for (const [mk, g] of Object.entries(groups)) {
    if (g.v === 0) {
      releaseGroup(g);
      continue;
    }
    const mesh = new BABYLON.Mesh("ghostBlock_" + mk, scene) as Mesh;
    const vd = new BABYLON.VertexData();
    vd.positions = g.p.subarray(0, g.v * 3);
    vd.normals = g.n.subarray(0, g.v * 3);
    vd.uvs = g.u.subarray(0, g.v * 2);
    vd.colors = g.c.subarray(0, g.v * 4);
    vd.indices = g.i.subarray(0, g.ic);
    vd.applyToMesh(mesh, false);
    mesh.material = getOrCreateGhostMat(scene, mk);
    mesh.isPickable = false;
    meshes.push(mesh);
    releaseGroup(g);
  }
  return meshes;
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
      __mcBuf = { key: `${cx},${cz}`, groups: {}, gltfPositions: {} };
      if (!window.__mcFB) window.__mcFB = new Int32Array(FACE_BUF_SLOTS);
      window.__mcFI = 0;
    },

    // no-op stub — Kotlin uses jsChunkFaceAppend (writes directly to __mcFB)
    chunkFace: (_wx: number, _wy: number, _wz: number, _faceMat: number, _ao: number): void => {},

    // Process a slice of __mcFB into FaceGroups (geometry work only, no GPU upload).
    // cursor: face index (not byte offset); maxFaces: max to process this call.
    // Returns actual faces processed. Call repeatedly until return value < maxFaces
    // (or until cursor × FACE_STRIDE >= __mcFI) then call chunkEnd for GPU upload.
    chunkProcessFaces: (cursor: number, maxFaces: number): number => {
      const buf = __mcBuf!;
      const fb = window.__mcFB!;
      const fi = window.__mcFI ?? 0;
      const startI = cursor * FACE_STRIDE;
      const endI = Math.min(startI + maxFaces * FACE_STRIDE, fi);
      // if (cursor === 0) console.warn("[fracDebug] chunkProcessFaces start", { fi, totalFaces: fi / FACE_STRIDE });
      const grp = buf.groups;
      for (let i = startI; i < endI; i += FACE_STRIDE) {
        let wx = fb[i],
          wz = fb[i + 2];
        const faceMat = fb[i + 3];
        const aoPacked = fb[i + 4];
        const runLen = fb[i + 5];
        const ao = aoPacked & 0xffff;
        const yOff = (aoPacked >>> 16) & 0x3;
        const colorIdx = (aoPacked >>> 18) & 0x3f;
        const xOff = (aoPacked >>> 24) & 0x3;
        const zOff = (aoPacked >>> 26) & 0x3;
        const plainKey = colorIdx > 0 ? plainMatKey(colorIdx) : null;
        const wy = fb[i + 1] + (yOff === 0 ? 0 : yOff / 3);
        if (xOff || zOff) {
          const typeOrd = (faceMat / 24) | 0;
          const frac = brickFracTable[typeOrd] ?? [1, 1];
          wx += xOff * frac[0];
          wz += zOff * frac[1];
        }
        const infos = faceTable[faceMat];
        // if (xOff || zOff || yOff) {
        //  // TEMP DEBUG — remove once the "offset entities don't render" bug is found
        //  console.warn("[fracDebug]", { wx, wy, wz, xOff, zOff, yOff, faceMat, hasInfos: !!infos });
        //}
        if (!infos) {
          const typeOrd = (faceMat / 24) | 0;
          if (gltfTypeTable[typeOrd] !== undefined) {
            const posKey = `${fb[i]},${fb[i + 1]},${fb[i + 2]}`;
            if (!buf.gltfPositions[typeOrd]) buf.gltfPositions[typeOrd] = new Set();
            buf.gltfPositions[typeOrd].add(posKey);
          }
          continue;
        }
        const yBand = Math.floor(wy / SLAB_HEIGHT);
        for (const info of infos) {
          const groupKey = `${plainKey ?? info.matKey}|${yBand}`;
          let g = grp[groupKey];
          if (!g) {
            g = acquireGroup();
            grp[groupKey] = g;
          }
          if (info.isCrossSprite) {
            emitCrossSprite(wx, wy, wz, g, info.uv, ao);
          } else if (runLen > 1) {
            emitQuad(
              g,
              wx,
              wy,
              wz,
              stretchVertsZ(info.verts, runLen),
              info.normX,
              info.normY,
              info.normZ,
              stretchUVZ(info.uv, info.verts, runLen),
              info.shade,
              ao,
              info.isPlastic,
            );
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
      return ((endI - startI) / FACE_STRIDE) | 0;
    },

    // GPU upload only — call after chunkProcessFaces has consumed all faces.
    chunkEnd: (scene: Scene, materials: Record<string, Material>): void => {
      const buf = __mcBuf!;
      __mcBuf = null;

      disposeChunk(buf.key);

      const meshes: Mesh[] = [];
      for (const groupKey of Object.keys(buf.groups)) {
        const g = buf.groups[groupKey];
        if (g.v === 0) {
          releaseGroup(g);
          continue;
        }
        // groupKey = "matKey|yBand" — extract matKey for material lookup
        const matKey = groupKey.slice(0, groupKey.lastIndexOf("|"));
        const mesh = new BABYLON.Mesh(`ck${buf.key}${groupKey}`, scene);
        const vd = new BABYLON.VertexData();
        vd.positions = g.p.subarray(0, g.v * 3);
        vd.normals = g.n.subarray(0, g.v * 3);
        vd.uvs = g.u.subarray(0, g.v * 2);
        vd.colors = g.c.subarray(0, g.v * 4);
        vd.indices = g.i.subarray(0, g.ic);
        vd.applyToMesh(mesh, false);
        mesh.material = materials[matKey] ?? null;
        mesh.isPickable = false;
        mesh.doNotSyncBoundingInfo = true;
        mesh.refreshBoundingInfo();
        mesh.freezeWorldMatrix();
        const shadowRTT = window.mcState.sunShadowRTT;
        const shadowDepthMat = window.mcState.sunShadowDepthMat;
        if (shadowRTT?.renderList && shadowDepthMat) {
          shadowRTT.renderList.push(mesh);
          shadowRTT.setMaterialForRendering(mesh, shadowDepthMat);
        }
        meshes.push(mesh);
        releaseGroup(g);
      }
      window.mcState.chunks[buf.key] = meshes;

      const gltfEntries = Object.entries(buf.gltfPositions);
      if (gltfEntries.length > 0) {
        const chunkKey = buf.key;
        (async () => {
          for (const [typeOrdStr, posSet] of gltfEntries) {
            const gltfPath = gltfTypeTable[Number(typeOrdStr)];
            if (!gltfPath) continue;
            const sources = await loadGltfSources(gltfPath, scene);
            if (!window.mcState.chunks[chunkKey]) continue; // chunk disposed during async load
            const instances: InstanceType<typeof BABYLON.AbstractMesh>[] = [];
            for (const posKey of posSet) {
              const [wx, wy, wz] = posKey.split(",").map(Number);
              for (const src of sources) {
                const inst = src.createInstance(`gltfi_${chunkKey}_${posKey}`);
                inst.position.set(wx, wy, wz + 1);
                //                 inst.position.set(wx, wy, wz);
                inst.scaling.setAll(0.8);
                inst.isPickable = false;
                instances.push(inst);
              }
            }
            const existing = window.mcState.chunks[chunkKey] as InstanceType<typeof BABYLON.AbstractMesh>[];
            window.mcState.chunks[chunkKey] = [...existing, ...instances];
          }
        })();
      }
    },
  };
}
