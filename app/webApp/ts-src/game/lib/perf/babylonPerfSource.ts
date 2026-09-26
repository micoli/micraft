import type { AbstractEngine, EngineInstrumentation, Scene, SceneInstrumentation } from "@babylonjs/core";
import type { FrameCounters, MemoryReading, PerfSource } from "./perfCollector";

interface ChromePerformanceMemory {
  usedJSHeapSize: number;
}

let wasmMemory: WebAssembly.Memory | null = null;

/**
 * Wraps `WebAssembly.instantiate*` so the Kotlin/Wasm module's linear memory can be read later.
 * Must run before `webApp.js` loads its module.
 */
export function captureWasmMemory(): void {
  // Kotlin/Wasm imports its memory (`intrinsics.memory`) rather than exporting it.
  const keepImported = (imports: WebAssembly.Imports | undefined) => {
    for (const namespace of Object.values(imports ?? {})) {
      const memory = Object.values(namespace).find((v) => v instanceof WebAssembly.Memory);
      if (memory) wasmMemory = memory as WebAssembly.Memory;
    }
  };
  const keepExported = (result: WebAssembly.WebAssemblyInstantiatedSource | WebAssembly.Instance) => {
    const instance = "instance" in result ? result.instance : result;
    const memory = Object.values(instance.exports).find((e) => e instanceof WebAssembly.Memory);
    if (memory) wasmMemory = memory as WebAssembly.Memory;
    return result;
  };
  const instantiate = WebAssembly.instantiate.bind(WebAssembly);
  const instantiateStreaming = WebAssembly.instantiateStreaming?.bind(WebAssembly);
  WebAssembly.instantiate = ((...args: Parameters<typeof WebAssembly.instantiate>) => {
    keepImported(args[1]);
    return instantiate(...args).then(keepExported);
  }) as typeof WebAssembly.instantiate;
  if (instantiateStreaming) {
    WebAssembly.instantiateStreaming = ((...args: Parameters<typeof WebAssembly.instantiateStreaming>) => {
      keepImported(args[1]);
      return instantiateStreaming(...args).then(keepExported);
    }) as typeof WebAssembly.instantiateStreaming;
  }
}

/** Bytes allocated on the GPU for mesh vertex and index buffers (terrain keeps no CPU copy to measure). */
function gpuBufferBytes(scene: Scene): number {
  const seen = new Set<unknown>();
  let bytes = 0;
  const add = (buffer: { capacity: number } | null | undefined) => {
    if (!buffer || seen.has(buffer)) return;
    seen.add(buffer);
    bytes += buffer.capacity;
  };
  for (const mesh of scene.meshes) {
    const geometry = (mesh as { geometry?: import("@babylonjs/core").Geometry | null }).geometry;
    if (!geometry) continue;
    for (const kind of geometry.getVerticesDataKinds()) add(geometry.getVertexBuffer(kind)?.getBuffer());
    // Index buffers carry no capacity; their size follows from the index count and width.
    const indexBuffer = geometry.getIndexBuffer();
    if (indexBuffer && !seen.has(indexBuffer)) {
      seen.add(indexBuffer);
      bytes += geometry.getTotalIndices() * (indexBuffer.is32Bits ? 4 : 2);
    }
  }
  return bytes;
}

/** Reads the live engine's first scene; instruments it lazily once the game has created it. */
export function babylonPerfSource(engine: AbstractEngine): PerfSource {
  let scene: Scene | null = null;
  let sceneInstrumentation: SceneInstrumentation | null = null;
  const engineInstrumentation: EngineInstrumentation = new BABYLON.EngineInstrumentation(engine);
  engineInstrumentation.captureGPUFrameTime = true;
  const gpuTimingSupported = !!engine.getCaps().timerQuery;

  // The game's render loop skips some animation frames (it caps its own rate), so a frame only
  // counts when the scene actually rendered during it.
  let renderedThisFrame = false;

  const currentScene = (): Scene | null => {
    if (scene || engine.scenes.length === 0) return scene;
    scene = engine.scenes[0];
    sceneInstrumentation = new BABYLON.SceneInstrumentation(scene);
    scene.onAfterRenderObservable.add(() => {
      renderedThisFrame = true;
    });
    return scene;
  };

  return {
    now: () => performance.now(),
    onFrame(cb) {
      const observer = engine.onEndFrameObservable.add(() => {
        currentScene();
        if (!renderedThisFrame) return;
        renderedThisFrame = false;
        cb();
      });
      return () => engine.onEndFrameObservable.remove(observer);
    },
    counters(): FrameCounters {
      const s = currentScene();
      // GPU timer queries resolve a few frames late; frames without a result read 0.
      const gpuNanos = gpuTimingSupported ? engineInstrumentation.gpuFrameTimeCounter.current : 0;
      return {
        drawCalls: sceneInstrumentation?.drawCallsCounter.current ?? 0,
        activeMeshes: s?.getActiveMeshes().length ?? 0,
        activeIndices: s?.getActiveIndices() ?? 0,
        textures: s?.textures.length ?? 0,
        gpuFrameMs: gpuNanos > 0 ? gpuNanos / 1_000_000 : null,
      };
    },
    memory(): MemoryReading {
      const s = currentScene();
      const heap = (performance as Performance & { memory?: ChromePerformanceMemory }).memory;
      return {
        jsHeapUsedBytes: heap?.usedJSHeapSize ?? null,
        wasmMemoryBytes: wasmMemory?.buffer.byteLength ?? null,
        gpuBufferBytes: s ? gpuBufferBytes(s) : null,
      };
    },
  };
}
