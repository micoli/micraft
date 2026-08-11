import { useEffect, useRef } from "react";
import { setupBlockScene } from "./blockSceneRenderer";

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
