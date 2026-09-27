import { describe, expect, it } from "vitest";
import { BLOCK_ARRAY_FRAG, BLOCK_ARRAY_VERT } from "../game/lib/block";
import { buildTerrainLayerTable, packLayer } from "../game/lib/materials/terrainTextureArray";

describe("terrain texture array", () => {
  it("maps every terrain material key to its layer and tint", () => {
    const textures = [
      { name: "stone", url: "", hasAlpha: false },
      { name: "grass_top", url: "", hasAlpha: false, tint: [0.5, 1, 0.5] as [number, number, number], biomeTint: true },
      { name: "water", url: "", hasAlpha: true },
    ];
    const layers = {
      layerOf: new Map([
        ["stone", 0],
        ["grass_top", 1],
        ["water", 2],
      ]),
      whiteLayer: 3,
    };

    const table = buildTerrainLayerTable(textures, [{ name: "red", hex: "ff0000", r: 255, g: 0, b: 0 }], layers);

    expect(table).toEqual({
      stone: [1, 1, 1, 0],
      grass_top: [0.5, 1, 0.5, 1],
      "grass_top:biome_tint": [0.47, 0.75, 0.35, 1],
      "plain:ff0000": [1, 0, 0, 3],
    });
  });

  it("packs a layer bottom row first", () => {
    const size = 2;
    const top = [1, 1, 1, 1, 2, 2, 2, 2];
    const bottom = [3, 3, 3, 3, 4, 4, 4, 4];
    const out = new Uint8Array(2 * size * size * 4);

    packLayer(out, new Uint8ClampedArray([...top, ...bottom]), size, 1);

    expect(Array.from(out.subarray(16))).toEqual([...bottom, ...top]);
  });

  it("derives the array shaders from the block shaders", () => {
    expect(BLOCK_ARRAY_VERT).toContain("attribute vec4 tintLayer;");
    expect(BLOCK_ARRAY_VERT).toContain("vTintLayer = tintLayer;");
    expect(BLOCK_ARRAY_FRAG).toContain("uniform highp sampler2DArray textureArray;");
    expect(BLOCK_ARRAY_FRAG).toContain("texture(textureArray, vec3(vUv, vTintLayer.a))");
    expect(BLOCK_ARRAY_FRAG).not.toContain("uniform vec3 tint;");
    expect(BLOCK_ARRAY_FRAG).not.toContain("textureSampler");
  });
});
