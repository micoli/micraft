/** Chrome's `HeapProfiler.SamplingHeapProfile`, reduced to what the summary reads. */
export interface SamplingHeapProfileNode {
  callFrame: { functionName: string; url: string; lineNumber: number };
  selfSize: number;
  children: SamplingHeapProfileNode[];
}

export interface SamplingHeapProfile {
  head: SamplingHeapProfileNode;
}

export interface HeapShare {
  name: string;
  bytes: number;
}

export interface HeapProfileSummary {
  totalBytes: number;
  byFunction: HeapShare[];
  bySource: HeapShare[];
}

const fileOf = (url: string) => url.split(/[?#]/)[0].split("/").pop() || url;

/**
 * Where the allocating code lives: the Kotlin/Wasm module, BabylonJS, other dependencies, or the app.
 * The Kotlin/Wasm glue reports no URL but real line numbers; WasmGC objects allocated inside Wasm
 * are attributed to it. Native frames have neither.
 */
export function sourceOf({ url, lineNumber }: SamplingHeapProfileNode["callFrame"]): string {
  if (url === "") return lineNumber >= 0 ? "Kotlin/Wasm" : "(native / VM)";
  if (url.startsWith("wasm://")) return "Kotlin/Wasm";
  if (/babylon/i.test(url)) return "BabylonJS";
  if (/node_modules|vendor|chunk-/.test(url)) return "JS dependencies";
  return "JS app";
}

const frameName = (node: SamplingHeapProfileNode) => {
  const { functionName, url, lineNumber } = node.callFrame;
  const where = url ? ` (${fileOf(url)}:${lineNumber + 1})` : "";
  return `${functionName || "(anonymous)"}${where}`;
};

function ranked(totals: Map<string, number>, top: number): HeapShare[] {
  return [...totals.entries()]
    .map(([name, bytes]) => ({ name, bytes }))
    .sort((a, b) => b.bytes - a.bytes)
    .slice(0, top);
}

/**
 * Bottom-up view of a sampling heap profile: live bytes attributed to the function that allocated
 * them (self size), and to the code base that function belongs to.
 */
export function summarizeHeapProfile(profile: SamplingHeapProfile, top = 25): HeapProfileSummary {
  const byFunction = new Map<string, number>();
  const bySource = new Map<string, number>();
  let totalBytes = 0;
  const stack = [profile.head];
  while (stack.length > 0) {
    const node = stack.pop()!;
    stack.push(...node.children);
    if (node.selfSize === 0) continue;
    totalBytes += node.selfSize;
    const fn = frameName(node);
    byFunction.set(fn, (byFunction.get(fn) ?? 0) + node.selfSize);
    const source = sourceOf(node.callFrame);
    bySource.set(source, (bySource.get(source) ?? 0) + node.selfSize);
  }
  return { totalBytes, byFunction: ranked(byFunction, top), bySource: ranked(bySource, top) };
}
