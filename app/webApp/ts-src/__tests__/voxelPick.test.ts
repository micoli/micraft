import { describe, expect, it } from "vitest";
import { parseVoxelHit } from "../game/lib/targeting/voxelPick";

describe("parseVoxelHit", () => {
  it("splits the hit point and the face normal", () => {
    expect(parseVoxelHit("3.5,4,-2.25,0,1,0")).toEqual({ point: [3.5, 4, -2.25], normal: [0, 1, 0] });
  });

  it("treats an empty or malformed answer as a miss", () => {
    expect(parseVoxelHit("")).toBeNull();
    expect(parseVoxelHit("1,2,3")).toBeNull();
    expect(parseVoxelHit("1,2,x,0,1,0")).toBeNull();
  });
});
