import type { Texture } from "@babylonjs/core";
import { useEffect, useRef, useState } from "react";
import { getFaceTexUrl } from "../../lib/blockDefs";
import type { VehicleEntry } from "./CodexModal";

function useVehicleModelsReady(): boolean {
  const [ready, setReady] = useState(() => !!window.mc.isVehicleModelsReady?.());
  useEffect(() => {
    if (ready) return;
    const iv = setInterval(() => {
      if (window.mc.isVehicleModelsReady?.()) {
        setReady(true);
        clearInterval(iv);
      }
    }, 150);
    return () => clearInterval(iv);
  }, [ready]);
  return ready;
}

// Fork of Npc3DPreview.tsx for vehicles: same rotating-turntable-on-a-grass-slab preview, minus
// the walk/static toggle (a rail vehicle has no walk animation).
export function Vehicle3DPreview({ vehicle }: { vehicle: VehicleEntry }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const ready = useVehicleModelsReady();

  useEffect(() => {
    if (!ready) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const B = window.BABYLON;
    if (!B) return;

    const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
    const scene = new B.Scene(engine);
    scene.clearColor = new B.Color4(0.08, 0.08, 0.08, 0);

    const midY = vehicle.height / 2;
    const initialRadius = Math.max(2.5, vehicle.height * 2.0);
    const camera = new B.ArcRotateCamera(
      "cam",
      -Math.PI * 0.3,
      Math.PI / 3,
      initialRadius,
      new B.Vector3(0, midY, 0),
      scene,
    );

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      camera.radius = Math.max(1.0, Math.min(12, camera.radius + e.deltaY * 0.005));
    };
    canvas.addEventListener("wheel", onWheel, { passive: false });

    const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
    light.intensity = 1.1;
    light.groundColor = new B.Color3(0.2, 0.2, 0.2);

    const model: McPlayerModel | null = window.mc.createVehicleModel?.(scene, vehicle.type) ?? null;

    const pivot = new B.TransformNode("pivot", scene);
    if (model) model.root.parent = pivot;

    const grassOrdinal = (window.mcState?.codexBlocks ?? []).findIndex((b) => b.name === "GRASS");
    if (grassOrdinal >= 0) {
      const topUrl = getFaceTexUrl(grassOrdinal, 4);
      const sideUrl = getFaceTexUrl(grassOrdinal, 0) ?? getFaceTexUrl(grassOrdinal, 2);

      const ground = B.MeshBuilder.CreateGround("floor", { width: 5, height: 5 }, scene);
      ground.parent = pivot;
      ground.position.y = 0.002;
      const topMat = new B.StandardMaterial("floorTop", scene);
      if (topUrl) {
        topMat.diffuseTexture = new B.Texture(topUrl, scene, false, true, B.Texture.NEAREST_SAMPLINGMODE);
        (topMat.diffuseTexture as Texture).uScale = 5;
        (topMat.diffuseTexture as Texture).vScale = 5;
      } else {
        topMat.diffuseColor = new B.Color3(0.3, 0.6, 0.2);
      }
      topMat.specularColor = new B.Color3(0, 0, 0);
      ground.material = topMat;

      const slab = B.MeshBuilder.CreateBox("slab", { width: 5, height: 0.5, depth: 5 }, scene);
      slab.parent = pivot;
      slab.position.y = -0.25;
      const sideMat = new B.StandardMaterial("floorSide", scene);
      if (sideUrl) {
        sideMat.diffuseTexture = new B.Texture(sideUrl, scene, false, true, B.Texture.NEAREST_SAMPLINGMODE);
      } else {
        sideMat.diffuseColor = new B.Color3(0.45, 0.3, 0.15);
      }
      sideMat.specularColor = new B.Color3(0, 0, 0);
      slab.material = sideMat;
    }

    let angle = 0;
    scene.onBeforeRenderObservable.add(() => {
      angle += 0.015;
      pivot.rotation.y = angle;
      if (model) window.mc.setVehicleTransform?.(model, 0, 0, 0, 0);
    });

    engine.runRenderLoop(() => scene.render());

    return () => {
      canvas.removeEventListener("wheel", onWheel);
      if (model) window.mc.disposeVehicleModel?.(model);
      engine.dispose();
    };
  }, [ready, vehicle.type, vehicle.height]);

  return (
    <canvas
      ref={canvasRef}
      width={160}
      height={200}
      style={{ display: "block", width: 160, height: 200, borderRadius: 6, background: "#111" }}
    />
  );
}
