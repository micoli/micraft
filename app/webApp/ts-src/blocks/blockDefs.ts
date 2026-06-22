// Sync with core/.../world/Block.kt BlockType enum (ordinal order)
const BLOCK_NAMES = [
  'AIR', 'BEDROCK', 'STONE', 'DIRT', 'GRASS', 'SAND', 'SANDSTONE', 'GRAVEL', 'SNOW',
  'OAK_LOG', 'OAK_LEAVES', 'PINE_LOG', 'PINE_LEAVES', 'PINE_LEAVES_SNOW', 'FLOWER', 'WEED',
];

// faceDir → bbmodel face key (faceDir: 0=+Z/south, 1=-Z/north, 2=+X/east, 3=-X/west, 4=+Y/up, 5=-Y/down)
const FACEKEY_BY_DIR = ['south', 'north', 'east', 'west', 'up', 'down'] as const;
type FaceKey = typeof FACEKEY_BY_DIR[number];

// Convert bbmodel face uv [x0,y0,x1,y1] (pixel coords) to per-vertex UV array.
// Winding order matches MC_VERTS: k=0 bottom-left, k=1 bottom-right, k=2 top-right, k=3 top-left.
// v = y/H directly (BabylonJS invertY=true makes v=0 the top of the image, matching bbmodel y=0).
function toVertexUV(uv: [number, number, number, number], W: number, H: number): number[] {
  const uMin = uv[0] / W, uMax = uv[2] / W;
  const vTop = uv[1] / H, vBot = uv[3] / H;
  return [uMin, vBot, uMax, vBot, uMax, vTop, uMin, vTop];
}

function parseBbmodel(bbmodel: BlocksBbModel): { defs: (McBlockDef | null)[]; textures: McBlockTextureDef[] } {
  const W = bbmodel.resolution?.width ?? 16;
  const H = bbmodel.resolution?.height ?? 16;

  // Build texture list from bbmodel
  const textures: McBlockTextureDef[] = bbmodel.textures.map(t => ({
    name: t.name,
    url: '/' + t.path,
    hasAlpha: t.mc_alpha === true,
    tint: t.mc_tint,
    biomeTint: false,
  }));

  // Mark textures used as biome_tint
  for (const el of bbmodel.elements) {
    for (const faceKey of (Object.keys(el.faces) as FaceKey[])) {
      const face = el.faces[faceKey];
      if (face?.biome_tint) {
        const texDef = textures[face.texture];
        if (texDef) texDef.biomeTint = true;
      }
    }
  }

  // Build element map by name for fast lookup
  const elemByName = new Map<string, BlocksBbModelElement>();
  for (const el of bbmodel.elements) {
    elemByName.set(el.name, el);
  }

  // Build BlockDef array indexed by ordinal
  const defs: (McBlockDef | null)[] = BLOCK_NAMES.map((blockName, ordinal) => {
    if (ordinal === 0) return null; // AIR

    const el = elemByName.get(blockName);
    if (!el) return null;

    const renderType = (el.render_type as McBlockDef['renderType']) ?? 'solid';

    const faces: (McBlockFaceInfo | null)[] = FACEKEY_BY_DIR.map(faceKey => {
      const face = el.faces[faceKey as FaceKey];
      if (!face) return null;

      const texDef = textures[face.texture];
      if (!texDef) return null;

      const uv = face.uv ?? [0, 0, W, H];
      const matKey = face.biome_tint ? texDef.name + ':biome_tint' : texDef.name;

      return { matKey, uv: toVertexUV(uv as [number, number, number, number], W, H) };
    });

    return { name: blockName, renderType, faces };
  });

  return { defs, textures };
}

let _blockDefs: (McBlockDef | null)[] | null = null;
let _blockTextures: McBlockTextureDef[] | null = null;

export function registerBlockDefs(): void {
  window.mcInitBlockDefs = () => {
    fetch('/models/blocks.bbmodel')
      .then(r => r.json())
      .then((data: BlocksBbModel) => {
        const { defs, textures } = parseBbmodel(data);
        _blockDefs = defs;
        _blockTextures = textures;
      })
      .catch(() => {});
  };

  window.mcIsBlockDefsReady = () => _blockDefs !== null && _blockTextures !== null;

  window.mcGetBlockDef = (ordinal: number): McBlockDef | null =>
    _blockDefs?.[ordinal] ?? null;

  window.mcGetBlockTextures = (): McBlockTextureDef[] =>
    _blockTextures ?? [];
}
