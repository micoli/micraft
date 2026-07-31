import type { Engine } from "@babylonjs/core";
import { getRegistryBlockCount } from "../blocks/blockDefs";
import { setupBlockScene } from "./blockSceneRenderer";

const PREVIEW_SIZE = 64;

const _cache = new Map<number, string>();
const _listeners = new Set<() => void>();
let _canvas: HTMLCanvasElement | null = null;
let _engine: Engine | null = null;
let _preloading = false;

function notify() {
  _listeners.forEach((fn) => fn());
}

export function getCached(ordinal: number): string | null {
  return _cache.get(ordinal) ?? null;
}

export function subscribe(fn: () => void): () => void {
  _listeners.add(fn);
  return () => _listeners.delete(fn);
}

function ensureEngine(): { canvas: HTMLCanvasElement; engine: Engine } {
  if (!_canvas) {
    const B = window.BABYLON!;
    _canvas = document.createElement("canvas");
    _canvas.width = PREVIEW_SIZE;
    _canvas.height = PREVIEW_SIZE;
    Object.assign(_canvas.style, {
      position: "fixed",
      top: "-9999px",
      left: "-9999px",
      pointerEvents: "none",
    });
    document.body.appendChild(_canvas);
    _engine = new B.Engine(_canvas, true, { preserveDrawingBuffer: true, antialias: false, premultipliedAlpha: false });
  }
  return { canvas: _canvas!, engine: _engine! };
}

function renderBlockToDataUrl(ordinal: number): Promise<string | null> {
  const B = window.BABYLON;
  if (!B) return Promise.resolve(null);

  const blockDef = window.mc?.getBlockDef?.(ordinal) as McBlockDef | null;
  if (!blockDef?.elements?.length) return Promise.resolve(null);

  const { engine } = ensureEngine();

  const scene = new B.Scene(engine);
  scene.clearColor = new B.Color4(0, 0, 0, 0);

  setupBlockScene(scene, ordinal);

  return new Promise<string | null>((resolve) => {
    let stopped = false;
    let readyFrames = 0;

    const done = () => {
      if (stopped) return;
      stopped = true;
      engine.stopRenderLoop(loopFn);
      const dataUrl = _canvas!.toDataURL("image/png");
      scene.dispose();
      resolve(dataUrl);
    };

    const timeout = setTimeout(done, 3000);

    const loopFn = () => {
      scene.render();
      if (scene.isReady()) {
        readyFrames++;
        if (readyFrames >= 2) {
          clearTimeout(timeout);
          done();
        }
      }
    };

    engine.runRenderLoop(loopFn);
  });
}

export async function startPreloading(): Promise<void> {
  if (_preloading) return;
  _preloading = true;

  await new Promise<void>((resolve) => {
    const check = () => {
      if (window.mc?.isBlockDefsReady?.() && window.BABYLON) {
        resolve();
      } else {
        setTimeout(check, 200);
      }
    };
    check();
  });

  const count = getRegistryBlockCount();
  for (let ordinal = 0; ordinal < count; ordinal++) {
    if (_cache.has(ordinal)) continue;
    try {
      const url = await renderBlockToDataUrl(ordinal);
      if (url) {
        _cache.set(ordinal, url);
        notify();
      }
    } catch (e) {
      console.warn("blockPreviewCache: failed ordinal", ordinal, e);
    }
  }
}
