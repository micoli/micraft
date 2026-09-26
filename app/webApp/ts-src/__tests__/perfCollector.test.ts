import { describe, expect, it } from "vitest";
import { createPerfCollector, type FrameCounters, type PerfSource } from "../game/lib/perf/perfCollector";

class FakeSource implements PerfSource {
  time = 0;
  counterValues: FrameCounters = { drawCalls: 0, activeMeshes: 0, activeIndices: 0, textures: 0, gpuFrameMs: null };
  private listener: (() => void) | null = null;

  now(): number {
    return this.time;
  }

  onFrame(cb: () => void): () => void {
    this.listener = cb;
    return () => {
      this.listener = null;
    };
  }

  counters(): FrameCounters {
    return this.counterValues;
  }

  memory() {
    return { jsHeapUsedBytes: null, wasmMemoryBytes: null, gpuBufferBytes: null };
  }

  frameAfter(ms: number): void {
    this.time += ms;
    this.listener?.();
  }
}

describe("perfCollector", () => {
  it("reports nearest-rank frame-time percentiles since reset", () => {
    const source = new FakeSource();
    const collector = createPerfCollector(source);
    collector.reset();

    for (const ms of [...Array(100).keys()].map((i) => i + 1).reverse()) source.frameAfter(ms);

    const snap = collector.snapshot();
    expect(snap.frames).toBe(100);
    expect(snap.frameMs).toEqual({ p50: 50, p95: 95, p99: 99, max: 100 });
    expect(snap.durationMs).toBe(5050);
  });

  it("discards frames recorded before reset", () => {
    const source = new FakeSource();
    const collector = createPerfCollector(source);
    source.frameAfter(500);
    collector.reset();
    source.frameAfter(16);

    const snap = collector.snapshot();
    expect(snap.frames).toBe(1);
    expect(snap.frameMs.max).toBe(16);
  });

  it("reports render counters as average and max over the frames", () => {
    const source = new FakeSource();
    const collector = createPerfCollector(source);
    collector.reset();
    for (const drawCalls of [100, 300]) {
      source.counterValues = {
        drawCalls,
        activeMeshes: drawCalls / 2,
        activeIndices: drawCalls * 10,
        textures: 7,
        gpuFrameMs: null,
      };
      source.frameAfter(16);
    }

    const snap = collector.snapshot();
    expect(snap.drawCalls).toEqual({ avg: 200, max: 300 });
    expect(snap.activeMeshes).toEqual({ avg: 100, max: 150 });
    expect(snap.activeIndices).toEqual({ avg: 2000, max: 3000 });
    expect(snap.textures).toBe(7);
    expect(snap.gpuFrameMs).toBeNull();
  });

  it("reports gpu frame-time percentiles when the source measures them", () => {
    const source = new FakeSource();
    const collector = createPerfCollector(source);
    collector.reset();
    for (const gpuFrameMs of [4, 8]) {
      source.counterValues = { ...source.counterValues, gpuFrameMs };
      source.frameAfter(16);
    }

    expect(collector.snapshot().gpuFrameMs).toEqual({ p50: 4, p95: 8, p99: 8, max: 8 });
  });

  it("reads memory from the source at snapshot time", () => {
    const source = new FakeSource();
    source.memory = () => ({ jsHeapUsedBytes: 1_000, wasmMemoryBytes: 2_000, gpuBufferBytes: 3_000 });
    const collector = createPerfCollector(source);

    expect(collector.snapshot().memory).toEqual({
      jsHeapUsedBytes: 1_000,
      wasmMemoryBytes: 2_000,
      gpuBufferBytes: 3_000,
    });
  });
});
