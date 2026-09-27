import { describe, expect, it } from "vitest";
import { BLOCK_VERTEX_TINT_FRAG, BLOCK_VERTEX_TINT_VERT } from "../game/lib/block";
import { DEFAULT_GRASS_TINT, defaultGrassTints, grassTintLayer } from "../game/lib/materials/grassTint";

describe("grass tint per column", () => {
  it("reads the column's packed color, negative world coordinates included", () => {
    const tints = defaultGrassTints();
    tints[15 * 16 + 1] = 0xff8000;

    expect(grassTintLayer(tints, -31, 47, 3, [0, 0, 0, 0])).toEqual([1, 128 / 255, 0, 3]);
  });

  it("defaults every column to the default grass color", () => {
    const [r, g, b] = grassTintLayer(defaultGrassTints(), 5, 9, 0, [0, 0, 0, 0]);

    [r, g, b].forEach((v, c) => expect(v).toBeCloseTo(DEFAULT_GRASS_TINT[c], 2));
  });

  it("takes the tint from the vertex instead of a uniform", () => {
    expect(BLOCK_VERTEX_TINT_VERT).toContain("attribute vec4 tintLayer;");
    expect(BLOCK_VERTEX_TINT_FRAG).not.toContain("uniform vec3 tint;");
    expect(BLOCK_VERTEX_TINT_FRAG).toContain("vec3 tint = vTintLayer.rgb;");
    expect(BLOCK_VERTEX_TINT_FRAG).toContain("texture2D(textureSampler, vUv)");
  });
});
