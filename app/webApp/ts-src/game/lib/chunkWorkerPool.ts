// Dispatches chunk-mesh geometry jobs to a small pool of chunkMeshWorker.ts instances and
// collects their results for polling consumption from Kotlin (see ChunkManager.kt's
// drainPendingChunks / jsRequestChunkMesh / jsIsChunkMeshReady / jsConsumeChunkMeshResult in
// BabylonBindingsChunkWorker.kt). Kotlin/Wasm interop doesn't bind JS Promises well across the
// boundary, so this is poll-based rather than promise-based, matching the existing
// pendingChunks/activeRender polling style already used by ChunkManager.

export interface WorkerFaceGroup {
  key: string;
  p: Float32Array;
  n: Float32Array;
  u: Float32Array;
  c: Float32Array;
  i: Int32Array;
  v: number;
  ic: number;
}

export interface WorkerGltfEntry {
  typeOrd: number;
  posKeys: string[];
}

interface MeshResultMessage {
  type: "meshResult";
  reqId: string;
  key: string;
  groups: WorkerFaceGroup[];
  gltf: WorkerGltfEntry[];
}

// Bounded like HttpChunkFetcher's MAX_CONCURRENT — one in-flight mesh job per worker at a time.
const POOL_SIZE = Math.max(1, Math.min((navigator.hardwareConcurrency || 4) - 1, 4));

const workers: Worker[] = [];
let nextWorker = 0;
const resultsByKey: Map<string, MeshResultMessage> = new Map();

function workerScriptUrl(): string {
  const v = window.mcBuildInfo.mcBindings ?? "";
  return `/chunk-mesh-worker.js?v=${encodeURIComponent(v)}`;
}

function sendBlockDefs(worker: Worker): void {
  const defs: { typeOrd: number; def: McBlockDef }[] = [];
  for (let typeOrd = 0; typeOrd < 512; typeOrd++) {
    const def = window.mc.getBlockDef(typeOrd);
    if (def) defs.push({ typeOrd, def });
  }
  worker.postMessage({ type: "blockDefs", defs });
}

function ensurePool(): Worker[] {
  if (workers.length > 0) return workers;
  const url = workerScriptUrl();
  for (let i = 0; i < POOL_SIZE; i++) {
    const worker = new Worker(url);
    worker.onmessage = (ev: MessageEvent<MeshResultMessage>) => {
      if (ev.data.type === "meshResult") resultsByKey.set(ev.data.key, ev.data);
    };
    sendBlockDefs(worker);
    workers.push(worker);
  }
  return workers;
}

// faceBuf must be a copy dedicated to this call — its ArrayBuffer is transferred (detached from
// the caller) for zero-copy handoff to the worker.
export function requestChunkMesh(key: string, faceBuf: Int32Array, faceCount: number): void {
  const pool = ensurePool();
  const worker = pool[nextWorker];
  nextWorker = (nextWorker + 1) % pool.length;
  worker.postMessage({ type: "mesh", reqId: key, key, faceBuf, faceCount }, [faceBuf.buffer]);
}

export function isChunkMeshReady(key: string): boolean {
  return resultsByKey.has(key);
}

// Consumes (removes) the stored result — call at most once per requestChunkMesh().
export function consumeChunkMeshResult(key: string): { groups: WorkerFaceGroup[]; gltf: WorkerGltfEntry[] } | null {
  const msg = resultsByKey.get(key);
  if (!msg) return null;
  resultsByKey.delete(key);
  return { groups: msg.groups, gltf: msg.gltf };
}

// Drops any not-yet-consumed result for a chunk that was unloaded/re-enqueued before its worker
// job's result was picked up — otherwise a stale result would be applied on the next mesh of the
// same chunk key. Mirrors ChunkManager.enqueueChunk's abort-in-progress-render handling.
export function discardChunkMeshResult(key: string): void {
  resultsByKey.delete(key);
}
