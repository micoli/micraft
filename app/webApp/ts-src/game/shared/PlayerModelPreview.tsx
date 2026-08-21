import { useState, useEffect, useLayoutEffect, useRef } from "react";

export function usePlayerModelReady(skin: string): boolean {
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

export function useArmorModelsReady(armors: string[]): boolean {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    if (armors.length === 0) {
      setReady(true);
      return;
    }
    armors.forEach((a) => window.mc.initArmorModel?.(a));
    const check = () => armors.every((a) => window.mc.isArmorModelReady?.(a));
    if (check()) {
      setReady(true);
      return;
    }
    const iv = setInterval(() => {
      if (check()) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- armors.join(",") is intentional: stable string key for array identity
  }, [armors.join(",")]);
  return ready;
}

export function useWeaponModelsReady(items: (string | null | undefined)[]): boolean {
  const names = items.filter((n): n is string => !!n);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    if (names.length === 0) {
      setReady(true);
      return;
    }
    names.forEach((n) => window.mc.initWeaponModel?.(n));
    const check = () => names.every((n) => window.mc.isWeaponModelReady?.(n));
    if (check()) {
      setReady(true);
      return;
    }
    const iv = setInterval(() => {
      if (check()) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- names.join(",") is intentional: stable string key for array identity
  }, [names.join(",")]);
  return ready;
}

export function PlayerModelPreview({
  skin,
  armors,
  rightHandItem,
  leftHandItem,
  walking,
  width = 160,
  height = 220,
}: {
  skin: string;
  armors: string[];
  rightHandItem?: string | null;
  leftHandItem?: string | null;
  walking: boolean;
  width?: number;
  height?: number;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const skinReady = usePlayerModelReady(skin);
  const armorsReady = useArmorModelsReady(armors);
  const weaponsReady = useWeaponModelsReady([rightHandItem, leftHandItem]);
  const ready = skinReady && armorsReady && weaponsReady;
  const rightHandItemRef = useRef(rightHandItem);
  const leftHandItemRef = useRef(leftHandItem);
  useLayoutEffect(() => {
    rightHandItemRef.current = rightHandItem;
    leftHandItemRef.current = leftHandItem;
  });
  const walkingRef = useRef(walking);
  useLayoutEffect(() => {
    walkingRef.current = walking;
  });

  useEffect(() => {
    if (!ready) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0, 0, 0, 0);

    new B.ArcRotateCamera("cam", -Math.PI * 0.25, Math.PI / 3.2, 3.0, new B.Vector3(0, 0.9, 0), scene);

    const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
    light.intensity = 1.1;
    light.groundColor = new B.Color3(0.2, 0.2, 0.2);

    const model = window.mc.createPlayerModelNow?.(scene, skin) ?? null;
    if (model) {
      armors.forEach((a) => window.mc.attachArmor?.(model, a, scene));
      if (rightHandItemRef.current) window.mc.attachWeapon?.(model, rightHandItemRef.current, scene, "RIGHT");
      if (leftHandItemRef.current) window.mc.attachWeapon?.(model, leftHandItemRef.current, scene, "LEFT");
    }

    let angle = 0;
    scene.onBeforeRenderObservable.add(() => {
      angle += 0.015;
      if (model)
        window.mc.setPlayerTransform?.(model, 0, 0, 0, angle, 0, walkingRef.current ? "walking_forward" : "idle");
    });

    engine.runRenderLoop(() => scene.render());

    return () => {
      if (model) window.mc.disposePlayerModel?.(model);
      engine.dispose();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-only when ready; skin/armors change via component key
  }, [ready]);

  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={height}
      style={{ display: "block", width, height, borderRadius: 6, background: "#111" }}
    />
  );
}
