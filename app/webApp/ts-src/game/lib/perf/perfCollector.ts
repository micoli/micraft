export interface FrameCounters {
  drawCalls: number;
  activeMeshes: number;
  activeIndices: number;
  textures: number;
  gpuFrameMs: number | null;
}

export interface MemoryReading {
  jsHeapUsedBytes: number | null;
  wasmMemoryBytes: number | null;
  gpuBufferBytes: number | null;
}

/** What the collector reads each frame; the Babylon adapter in production, a fake in tests. */
export interface PerfSource {
  now(): number;
  onFrame(cb: () => void): () => void;
  counters(): FrameCounters;
  memory(): MemoryReading;
}

export interface Percentiles {
  p50: number;
  p95: number;
  p99: number;
  max: number;
}

export interface AvgMax {
  avg: number;
  max: number;
}

export interface ClientPerfSnapshot {
  durationMs: number;
  frames: number;
  frameMs: Percentiles;
  /** Frames longer than 1.5 × the median frame: each one missed at least one display refresh. */
  longFrames: number;
  gpuFrameMs: Percentiles | null;
  drawCalls: AvgMax;
  activeMeshes: AvgMax;
  activeIndices: AvgMax;
  textures: number;
  memory: MemoryReading;
}

export interface PerfCollector {
  reset(): void;
  snapshot(): ClientPerfSnapshot;
  dispose(): void;
}

function percentiles(values: number[]): Percentiles {
  if (values.length === 0) return { p50: 0, p95: 0, p99: 0, max: 0 };
  const sorted = [...values].sort((a, b) => a - b);
  const rank = (p: number) => sorted[Math.min(sorted.length - 1, Math.max(0, Math.ceil(p * sorted.length) - 1))];
  return { p50: rank(0.5), p95: rank(0.95), p99: rank(0.99), max: sorted[sorted.length - 1] };
}

const LONG_FRAME_FACTOR = 1.5;

function countLongFrames(intervals: number[], medianMs: number): number {
  return intervals.filter((ms) => ms > medianMs * LONG_FRAME_FACTOR).length;
}

class AvgMaxAccumulator {
  private sum = 0;
  private count = 0;
  private max = 0;

  add(value: number): void {
    this.sum += value;
    this.count++;
    this.max = Math.max(this.max, value);
  }

  result(): AvgMax {
    return { avg: this.count === 0 ? 0 : this.sum / this.count, max: this.max };
  }
}

export function createPerfCollector(source: PerfSource): PerfCollector {
  let startMs = source.now();
  let lastFrameMs = startMs;
  let frameIntervals: number[] = [];
  let gpuFrameTimes: number[] = [];
  let drawCalls = new AvgMaxAccumulator();
  let activeMeshes = new AvgMaxAccumulator();
  let activeIndices = new AvgMaxAccumulator();
  let textures = 0;

  const unsubscribe = source.onFrame(() => {
    const now = source.now();
    frameIntervals.push(now - lastFrameMs);
    lastFrameMs = now;
    const counters = source.counters();
    drawCalls.add(counters.drawCalls);
    activeMeshes.add(counters.activeMeshes);
    activeIndices.add(counters.activeIndices);
    textures = counters.textures;
    if (counters.gpuFrameMs !== null) gpuFrameTimes.push(counters.gpuFrameMs);
  });

  return {
    reset() {
      startMs = source.now();
      lastFrameMs = startMs;
      frameIntervals = [];
      gpuFrameTimes = [];
      drawCalls = new AvgMaxAccumulator();
      activeMeshes = new AvgMaxAccumulator();
      activeIndices = new AvgMaxAccumulator();
    },
    snapshot() {
      const frameMs = percentiles(frameIntervals);
      return {
        durationMs: source.now() - startMs,
        frames: frameIntervals.length,
        frameMs,
        longFrames: countLongFrames(frameIntervals, frameMs.p50),
        gpuFrameMs: gpuFrameTimes.length === 0 ? null : percentiles(gpuFrameTimes),
        drawCalls: drawCalls.result(),
        activeMeshes: activeMeshes.result(),
        activeIndices: activeIndices.result(),
        textures,
        memory: source.memory(),
      };
    },
    dispose: unsubscribe,
  };
}
