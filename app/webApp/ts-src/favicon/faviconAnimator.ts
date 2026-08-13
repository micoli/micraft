import type { StandardMaterial } from "@babylonjs/core";
import { getFaceTexUrl } from "../game/lib/blockDefs";

const FACES = [
  { dir: 0, x: 0, y: 0, z: 0.5, rx: 0, ry: Math.PI },
  { dir: 1, x: 0, y: 0, z: -0.5, rx: 0, ry: 0 },
  { dir: 2, x: 0.5, y: 0, z: 0, rx: 0, ry: -Math.PI / 2 },
  { dir: 3, x: -0.5, y: 0, z: 0, rx: 0, ry: Math.PI / 2 },
  { dir: 4, x: 0, y: 0.5, z: 0, rx: Math.PI / 2, ry: 0 },
  { dir: 5, x: 0, y: -0.5, z: 0, rx: -Math.PI / 2, ry: 0 },
];

const FRAME_INTERVAL_MS = 1000;

// Fetches a block texture image, not a JSON API route — kept as a manual fetch.
async function toObjectUrl(url: string): Promise<string> {
  const res = await fetch(url);
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

let _link: HTMLLinkElement | null = null;
let _ordinal = -1;
let _staticUrl: string | null = null;
const _frameCache: string[] = [];
let _intervalId: ReturnType<typeof setInterval> | null = null;
let _animating = false;
let _buildStarted = false;

function _startAnimation(): void {
  if (_intervalId !== null || _frameCache.length === 0) return;
  let idx = 0;
  _intervalId = setInterval(() => {
    if (_link) {
      _link.href = _frameCache[idx % _frameCache.length];
      idx++;
    }
  }, FRAME_INTERVAL_MS);
}

function _stopAnimation(): void {
  if (_intervalId !== null) {
    clearInterval(_intervalId);
    _intervalId = null;
  }
  if (_link) _link.href = _frameCache.length > 0 ? _frameCache[0] : (_staticUrl ?? "");
}

function _buildFrameCache(): void {
  const B = window.BABYLON;
  if (!B || _ordinal < 0) return;

  const fallback = _staticUrl;
  const rawUrls = new Set<string>();
  for (const { dir } of FACES) {
    const url = getFaceTexUrl(_ordinal, dir) ?? fallback;
    if (url) rawUrls.add(url);
  }

  Promise.all([...rawUrls].map(async (url) => [url, await toObjectUrl(url)] as [string, string])).then((entries) => {
    const blobMap = new Map(entries);
    const textureBlobUrls = entries.map(([, b]) => b);

    const glCanvas = document.createElement("canvas");
    glCanvas.width = 64;
    glCanvas.height = 64;

    const engine = new B.Engine(glCanvas, true, { preserveDrawingBuffer: true, antialias: false });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0, 0, 0, 0);

    new B.ArcRotateCamera("favcam", -Math.PI * 0.25, Math.PI / 3.5, 2.5, B.Vector3.Zero(), scene);
    const light = new B.HemisphericLight("favlight", new B.Vector3(1, 2, 1), scene);
    light.intensity = 1.0;
    light.groundColor = new B.Color3(0.25, 0.25, 0.25);

    const root = new B.TransformNode("favroot", scene);
    const matCache = new Map<string, StandardMaterial>();

    for (const { dir, x, y, z, rx, ry } of FACES) {
      const rawUrl = (getFaceTexUrl(_ordinal, dir) ?? fallback)!;
      if (!rawUrl) continue;
      const blobUrl = blobMap.get(rawUrl)!;

      if (!matCache.has(blobUrl)) {
        const mat = new B.StandardMaterial("fm_" + dir, scene);
        mat.diffuseTexture = new B.Texture(blobUrl, scene, false, true, B.Texture.NEAREST_SAMPLINGMODE);
        mat.specularColor = new B.Color3(0, 0, 0);
        mat.backFaceCulling = true;
        matCache.set(blobUrl, mat);
      }

      const plane = B.MeshBuilder.CreatePlane("ff" + dir, { size: 1 }, scene);
      plane.parent = root;
      plane.position = new B.Vector3(x, y, z);
      plane.rotation = new B.Vector3(rx, ry, 0);
      plane.material = matCache.get(blobUrl) ?? null;
    }

    const ANGLE_STEP = 0.035;
    const FULL_ROTATION = Math.PI * 2;
    let angle = 0;
    let frame = 0;
    let cacheComplete = false;
    let pendingCapture = false;

    scene.onBeforeRenderObservable.add(() => {
      if (cacheComplete) return;
      angle += ANGLE_STEP;
      root.rotation.y = angle;
      if (++frame % 6 === 0) pendingCapture = true;
    });

    scene.onAfterRenderObservable.add(() => {
      if (!pendingCapture || cacheComplete) return;
      pendingCapture = false;
      _frameCache.push(glCanvas.toDataURL("image/png"));
      if (_link && _animating) _link.href = _frameCache[_frameCache.length - 1];
      if (angle >= FULL_ROTATION) {
        cacheComplete = true;
        engine.stopRenderLoop();
        setTimeout(() => {
          scene.dispose();
          engine.dispose();
          textureBlobUrls.forEach((b) => URL.revokeObjectURL(b));
        }, 0);
        if (_animating) _startAnimation();
        else if (_link) _link.href = _frameCache[0];
      }
    });

    engine.runRenderLoop(() => scene.render());
  });
}

export function setFaviconAnimated(animated: boolean): void {
  _animating = animated;
  if (animated) {
    if (_frameCache.length > 0) {
      _startAnimation();
    } else if (!_buildStarted) {
      _buildStarted = true;
      _buildFrameCache();
    }
  } else {
    _stopAnimation();
  }
}

export function initFaviconAnimator(ordinal: number, animated: boolean): void {
  _link = document.getElementById("favicon") as HTMLLinkElement | null;
  if (!_link) return;
  _ordinal = ordinal;
  _staticUrl = getFaceTexUrl(ordinal, 4) ?? getFaceTexUrl(ordinal, 0) ?? null;
  _animating = animated;
  _buildStarted = true;
  _buildFrameCache();
}
