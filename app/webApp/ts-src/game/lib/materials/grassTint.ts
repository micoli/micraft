export const DEFAULT_GRASS_TINT: readonly [number, number, number] = [0.47, 0.75, 0.35];

export const BIOME_TINT_SUFFIX = ":biome_tint";

const CHUNK_SIZE = 16;
const DEFAULT_PACKED =
  (Math.round(DEFAULT_GRASS_TINT[0] * 255) << 16) |
  (Math.round(DEFAULT_GRASS_TINT[1] * 255) << 8) |
  Math.round(DEFAULT_GRASS_TINT[2] * 255);

/** One packed 0xRRGGBB per column of a chunk, indexed by `lz * 16 + lx`. */
export function defaultGrassTints(): Int32Array {
  return new Int32Array(CHUNK_SIZE * CHUNK_SIZE).fill(DEFAULT_PACKED);
}

/**
 * The [r, g, b, layer] a biome-tinted face at world column (wx, wz) carries per vertex, written
 * into `out` so the per-face hot loop allocates nothing.
 */
export function grassTintLayer(tints: Int32Array, wx: number, wz: number, layer: number, out: number[]): number[] {
  const rgb = tints[(wz & (CHUNK_SIZE - 1)) * CHUNK_SIZE + (wx & (CHUNK_SIZE - 1))];
  out[0] = ((rgb >> 16) & 0xff) / 255;
  out[1] = ((rgb >> 8) & 0xff) / 255;
  out[2] = (rgb & 0xff) / 255;
  out[3] = layer;
  return out;
}
