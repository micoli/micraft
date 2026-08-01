import { describe, expect, it } from "vitest";
import {
  LAYER_DEFAULTS,
  LAYER_KEYS,
  fitScale,
  gridLinesFor,
  hitRadiusFor,
  loadLayers,
  markerRadiusFor,
  pickNpcAt,
} from "../admin/simulator/arenaView";

/** Same projection as useArenaCamera, with an explicit camera. */
function projector(camX: number, camZ: number, ppb: number, w: number, h: number) {
  return (wx: number, wz: number): [number, number] => [(wx - camX) * ppb + w / 2, -(wz - camZ) * ppb + h / 2];
}

describe("arena view metrics", () => {
  it("clamps the marker radius so NPCs stay visible when zoomed out and readable zoomed in", () => {
    expect(markerRadiusFor(0.5)).toBe(3);
    expect(markerRadiusFor(64)).toBe(9);
    expect(markerRadiusFor(5)).toBeCloseTo(3.5);
  });

  it("covers the whole arena with grid lines", () => {
    const lines = gridLinesFor(100, 3);
    expect(lines[0]).toBe(-100);
    expect(lines.at(-1)).toBeLessThanOrEqual(100);
    expect(lines.length).toBeGreaterThan(2);
  });

  it("keeps the hit radius a bit larger than the drawn marker", () => {
    expect(hitRadiusFor(3)).toBeGreaterThan(markerRadiusFor(3));
  });
});

describe("layer defaults", () => {
  it("has names off and everything else on", () => {
    // one label per NPC is unreadable past a few dozen, which is the normal case here
    expect(LAYER_DEFAULTS.names).toBe(false);
    expect(LAYER_KEYS.filter((key) => key !== "names").every((key) => LAYER_DEFAULTS[key])).toBe(true);
  });

  it("covers every layer key", () => {
    expect(Object.keys(LAYER_DEFAULTS).sort()).toEqual([...LAYER_KEYS].sort());
  });

  it("falls back to the defaults when storage is unreadable", () => {
    // this environment has no localStorage at all, which is exactly the guarded path
    expect(loadLayers()).toEqual(LAYER_DEFAULTS);
  });
});

describe("fitScale", () => {
  it("fits the arena inside the box, padding included", () => {
    // 200 blocks wide + 8 of padding into 624 px
    expect(fitScale(624, 624, 100)).toBe(3);
  });

  it("uses the smaller side, so the other one cannot overflow", () => {
    expect(fitScale(2000, 624, 100)).toBe(3);
    expect(fitScale(624, 2000, 100)).toBe(3);
  });

  it("stays within the zoom limits", () => {
    expect(fitScale(100_000, 100_000, 10)).toBeLessThanOrEqual(64);
    expect(fitScale(20, 20, 10_000)).toBeGreaterThanOrEqual(0.5);
  });

  it("falls back to the initial scale before the first measure", () => {
    // 0 or Infinity here would poison every world↔screen conversion
    expect(fitScale(0, 0, 100)).toBe(3);
    expect(fitScale(1, 1, 100)).toBe(3);
  });
});

describe("pickNpcAt", () => {
  const w2s = projector(0, 0, 4, 800, 600);
  // centre of the viewport
  const alpha = { id: "a", x: 0, z: 0 };
  // 10 blocks east → 40 px right of centre
  const beta = { id: "b", x: 10, z: 0 };

  it("returns the NPC under the pointer", () => {
    const hit = pickNpcAt([alpha, beta], w2s, 400, 300, 4);
    expect(hit?.npc.id).toBe("a");
    expect(hit?.sx).toBe(400);
    expect(hit?.sy).toBe(300);
  });

  it("respects the Z flip: positive Z is up on screen", () => {
    const north = { id: "n", x: 0, z: 10 };
    const [, sy] = w2s(north.x, north.z);
    expect(sy).toBeLessThan(300);
    expect(pickNpcAt([north], w2s, 400, sy, 4)?.npc.id).toBe("n");
  });

  it("returns null when nothing is close enough", () => {
    expect(pickNpcAt([alpha, beta], w2s, 700, 500, 4)).toBeNull();
  });

  it("prefers the closest NPC when markers overlap", () => {
    const near = { id: "near", x: 0.2, z: 0 };
    const hit = pickNpcAt([alpha, near], w2s, w2s(near.x, near.z)[0], 300, 4);
    expect(hit?.npc.id).toBe("near");
  });

  it("widens the tolerance as the zoom grows", () => {
    const justOutsideAtLowZoom = markerRadiusFor(1) + 5;
    expect(pickNpcAt([alpha], w2s, 400 + justOutsideAtLowZoom, 300, 1)).toBeNull();
    expect(pickNpcAt([alpha], w2s, 400 + justOutsideAtLowZoom, 300, 64)).not.toBeNull();
  });

  it("handles an empty arena", () => {
    expect(pickNpcAt([], w2s, 400, 300, 4)).toBeNull();
  });
});
