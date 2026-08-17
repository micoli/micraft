import { useEffect, useLayoutEffect, useRef, useState } from "react";

function usePlayerModelReady(skin: string): boolean {
  const [ready, setReady] = useState(() => !!window.mc.isPlayerBbmodelReady?.(skin));
  useEffect(() => {
    setReady(!!window.mc.isPlayerBbmodelReady?.(skin));
    window.mc.initPlayerModel?.(skin);
    if (window.mc.isPlayerBbmodelReady?.(skin)) return;
    const iv = setInterval(() => {
      if (window.mc.isPlayerBbmodelReady?.(skin)) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
  }, [skin]);
  return ready;
}

export function SkinModelPreview({ skin, walking }: { skin: string; walking: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const ready = usePlayerModelReady(skin);
  const walkingRef = useRef(walking);
  useLayoutEffect(() => {
    walkingRef.current = walking;
  });

  useEffect(() => {
    if (!ready) return;
    if (!window.mc.isPlayerBbmodelReady?.(skin)) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.08, 0.08, 0.08, 0);

    new B.ArcRotateCamera("cam", -Math.PI * 0.25, Math.PI / 3.2, 3.0, new B.Vector3(0, 0.9, 0), scene);
    const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
    light.intensity = 1.1;
    light.groundColor = new B.Color3(0.2, 0.2, 0.2);

    const model = window.mc.createPlayerModelNow?.(scene, skin) ?? null;

    let angle = 0;
    scene.onBeforeRenderObservable.add(() => {
      angle += 0.015;
      if (model) window.mc.setPlayerTransform?.(model, 0, 0, 0, angle, 0, walkingRef.current);
    });

    engine.runRenderLoop(() => scene.render());
    return () => {
      if (model) window.mc.disposePlayerModel?.(model);
      engine.dispose();
    };
  }, [ready, skin]);

  return (
    <canvas
      ref={canvasRef}
      width={160}
      height={220}
      style={{ display: "block", width: 160, height: 220, borderRadius: 6, background: "#111" }}
    />
  );
}
