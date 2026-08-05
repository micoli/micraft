import type { Engine } from "@babylonjs/core";
import { getRegistryBlockCount, isPlainColorable } from "../blocks/blockDefs";
import { setupBlockScene } from "./blockSceneRenderer";

/** Neutral grey used as stand-in for plainColorable blocks in cached previews. */
export const PLAIN_COLORABLE_PREVIEW_HEX = "A0A0A0";

const PREVIEW_SIZE = 64;

const _cache = new Map<number, string>();
const _coloredCache = new Map<string, string>();
const _coloredPending = new Set<string>();
const _listeners = new Set<() => void>();
let _canvas: HTMLCanvasElement | null = null;
let _engine: Engine | null = null;
let _preloading = false;

// Serialized render queue — prevents concurrent renders on the shared canvas.
type RenderTask = () => Promise<void>;
const _renderQueue: RenderTask[] = [];
let _rendering = false;

function notify() {
  _listeners.forEach((fn) => fn());
}

async function drainQueue() {
  if (_rendering) return;
  _rendering = true;
  while (_renderQueue.length > 0) {
    const task = _renderQueue.shift()!;
    try {
      await task();
    } catch (e) {
      console.warn("blockPreviewCache: render task failed", e);
    }
  }
  _rendering = false;
}

function enqueue(task: RenderTask) {
  _renderQueue.push(task);
  drainQueue();
}

export function getCached(ordinal: number): string | null {
  return _cache.get(ordinal) ?? null;
}

export function getColoredCached(ordinal: number, colorHex: string): string | null {
  return _coloredCache.get(`${ordinal}:${colorHex}`) ?? null;
}

export function ensureColoredPreview(ordinal: number, colorHex: string): void {
  const key = `${ordinal}:${colorHex}`;
  if (_coloredCache.has(key) || _coloredPending.has(key)) return;
  _coloredPending.add(key);
  enqueue(async () => {
    const url = await renderBlockToDataUrl(ordinal, colorHex);
    _coloredPending.delete(key);
    if (url) {
      _coloredCache.set(key, url);
      notify();
    }
  });
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

function renderBlockToDataUrl(ordinal: number, colorHex?: string): Promise<string | null> {
  const B = window.BABYLON;
  if (!B) return Promise.resolve(null);

  const blockDef = window.mc?.getBlockDef?.(ordinal) as McBlockDef | null;
  if (!blockDef?.elements?.length) return Promise.resolve(null);

  const { engine } = ensureEngine();

  const scene = new B.Scene(engine);
  scene.clearColor = new B.Color4(0, 0, 0, 0);

  setupBlockScene(scene, ordinal, colorHex);

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
  const tasks: Promise<void>[] = [];
  for (let ordinal = 0; ordinal < count; ordinal++) {
    if (_cache.has(ordinal)) continue;
    const ord = ordinal;
    const colorHex = isPlainColorable(ord) ? PLAIN_COLORABLE_PREVIEW_HEX : undefined;
    const p = new Promise<void>((resolve) => {
      enqueue(async () => {
        try {
          const url = await renderBlockToDataUrl(ord, colorHex);
          if (url) {
            _cache.set(ord, url);
            notify();
          }
        } catch (e) {
          console.warn("blockPreviewCache: failed ordinal", ord, e);
        }
        resolve();
      });
    });
    tasks.push(p);
  }
  await Promise.all(tasks);
}
