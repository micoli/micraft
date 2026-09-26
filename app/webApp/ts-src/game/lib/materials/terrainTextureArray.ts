import type { Scene } from "@babylonjs/core";

/** Material key of the single terrain material the texture array replaces the others with. */
export const TERRAIN_ARRAY_MAT_KEY = "terrain:array";

/** Material keys that must keep their own material (not a block ShaderMaterial). */
const OWN_MATERIAL_KEYS = new Set(["water"]);

/** Per material key: [r, g, b, layer]; r < 0 means the live biome tint. */
export type TerrainLayerTable = Record<string, [number, number, number, number]>;

export interface TerrainLayers {
  /** Layer of each texture, by texture name; the last layer is plain white. */
  layerOf: Map<string, number>;
  whiteLayer: number;
}

/** Which texture layer and tint every terrain material key stands for. */
export function buildTerrainLayerTable(
  textures: McBlockTextureDef[],
  plainColors: McPlainColor[],
  layers: TerrainLayers,
): TerrainLayerTable {
  const table: TerrainLayerTable = {};
  for (const t of textures) {
    const layer = layers.layerOf.get(t.name);
    if (layer === undefined || OWN_MATERIAL_KEYS.has(t.name)) continue;
    const [r, g, b] = t.tint ?? [1, 1, 1];
    table[t.name] = [r, g, b, layer];
    if (t.biomeTint) table[`${t.name}:biome_tint`] = [-1, -1, -1, layer];
  }
  for (const c of plainColors) table[`plain:${c.hex}`] = [c.r / 255, c.g / 255, c.b / 255, layers.whiteLayer];
  return table;
}

/**
 * Copies one decoded RGBA image of `size`×`size` into its layer, bottom row first: the block
 * textures are sampled with invertY (v = 1 at the image's top row), and texImage3D cannot flip.
 */
export function packLayer(out: Uint8Array, rgba: Uint8ClampedArray, size: number, layer: number): void {
  const rowBytes = size * 4;
  const base = layer * size * rowBytes;
  for (let row = 0; row < size; row++) {
    const src = (size - 1 - row) * rowBytes;
    out.set(rgba.subarray(src, src + rowBytes), base + row * rowBytes);
  }
}

let enabled = false;
let prepared: { size: number; data: Uint8Array; layers: TerrainLayers } | null = null;

/** In-game on WebGL2 only (engine.ts); the admin editors and WebGL1 keep one material per texture. */
export function enableTerrainTextureArray(): void {
  enabled = true;
}

export function isTerrainTextureArrayActive(): boolean {
  return enabled && prepared !== null;
}

function pixelsOf(bitmap: ImageBitmap, size: number): Uint8ClampedArray {
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext("2d", { willReadFrequently: true })!;
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(bitmap, 0, 0, size, size);
  return ctx.getImageData(0, 0, size, size).data;
}

/**
 * Decodes every block texture into one RGBA volume, all upscaled (nearest) to the largest texture
 * size — UVs are normalised, so a resized layer maps exactly as the original did. A failure leaves
 * the array inactive: terrain falls back to one material per texture.
 */
export async function prepareTerrainTextureArray(textures: McBlockTextureDef[]): Promise<void> {
  if (!enabled || textures.length === 0) return;
  try {
    const bitmaps = await Promise.all(textures.map(async (t) => createImageBitmap(await (await fetch(t.url)).blob())));
    const size = Math.max(...bitmaps.map((b) => Math.max(b.width, b.height)));
    const whiteLayer = textures.length;
    const data = new Uint8Array((textures.length + 1) * size * size * 4);
    const layerOf = new Map<string, number>();
    bitmaps.forEach((bitmap, i) => {
      packLayer(data, pixelsOf(bitmap, size), size, i);
      layerOf.set(textures[i].name, i);
      bitmap.close();
    });
    data.fill(255, whiteLayer * size * size * 4);
    prepared = { size, data, layers: { layerOf, whiteLayer } };
  } catch (e) {
    console.warn("[MiCraft] terrain texture array unavailable, one material per texture instead", e);
    prepared = null;
  }
}

export function terrainLayerTable(
  textures: McBlockTextureDef[],
  plainColors: McPlainColor[],
): TerrainLayerTable | null {
  if (!isTerrainTextureArrayActive()) return null;
  return buildTerrainLayerTable(textures, plainColors, prepared!.layers);
}

/** The one texture every terrain face samples, as a 2D array with one layer per block texture. */
export function createTerrainTextureArray(scene: Scene): InstanceType<typeof BABYLON.RawTexture2DArray> | null {
  if (!isTerrainTextureArrayActive()) return null;
  const { size, data, layers } = prepared!;
  const texture = new BABYLON.RawTexture2DArray(
    data,
    size,
    size,
    layers.whiteLayer + 1,
    BABYLON.Constants.TEXTUREFORMAT_RGBA,
    scene,
    false,
    false,
    BABYLON.Texture.NEAREST_SAMPLINGMODE,
  );
  texture.wrapU = BABYLON.Texture.WRAP_ADDRESSMODE;
  texture.wrapV = BABYLON.Texture.WRAP_ADDRESSMODE;
  return texture;
}
