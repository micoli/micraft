// faceDir → bbmodel face key (faceDir: 0=+Z/south, 1=-Z/north, 2=+X/east, 3=-X/west, 4=+Y/up, 5=-Y/down)
const FACEKEY_BY_DIR = ["south", "north", "east", "west", "up", "down"] as const;
type FaceKey = (typeof FACEKEY_BY_DIR)[number];

// Convert bbmodel face uv [x0,y0,x1,y1] (pixel coords) to per-vertex UV array.
// Winding order matches MC_VERTS: k=0 bottom-left, k=1 bottom-right, k=2 top-right, k=3 top-left.
// V is flipped (1 - y/H) because ShaderMaterial + invertY=true: v=0 = bottom of image, not top.
function toVertexUV(uv: [number, number, number, number], W: number, H: number): number[] {
  const uMin = uv[0] / W,
    uMax = uv[2] / W;
  const vTop = 1 - uv[1] / H,
    vBot = 1 - uv[3] / H;
  return [uMin, vBot, uMax, vBot, uMax, vTop, uMin, vTop];
}

function parseBlockBbmodel(bbmodel: BlocksBbModel): { def: McBlockDef | null; textures: McBlockTextureDef[] } {
  const W = bbmodel.resolution?.width ?? 16;
  const H = bbmodel.resolution?.height ?? 16;

  const textures: McBlockTextureDef[] = bbmodel.textures.map((t) => ({
    name: t.name,
    url: "/" + t.path,
    hasAlpha: t.mc_alpha === true,
    tint: t.mc_tint,
    biomeTint: false,
  }));

  if (!bbmodel.elements.length) return { def: null, textures };

  const renderType = (bbmodel.elements[0].render_type as McBlockDef["renderType"]) ?? "solid";
  const allElements: McBlockElement[] = [];

  for (const el of bbmodel.elements) {
    for (const faceKey of Object.keys(el.faces) as FaceKey[]) {
      const face = el.faces[faceKey];
      if (face?.biome_tint) {
        const texDef = textures[face.texture];
        if (texDef) texDef.biomeTint = true;
      }
    }

    const elemFaces: (McBlockFaceInfo | null)[] = FACEKEY_BY_DIR.map((faceKey) => {
      const face = el.faces[faceKey as FaceKey];
      if (!face) return null;
      const texDef = textures[face.texture];
      if (!texDef) return null;
      const uv = face.uv ?? [0, 0, W, H];
      const matKey = face.biome_tint ? texDef.name + ":biome_tint" : texDef.name;
      return { matKey, uv: toVertexUV(uv as [number, number, number, number], W, H) };
    });

    allElements.push({
      from: (el.from ?? [0, 0, 0]) as [number, number, number],
      to: (el.to ?? [16, 16, 16]) as [number, number, number],
      faces: elemFaces,
    });
  }

  const allElemFaces = allElements.map((e) => e.faces);
  return { def: { name: bbmodel.elements[0].name, renderType, elements: allElements, faces: allElemFaces }, textures };
}

const WATER_MAT_KEY = "water";
const LIQUID_UV = [0, 0, 1, 0, 1, 1, 0, 1];

let _blockDefs: (McBlockDef | null)[] | null = null;
let _blockTextures: McBlockTextureDef[] | null = null;

// Loaded from RegistrySync — ordinal-indexed list of block infos
let _registryBlocks:
  | { name: string; modelElement: string; gltfModel?: string; liquid?: boolean; hasStuds?: boolean }[]
  | null = null;

export function registerBlockDefs(): Pick<
  McBindings,
  "initBlockDefs" | "isBlockDefsReady" | "getBlockDef" | "getBlockTextures"
> {
  return {
    initBlockDefs: () => {
      if (!_registryBlocks) return;

      const allTextures: Map<string, McBlockTextureDef> = new Map();
      const defs: (McBlockDef | null)[] = new Array(_registryBlocks.length).fill(null);

      const fetches = _registryBlocks.map((info, ordinal) => {
        if (info.name === "AIR") return Promise.resolve();
        if (info.gltfModel) {
          defs[ordinal] = { name: info.name, renderType: "gltf", gltfPath: info.gltfModel, elements: [], faces: [] };
          return Promise.resolve();
        }
        if (info.liquid) {
          const liquidFaces: McBlockFaceInfo[] = Array.from({ length: 6 }, () => ({
            matKey: WATER_MAT_KEY,
            uv: LIQUID_UV,
          }));
          const liquidElem: McBlockElement = { from: [0, 0, 0], to: [16, 16, 16], faces: liquidFaces };
          defs[ordinal] = { name: info.name, renderType: "liquid", elements: [liquidElem], faces: [liquidFaces] };
          return Promise.resolve();
        }
        const fileName = info.modelElement || info.name;
        return fetch(`/api/models/blocks/${fileName}/${fileName}.bbmodel`)
          .then((r) => r.json())
          .then((data: BlocksBbModel) => {
            const { def, textures } = parseBlockBbmodel(data);
            if (def && info.hasStuds) def.hasStuds = true;
            defs[ordinal] = def;
            for (const t of textures) {
              if (!allTextures.has(t.name)) allTextures.set(t.name, t);
              else if (t.biomeTint) allTextures.get(t.name)!.biomeTint = true;
            }
          })
          .catch(() => {});
      });

      Promise.all(fetches).then(() => {
        _blockDefs = defs;
        _blockTextures = Array.from(allTextures.values());
      });
    },

    isBlockDefsReady: () => _blockDefs !== null && _blockTextures !== null,

    getBlockDef: (ordinal: number): McBlockDef | null => _blockDefs?.[ordinal] ?? null,

    getBlockTextures: (): McBlockTextureDef[] => _blockTextures ?? [],
  };
}

// Called from setBlockRegistry (set up in index.ts) before initBlockDefs
export function setRegistryBlocks(
  blocks: { name: string; modelElement: string; gltfModel?: string; liquid?: boolean; hasStuds?: boolean }[],
): void {
  _registryBlocks = blocks;
}

export function getBlockOrdinalByName(blockName: string): number | null {
  if (!_registryBlocks) return null;
  const ordinal = _registryBlocks.findIndex((b) => b.name === blockName);
  return ordinal === -1 ? null : ordinal;
}

export function getRegistryBlockCount(): number {
  return _registryBlocks?.length ?? 0;
}

export function getFaceTexUrl(ordinal: number, faceDir: number): string | null {
  const def = window.mc?.getBlockDef?.(ordinal) as McBlockDef | null;
  if (!def?.faces?.length) return null;
  // Use element 0 for preview/targeting purposes
  const elem0 = def.faces[0];
  const face = elem0[faceDir] ?? elem0.find((f) => f != null) ?? null;
  if (!face) return null;
  const texName = face.matKey.replace(":biome_tint", "");
  const texs: McBlockTextureDef[] = window.mc?.getBlockTextures?.() ?? [];
  return texs.find((t) => t.name === texName)?.url ?? null;
}
