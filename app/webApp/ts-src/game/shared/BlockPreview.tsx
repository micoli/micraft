import { useEffect, useReducer, useRef, useState } from "react";
import { getFaceTexUrl } from "../blocks/blockDefs";
import { subscribe, getCached } from "./blockPreviewCache";
import { setupBlockScene } from "./blockSceneRenderer";

export function useBlockPreviews(): (ordinal: number) => string | null {
  const [, inc] = useReducer((n: number) => n + 1, 0);
  useEffect(() => subscribe(inc), []);
  return getCached;
}

export function useBlockDefsReady(): boolean {
  const [ready, setReady] = useState(() => !!window.mc.isBlockDefsReady?.());
  useEffect(() => {
    if (ready) return;
    const iv = setInterval(() => {
      if (window.mc.isBlockDefsReady?.()) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
  }, [ready]);
  return ready;
}

export function CssBlockCube({ ordinal, size }: { ordinal: number; size: number }) {
  const S = size;
  const H = Math.round(S / 2);

  const topUrl = getFaceTexUrl(ordinal, 4);
  const frontUrl = getFaceTexUrl(ordinal, 0);
  const rightUrl = getFaceTexUrl(ordinal, 2);

  const face = (brightness: number, transform: string, texUrl: string | null): React.CSSProperties => ({
    position: "absolute",
    width: S,
    height: S,
    backgroundImage: texUrl ? `url(${texUrl})` : undefined,
    backgroundColor: texUrl ? undefined : "#666",
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
          width: S,
          height: S,
          transformStyle: "preserve-3d",
          transform: "rotateX(-25deg) rotateY(45deg)",
        }}
      >
        <div style={face(1.05, `rotateX(90deg) translateZ(${H}px)`, topUrl)} />
        <div style={face(0.78, `translateZ(${H}px)`, frontUrl)} />
        <div style={face(0.62, `rotateY(90deg) translateZ(${H}px)`, rightUrl)} />
      </div>
    </div>
  );
}

export function Block3DPreview({
  ordinal,
  size = 160,
  animate = true,
}: {
  ordinal: number;
  size?: number;
  animate?: boolean;
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

    const root = setupBlockScene(scene, ordinal);

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
  }, [ordinal, animate]);

  return (
    <canvas
      ref={canvasRef}
      width={size}
      height={size}
      style={{ display: "block", width: size, height: size, borderRadius: animate ? 6 : 0 }}
    />
  );
}
