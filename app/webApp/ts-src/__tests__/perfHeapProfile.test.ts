import { describe, expect, it } from "vitest";
import { sourceOf, summarizeHeapProfile, type SamplingHeapProfileNode } from "../perf/helpers/heapProfile";

const node = (
  functionName: string,
  url: string,
  selfSize: number,
  children: SamplingHeapProfileNode[] = [],
): SamplingHeapProfileNode => ({ callFrame: { functionName, url, lineNumber: 9 }, selfSize, children });

describe("heap profile summary", () => {
  it("attributes live bytes to the allocating function, merging its call sites", () => {
    const profile = {
      head: node("(root)", "", 0, [
        node("render", "http://localhost/game.js", 10, [node("meshChunk", "wasm://wasm/abc", 300)]),
        node("tick", "http://localhost/game.js", 0, [node("meshChunk", "wasm://wasm/abc", 200)]),
      ]),
    };

    const summary = summarizeHeapProfile(profile);

    expect(summary.totalBytes).toBe(510);
    expect(summary.byFunction[0]).toEqual({ name: "meshChunk (abc:10)", bytes: 500 });
    expect(summary.bySource).toEqual([
      { name: "Kotlin/Wasm", bytes: 500 },
      { name: "JS app", bytes: 10 },
    ]);
  });

  it("classifies allocating code by where it lives", () => {
    const at = (url: string, lineNumber = 0) => sourceOf({ functionName: "f", url, lineNumber });
    expect(at("wasm://wasm/0123")).toBe("Kotlin/Wasm");
    expect(at("", 168)).toBe("Kotlin/Wasm");
    expect(at("http://localhost/assets/babylon.core.js")).toBe("BabylonJS");
    expect(at("http://localhost/assets/chunk-react.js")).toBe("JS dependencies");
    expect(at("http://localhost/mc_bindings.js")).toBe("JS app");
    expect(at("", -1)).toBe("(native / VM)");
  });
});
