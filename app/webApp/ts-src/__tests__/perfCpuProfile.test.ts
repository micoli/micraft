import { describe, expect, it } from "vitest";
import { summarizeCpuProfile, type CpuProfileNode } from "../perf/helpers/cpuProfile";

const node = (id: number, functionName: string, url = "", lineNumber = -1): CpuProfileNode => ({
  id,
  callFrame: { functionName, url, lineNumber },
});

describe("cpu profile summary", () => {
  it("charges each sample interval to the function it landed in, grouped by origin", () => {
    const profile = {
      nodes: [
        node(1, "(root)"),
        node(2, "render", "http://localhost/babylon.js", 0),
        node(3, "meshChunk", "", 168),
        node(4, "(garbage collector)"),
        node(5, "(idle)"),
      ],
      startTime: 0,
      endTime: 10_000,
      samples: [2, 3, 3, 4, 5],
      // µs since the previous sample: each interval belongs to the sample after it.
      timeDeltas: [0, 1_000, 2_000, 3_000, 500, 3_500],
    };

    const summary = summarizeCpuProfile(profile);

    expect(summary.durationMs).toBe(10);
    expect(summary.busyMs).toBe(6.5);
    expect(summary.byFunction).toEqual([
      { name: "meshChunk", bytes: 5 },
      { name: "Idle", bytes: 3.5 },
      { name: "render (babylon.js:1)", bytes: 1 },
      { name: "Garbage collection", bytes: 0.5 },
    ]);
    expect(summary.bySource.map((s) => s.name)).toEqual(["Kotlin/Wasm", "Idle", "BabylonJS", "Garbage collection"]);
  });

  it("names functions that shadow Object.prototype members like any other", () => {
    const profile = {
      nodes: [node(1, "constructor", "http://localhost/babylon.js", 0)],
      startTime: 0,
      endTime: 2_000,
      samples: [1],
      timeDeltas: [0, 2_000],
    };

    expect(summarizeCpuProfile(profile).byFunction).toEqual([{ name: "constructor (babylon.js:1)", bytes: 2 }]);
  });
});
