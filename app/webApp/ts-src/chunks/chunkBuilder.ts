import type { Scene, Mesh, Material } from "@babylonjs/core";

// Vertex offsets for each face direction (4 vertices per face, CCW winding)
const MC_VERTS: [number, number, number][][] = [
  // 0: Front +Z
  [[-0.5,-0.5, 0.5], [ 0.5,-0.5, 0.5], [ 0.5, 0.5, 0.5], [-0.5, 0.5, 0.5]],
  // 1: Back  -Z
  [[ 0.5,-0.5,-0.5], [-0.5,-0.5,-0.5], [-0.5, 0.5,-0.5], [ 0.5, 0.5,-0.5]],
  // 2: Right +X
  [[ 0.5,-0.5, 0.5], [ 0.5,-0.5,-0.5], [ 0.5, 0.5,-0.5], [ 0.5, 0.5, 0.5]],
  // 3: Left  -X
  [[-0.5,-0.5,-0.5], [-0.5,-0.5, 0.5], [-0.5, 0.5, 0.5], [-0.5, 0.5,-0.5]],
  // 4: Top   +Y
  [[-0.5, 0.5, 0.5], [ 0.5, 0.5, 0.5], [ 0.5, 0.5,-0.5], [-0.5, 0.5,-0.5]],
  // 5: Bottom-Y
  [[-0.5,-0.5,-0.5], [ 0.5,-0.5,-0.5], [ 0.5,-0.5, 0.5], [-0.5,-0.5, 0.5]],
];
const MC_NORMS: [number, number, number][] = [
  [0,0,1], [0,0,-1], [1,0,0], [-1,0,0], [0,1,0], [0,-1,0],
];
const MC_UV = [0,1, 1,1, 1,0, 0,0]; // full texture per face

interface FaceGroup {
  p: number[]; n: number[]; u: number[]; i: number[]; v: number;
}
interface ChunkBuf {
  key: string;
  groups: Record<string, FaceGroup>;
}

let __mcBuf: ChunkBuf | null = null;

// BlockType ordinals: AIR=0, BEDROCK=1, STONE=2, DIRT=3, GRASS=4, SAND=5, SANDSTONE=6, GRAVEL=7, SNOW=8
// faceMat = blockOrdinal * 6 + faceDir (0=+Z,1=-Z,2=+X,3=-X,4=+Y,5=-Y)
function matGroup(faceMat: number): string {
  const faceDir = faceMat % 6;
  const typeOrd = (faceMat - faceDir) / 6;
  if (typeOrd === 4) {
    if (faceDir === 4) return 'gt';   // grass top
    if (faceDir === 5) return 'gb';   // grass bottom
    if (faceDir === 0) return 'gf';   // grass front
    if (faceDir === 1) return 'gbk';  // grass back
    return 'gx';                       // grass side
  }
  if (typeOrd === 2) return 's';   // STONE
  if (typeOrd === 3) return 'd';   // DIRT
  if (typeOrd === 5) return 'sa';  // SAND
  if (typeOrd === 6) return 'ss';  // SANDSTONE
  if (typeOrd === 7) return 'gr';  // GRAVEL
  if (typeOrd === 8) return 'sn';  // SNOW
  return 'b';                       // BEDROCK (and fallback)
}

function disposeChunk(key: string): void {
  const meshes = window.__mcChunks[key];
  if (meshes) { (meshes as InstanceType<typeof BABYLON.AbstractMesh>[]).forEach(m => m.dispose()); delete window.__mcChunks[key]; }
}

export function registerChunks(): void {
  window.__mcChunks = {};
  window.mcDisposeChunk = disposeChunk;

  window.mcChunkBegin = (cx: number, cz: number): void => {
    __mcBuf = { key: `${cx},${cz}`, groups: {} };
  };

  window.mcChunkFace = (wx: number, wy: number, wz: number, faceMat: number): void => {
    const mk  = matGroup(faceMat);
    const fd  = faceMat % 6;
    const grp = __mcBuf!.groups;
    if (!grp[mk]) grp[mk] = { p: [], n: [], u: [], i: [], v: 0 };
    const g  = grp[mk];
    const vt = MC_VERTS[fd];
    const nm = MC_NORMS[fd];
    for (let k = 0; k < 4; k++) {
      g.p.push(wx + vt[k][0], wy + vt[k][1], wz + vt[k][2]);
      g.n.push(nm[0], nm[1], nm[2]);
      g.u.push(MC_UV[k * 2], MC_UV[k * 2 + 1]);
    }
    const b = g.v; g.i.push(b, b+1, b+2, b, b+2, b+3); g.v += 4;
  };

  // grassMat is a BABYLON.MultiMaterial;
  // subMaterials = [sideFr(0), sideBk(1), sideX2(2), sideX(3), top(4), bottom(5)]
  window.mcChunkEnd = (
    scene: Scene,
    grassMat: any, stoneMat: Material, dirtMat: Material, bedrockMat: Material,
    sandMat: Material, sandstoneMat: Material, gravelMat: Material, snowMat: Material,
  ): void => {
    const buf = __mcBuf!; __mcBuf = null;
    const key = buf.key;
    disposeChunk(key);

    const gsm: Material[] = (grassMat?.subMaterials) ?? [];
    const matMap: Record<string, Material | null> = {
      s: stoneMat, d: dirtMat, b: bedrockMat,
      gt: gsm[4] ?? null, gb: gsm[5] ?? null,
      gf: gsm[0] ?? null, gbk: gsm[1] ?? null, gx: gsm[2] ?? null,
      sa: sandMat, ss: sandstoneMat, gr: gravelMat, sn: snowMat,
    };

    const meshes: Mesh[] = [];
    for (const mk of Object.keys(buf.groups)) {
      const g = buf.groups[mk];
      if (g.v === 0) continue;
      const mesh = new BABYLON.Mesh(`ck${key}${mk}`, scene);
      const vd   = new BABYLON.VertexData();
      vd.positions = g.p; vd.normals = g.n; vd.uvs = g.u; vd.indices = g.i;
      vd.applyToMesh(mesh, false);
      mesh.material = matMap[mk] ?? null;
      mesh.freezeWorldMatrix();
      mesh.isPickable = false;
      mesh.doNotSyncBoundingInfo = true;
      meshes.push(mesh);
    }
    window.__mcChunks[key] = meshes;
  };

}
