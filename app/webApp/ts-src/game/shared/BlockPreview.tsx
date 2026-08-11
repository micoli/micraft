import { useEffect, useReducer, useRef, useState } from "react";
import { getFaceTexUrl, getBlockBounds } from "../blocks/blockDefs";
import { subscribe, getCached, ensureColoredPreview, getColoredCached, ensurePreview } from "./blockPreviewCache";
import { setupBlockScene } from "./blockSceneRenderer";
import { suppressDeprecatedWebglWarnings } from "./webglPatches";

suppressDeprecatedWebglWarnings();

export function useBlockPreviews(): (ordinal: number) => string | null {
  const [, inc] = useReducer((n: number) => n + 1, 0);
  useEffect(() => subscribe(inc), []);
  return getCached;
}

export function useColoredBlockPreview(ordinal: number | null, colorHex: string | null): string | null {
  const [, inc] = useReducer((n: number) => n + 1, 0);
  useEffect(() => subscribe(inc), []);
  useEffect(() => {
    if (ordinal != null && colorHex != null) ensureColoredPreview(ordinal, colorHex);
  }, [ordinal, colorHex]);
  if (ordinal == null || colorHex == null) return null;
  return getColoredCached(ordinal, colorHex);
}

export function useBlockDefsReady(): boolean {
  const [ready, setReady] = useState(() => !!window.mc?.isBlockDefsReady?.());
  useEffect(() => {
    if (ready) return;
    const iv = setInterval(() => {
      if (window.mc?.isBlockDefsReady?.()) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
  }, [ready]);
  return ready;
}

export function CssBlockCube({
  ordinal,
  size,
  colorHex,
}: {
  ordinal: number;
  size: number;
  /** "RRGGBB" (no '#'): renders plain color instead of the block texture. */
  colorHex?: string | null;
}) {
  // CssBlockCube is a crude 3-face approximation — actively misleading for multi-element models
  // (e.g. studded LEGO plates). Jump the real preview to the front of the render queue instead of
  // waiting for the full-registry sweep (startPreloading) to reach this ordinal.
  useEffect(() => {
    if (!colorHex) ensurePreview(ordinal);
  }, [ordinal, colorHex]);

  const S = size;
  const { w, h, d } = getBlockBounds(ordinal);
  // Face dimensions in px, proportionate to the block's actual model bounds (not always a full cube).
  const wPx = S * w,
    hPx = S * h,
    dPx = S * d;

  const topUrl = colorHex ? null : getFaceTexUrl(ordinal, 4);
  const frontUrl = colorHex ? null : getFaceTexUrl(ordinal, 0);
  const rightUrl = colorHex ? null : getFaceTexUrl(ordinal, 2);

  const face = (
    faceW: number,
    faceH: number,
    brightness: number,
    transform: string,
    texUrl: string | null,
  ): React.CSSProperties => ({
    position: "absolute",
    width: faceW,
    height: faceH,
    backgroundImage: texUrl ? `url(${texUrl})` : undefined,
    backgroundColor: texUrl ? undefined : colorHex ? `#${colorHex}` : "#666",
    backgroundSize: "100% 100%",
    imageRendering: "pixelated",
    transform,
    filter: `brightness(${brightness})`,
  });

  return (
    <div
      style={{
        width: S * 1.8,
        height: S * 1.8,
        perspective: S * 5,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
      }}
    >
      <div
        style={{
          position: "relative",
          width: wPx,
          height: hPx,
          transformStyle: "preserve-3d",
          transform: "rotateX(-25deg) rotateY(45deg)",
        }}
      >
        <div style={face(wPx, dPx, 1.05, `rotateX(90deg) translateZ(${hPx / 2}px)`, topUrl)} />
        <div style={face(wPx, hPx, 0.78, `translateZ(${dPx / 2}px)`, frontUrl)} />
        <div style={face(dPx, hPx, 0.62, `rotateY(90deg) translateZ(${wPx / 2}px)`, rightUrl)} />
      </div>
    </div>
  );
}

export function Block3DPreview({
  ordinal,
  size = 160,
  animate = true,
  colorHex,
}: {
  ordinal: number;
  size?: number;
  animate?: boolean;
  /** "RRGGBB" (no '#'): renders plain color instead of the block texture. */
  colorHex?: string | null;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, {
      preserveDrawingBuffer: true,
      antialias: true,
      premultipliedAlpha: false,
    });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.08, 0.08, 0.08, 0);

    const root = setupBlockScene(scene, ordinal, colorHex);

    if (animate) {
      scene.onBeforeRenderObservable.add(() => {
        root.rotation.y += 0.018;
      });
      engine.runRenderLoop(() => scene.render());
    } else {
      scene.executeWhenReady(() => {
        scene.render();
      });
    }

    return () => engine.dispose();
  }, [ordinal, animate, colorHex]);

  return (
    <canvas
      ref={canvasRef}
      width={size}
      height={size}
      style={{ display: "block", width: size, height: size, borderRadius: animate ? 6 : 0 }}
    />
  );
}
