// faceDir → bbmodel face key (faceDir: 0=+Z/south, 1=-Z/north, 2=+X/east, 3=-X/west, 4=+Y/up, 5=-Y/down)
const FACEKEY_BY_DIR = ["south", "north", "east", "west", "up", "down"] as const;
type FaceKey = (typeof FACEKEY_BY_DIR)[number];

// Convert bbmodel face uv [x0,y0,x1,y1] (pixel coords) to per-vertex UV array.
// Winding order matches MC_VERTS: k=0 bottom-left, k=1 bottom-right, k=2 top-right, k=3 top-left.
// v = y/H directly (BabylonJS invertY=true makes v=0 the top of the image, matching bbmodel y=0).
function toVertexUV(uv: [number, number, number, number], W: number, H: number): number[] {
  const uMin = uv[0] / W,
    uMax = uv[2] / W;
  const vTop = uv[1] / H,
    vBot = uv[3] / H;
  return [uMin, vBot, uMax, vBot, uMax, vTop, uMin, vTop];
}

function parseSingleBlockBbmodel(bbmodel: BlocksBbModel): { def: McBlockDef | null; textures: McBlockTextureDef[] } {
  const W = bbmodel.resolution?.width ?? 16;
  const H = bbmodel.resolution?.height ?? 16;

  const textures: McBlockTextureDef[] = bbmodel.textures.map((t) => ({
    name: t.name,
    url: "/" + t.path,
    hasAlpha: t.mc_alpha === true,
    tint: t.mc_tint,
    biomeTint: false,
  }));

  const el = bbmodel.elements[0];
  if (!el) return { def: null, textures };

  for (const faceKey of Object.keys(el.faces) as FaceKey[]) {
    const face = el.faces[faceKey];
    if (face?.biome_tint) {
      const texDef = textures[face.texture];
      if (texDef) texDef.biomeTint = true;
    }
  }

  const renderType = (el.render_type as McBlockDef["renderType"]) ?? "solid";

  const faces: (McBlockFaceInfo | null)[] = FACEKEY_BY_DIR.map((faceKey) => {
    const face = el.faces[faceKey as FaceKey];
    if (!face) return null;

    const texDef = textures[face.texture];
    if (!texDef) return null;

    const uv = face.uv ?? [0, 0, W, H];
    const matKey = face.biome_tint ? texDef.name + ":biome_tint" : texDef.name;

    return { matKey, uv: toVertexUV(uv as [number, number, number, number], W, H) };
  });

  return { def: { name: el.name, renderType, faces }, textures };
}

const WATER_MAT_KEY = "water";
const LIQUID_UV = [0, 0, 1, 0, 1, 1, 0, 1];

let _blockDefs: (McBlockDef | null)[] | null = null;
let _blockTextures: McBlockTextureDef[] | null = null;

// Loaded from RegistrySync — ordinal-indexed list of block infos
let _registryBlocks: { name: string; modelElement: string; liquid?: boolean }[] | null = null;

export function registerBlockDefs(): void {
  window.mcInitBlockDefs = () => {
    if (!_registryBlocks) return;

    const allTextures: Map<string, McBlockTextureDef> = new Map();
    const defs: (McBlockDef | null)[] = new Array(_registryBlocks.length).fill(null);

    const fetches = _registryBlocks.map((info, ordinal) => {
      if (info.name === "AIR") return Promise.resolve();
      if (info.liquid) {
        const faces: McBlockFaceInfo[] = Array.from({ length: 6 }, () => ({ matKey: WATER_MAT_KEY, uv: LIQUID_UV }));
        defs[ordinal] = { name: info.name, renderType: "liquid", faces };
        return Promise.resolve();
      }
      const fileName = info.modelElement || info.name;
      return fetch(`/models/blocks/${fileName}.bbmodel`)
        .then((r) => r.json())
        .then((data: BlocksBbModel) => {
          const { def, textures } = parseSingleBlockBbmodel(data);
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
  };

  window.mcIsBlockDefsReady = () => _blockDefs !== null && _blockTextures !== null;

  window.mcGetBlockDef = (ordinal: number): McBlockDef | null => _blockDefs?.[ordinal] ?? null;

  window.mcGetBlockTextures = (): McBlockTextureDef[] => _blockTextures ?? [];
}

// Called from mcSetBlockRegistry (set up in minimap.ts) before mcInitBlockDefs
export function setRegistryBlocks(blocks: { name: string; modelElement: string; liquid?: boolean }[]): void {
  _registryBlocks = blocks;
}
