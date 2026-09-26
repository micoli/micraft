import { sourceOf, type HeapShare } from "./heapProfile";

/** Chrome's `Profiler.Profile`, reduced to what the summary reads. */
export interface CpuProfileNode {
  id: number;
  callFrame: { functionName: string; url: string; lineNumber: number };
  hitCount?: number;
  children?: number[];
}

export interface CpuProfile {
  nodes: CpuProfileNode[];
  startTime: number;
  endTime: number;
  samples?: number[];
  timeDeltas?: number[];
}

export interface CpuProfileSummary {
  /** Wall time the profile covered, in ms. */
  durationMs: number;
  /** Main-thread time spent doing anything (not idle), in ms. */
  busyMs: number;
  byFunction: HeapShare[];
  bySource: HeapShare[];
}

// V8's synthetic nodes: time outside any JS/Wasm function.
const VM_STATES = new Map([
  ["(garbage collector)", "Garbage collection"],
  ["(program)", "Browser (layout, paint, GPU commands, event dispatch)"],
  ["(idle)", "Idle"],
]);

const fileOf = (url: string) => url.split(/[?#]/)[0].split("/").pop() || url;

const frameName = ({ functionName, url, lineNumber }: CpuProfileNode["callFrame"]) =>
  `${functionName || "(anonymous)"}${url ? ` (${fileOf(url)}:${lineNumber + 1})` : ""}`;

function ranked(totals: Map<string, number>, top: number): HeapShare[] {
  return [...totals.entries()]
    .map(([name, bytes]) => ({ name, bytes }))
    .sort((a, b) => b.bytes - a.bytes)
    .slice(0, top);
}

/**
 * Bottom-up self time of a sampling CPU profile: each sample's interval is charged to the node it
 * landed in, then grouped by function and by the code base the function belongs to. `bytes` holds
 * milliseconds here, so the shape matches the heap summary.
 */
export function summarizeCpuProfile(profile: CpuProfile, top = 30): CpuProfileSummary {
  const byId = new Map(profile.nodes.map((n) => [n.id, n]));
  const selfMs = new Map<number, number>();
  const samples = profile.samples ?? [];
  const deltas = profile.timeDeltas ?? [];
  // A sample's delta is the time since the previous one; it belongs to the node sampled next.
  for (let i = 0; i < samples.length; i++) {
    const ms = (deltas[i + 1] ?? 0) / 1000;
    selfMs.set(samples[i], (selfMs.get(samples[i]) ?? 0) + ms);
  }

  const byFunction = new Map<string, number>();
  const bySource = new Map<string, number>();
  let busyMs = 0;
  for (const [id, ms] of selfMs) {
    const node = byId.get(id);
    if (!node || ms === 0) continue;
    const vmState = VM_STATES.get(node.callFrame.functionName);
    if (vmState !== "Idle") busyMs += ms;
    const fn = vmState ?? frameName(node.callFrame);
    byFunction.set(fn, (byFunction.get(fn) ?? 0) + ms);
    const source = vmState ?? sourceOf(node.callFrame);
    bySource.set(source, (bySource.get(source) ?? 0) + ms);
  }
  return {
    durationMs: (profile.endTime - profile.startTime) / 1000,
    busyMs,
    byFunction: ranked(byFunction, top),
    bySource: ranked(bySource, top),
  };
}
