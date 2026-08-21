/* eslint-disable @typescript-eslint/no-explicit-any */
import type { CSSProperties } from "react";
import { useEffect, useLayoutEffect, useRef } from "react";
import { interpAxis } from "../../game/lib/player/playerModel";
import { buildMeshElement, isMeshElement, resolveTextureDims } from "../../game/lib/player/bbmodelMesh";

function loadScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    if (document.querySelector(`script[src="${src}"]`)) {
      resolve();
      return;
    }
    const s = document.createElement("script");
    s.src = src;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error(`Failed: ${src}`));
    document.head.appendChild(s);
  });
}

async function ensureBabylon(): Promise<void> {
  if (!(window as any).BABYLON) await loadScript("/babylon.js");
}

function skinUV(B: any, face: any, W: number, H: number): any {
  if (!face?.uv) return new B.Vector4(0, 0, 0, 0);
  const [x0, y0, x1, y1] = face.uv;
  return new B.Vector4(Math.min(x0, x1) / W, 1 - Math.max(y0, y1) / H, Math.max(x0, x1) / W, 1 - Math.min(y0, y1) / H);
}

function skinFaceUV(B: any, el: any, W: number, H: number): any[] {
  const faces = el.faces;
  if (el.box_uv && el.uv_offset) {
    const bw = Math.round(Math.abs(el.to[0] - el.from[0]));
    const bh = Math.round(Math.abs(el.to[1] - el.from[1]));
    const bd = Math.round(Math.abs(el.to[2] - el.from[2]));
    const [u, v] = el.uv_offset;
    const fake = (x0: number, y0: number, x1: number, y1: number) => ({ uv: [x0, y0, x1, y1] });
    return [
      skinUV(B, fake(u + 2 * bd + bw, v + bd, u + 2 * bd + 2 * bw, v + bd + bh), W, H),
      skinUV(B, fake(u + bd, v + bd, u + bd + bw, v + bd + bh), W, H),
      skinUV(B, fake(u, v + bd, u + bd, v + bd + bh), W, H),
      skinUV(B, fake(u + bd + bw, v + bd, u + 2 * bd + bw, v + bd + bh), W, H),
      skinUV(B, fake(u + bd, v, u + bd + bw, v + bd), W, H),
      skinUV(B, fake(u + bd + bw, v, u + bd + 2 * bw, v + bd), W, H),
    ];
  }
  return [
    skinUV(B, faces?.south, W, H),
    skinUV(B, faces?.north, W, H),
    skinUV(B, faces?.east, W, H),
    skinUV(B, faces?.west, W, H),
    skinUV(B, faces?.up, W, H),
    skinUV(B, faces?.down, W, H),
  ];
}

function buildModel(
  B: any,
  bbmodel: BbModel,
  scene: any,
): {
  root: any;
  pivotNodes: Record<string, { node: any; origin: [number, number, number] }>;
  equippedWeapons: { LEFT: any; RIGHT: any };
} {
  const SCALE = 1 / 16;
  const DEG = Math.PI / 180;
  const W = bbmodel.resolution.width;
  const H = bbmodel.resolution.height;

  // One material per texture (rather than the shared/cached `buildTextureMaterials` helper) —
  // this viewer builds a fresh scene per mount, so there's nothing to reuse across calls.
  const materials: any[] = (bbmodel.textures ?? []).map((texDef, i) => {
    const tex = new B.Texture(texDef.source, scene, true, true, B.Texture.NEAREST_SAMPLINGMODE);
    tex.hasAlpha = false;
    tex.wrapU = B.Texture.CLAMP_ADDRESSMODE;
    tex.wrapV = B.Texture.CLAMP_ADDRESSMODE;
    const texMat = new B.StandardMaterial(`skinMat_${i}`, scene);
    texMat.diffuseTexture = tex;
    texMat.specularColor = new B.Color3(0, 0, 0);
    return texMat;
  });
  const mat: any = materials[0] ?? null;
  const textureDims = resolveTextureDims(bbmodel);

  const groupMap: Record<string, any> = {};
  bbmodel.groups.forEach((g: any) => {
    groupMap[g.uuid] = g;
  });

  const elToGroupUuid: Record<string, string | null> = {};
  const groupToParentGroupUuid: Record<string, string | null> = {};
  function walkOutliner(nodes: any[], parentUuid: string | null) {
    for (const node of nodes) {
      if (typeof node === "string") {
        elToGroupUuid[node] = parentUuid;
        continue;
      }
      groupToParentGroupUuid[node.uuid] = parentUuid;
      walkOutliner(node.children ?? [], node.uuid);
    }
  }
  walkOutliner(bbmodel.outliner ?? [], null);

  const root = new B.TransformNode("playerRoot", scene);
  const pivotNodes: Record<string, { node: any; origin: [number, number, number] }> = {};
  const allGroupNodes: Record<string, { node: any; origin: [number, number, number] }> = {};

  bbmodel.groups.forEach((g: any) => {
    const node = new B.TransformNode(`grp_${g.name}`, scene);
    node.parent = root;
    node.position = new B.Vector3(g.origin[0] * SCALE, g.origin[1] * SCALE, g.origin[2] * SCALE);
    if (g.rotation) node.rotation = new B.Vector3(g.rotation[0] * DEG, g.rotation[1] * DEG, g.rotation[2] * DEG);
    allGroupNodes[g.uuid] = { node, origin: g.origin };
    pivotNodes[g.name] = { node, origin: g.origin };
  });

  bbmodel.groups.forEach((g: any) => {
    const parentUuid = groupToParentGroupUuid[g.uuid];
    if (!parentUuid || !allGroupNodes[parentUuid]) return;
    const child = allGroupNodes[g.uuid];
    const parent = allGroupNodes[parentUuid];
    child.node.parent = parent.node;
    child.node.position = new B.Vector3(
      (g.origin[0] - parent.origin[0]) * SCALE,
      (g.origin[1] - parent.origin[1]) * SCALE,
      (g.origin[2] - parent.origin[2]) * SCALE,
    );
  });

  for (const el of bbmodel.elements as unknown as (BbModelElement | BbModelMeshElement)[]) {
    if (el.visibility === false) continue;

    if (isMeshElement(el)) {
      if (Object.keys(el.vertices).length === 0) continue;
      const meshEl = buildMeshElement(el.name, el, scene, [0, 0, 0], materials, textureDims);
      const groupUuid = elToGroupUuid[el.uuid];
      const pg = groupUuid ? allGroupNodes[groupUuid] : null;
      meshEl.parent = pg ? pg.node : root;
      continue;
    }

    const [fx, fy, fz] = el.from,
      [tx, ty, tz] = el.to;
    if (Math.abs(tx - fx) < 0.001 || Math.abs(ty - fy) < 0.001 || Math.abs(tz - fz) < 0.001) continue;
    const mesh = B.MeshBuilder.CreateBox(
      el.name,
      {
        width: Math.abs(tx - fx) * SCALE,
        height: Math.abs(ty - fy) * SCALE,
        depth: Math.abs(tz - fz) * SCALE,
        faceUV: skinFaceUV(B, el, W, H),
      },
      scene,
    );
    if (mat) mesh.material = mat;
    mesh.isPickable = false;
    const cx = ((fx + tx) / 2) * SCALE,
      cy = ((fy + ty) / 2) * SCALE,
      cz = ((fz + tz) / 2) * SCALE;
    const groupUuid = elToGroupUuid[el.uuid];
    const pg = groupUuid ? allGroupNodes[groupUuid] : null;
    if (pg) {
      mesh.parent = pg.node;
      mesh.position = new B.Vector3(cx - pg.origin[0] * SCALE, cy - pg.origin[1] * SCALE, cz - pg.origin[2] * SCALE);
    } else {
      mesh.parent = root;
      mesh.position = new B.Vector3(cx, cy, cz);
    }
  }

  return { root, pivotNodes, equippedWeapons: { LEFT: null, RIGHT: null } };
}

const DEG = Math.PI / 180;
const MIN_RADIUS = 1.0;
const MAX_RADIUS = 8.0;
const WHEEL_ZOOM_STEP = 0.06;
const BUTTON_ZOOM_STEP = 0.3;

type RotateOverride = { x: number; y: number; z: number } | null;

export function BbmodelAnimationViewer({
  bbmodel,
  animFullName,
  paused = false,
  initialZoom,
  initialAngle,
  onCameraChange,
  rightHandItem = null,
  leftHandItem = null,
  rightHandRotate = null,
  leftHandRotate = null,
  width = 200,
  height = 280,
}: {
  bbmodel: BbModel | null;
  animFullName: string;
  paused?: boolean;
  // Camera radius (zoom) and model spin angle (radians) to restore on mount.
  initialZoom?: number;
  initialAngle?: number;
  // Fired after a user interaction changes zoom and/or angle, so the caller can persist it.
  onCameraChange?: (zoom: number, angle: number) => void;
  rightHandItem?: string | null;
  leftHandItem?: string | null;
  // Overrides the equipment's yaml-configured `rotate` for this preview only.
  rightHandRotate?: RotateOverride;
  leftHandRotate?: RotateOverride;
  width?: number;
  height?: number;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const engineRef = useRef<any>(null);
  const cameraRef = useRef<any>(null);
  const angleRef = useRef(initialAngle ?? 0);
  const animRef = useRef(animFullName);
  const pausedRef = useRef(paused);
  // The playhead is real-time (Date.now()) based rather than accumulated per-frame, so pausing
  // needs to freeze a captured time rather than merely skip advancing it.
  const frozenTSecRef = useRef<number | null>(null);
  // Wrapped so the effect below can hold a stable reference while always calling the latest prop.
  const onCameraChangeRef = useRef(onCameraChange);
  useLayoutEffect(() => {
    onCameraChangeRef.current = onCameraChange;
  }, [onCameraChange]);

  const zoomBy = (delta: number) => {
    const camera = cameraRef.current;
    if (!camera) return;
    camera.radius = Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, camera.radius + delta));
    onCameraChangeRef.current?.(camera.radius, angleRef.current);
  };

  useLayoutEffect(() => {
    animRef.current = animFullName;
    frozenTSecRef.current = null;
  }, [animFullName]);

  useLayoutEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  useEffect(() => {
    if (!bbmodel) return;
    const canvas = canvasRef.current;
    const overlay = overlayRef.current;
    if (!canvas || !overlay) return;

    let disposed = false;
    let removeListeners: (() => void) | null = null;

    ensureBabylon()
      .then(() => {
        if (disposed) return;
        const B = (window as any).BABYLON;

        const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: true, antialias: true });
        const scene = new B.Scene(engine);
        scene.clearColor = new B.Color4(0.08, 0.08, 0.08, 0);
        const camera = new B.ArcRotateCamera(
          "cam",
          -Math.PI * 0.25,
          Math.PI / 3.2,
          initialZoom ?? 3.0,
          new B.Vector3(0, 0.9, 0),
          scene,
        );
        camera.inputs.clear();
        cameraRef.current = camera;
        const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
        light.intensity = 1.1;
        light.groundColor = new B.Color3(0.2, 0.2, 0.2);

        const model = buildModel(B, bbmodel, scene);
        if (rightHandItem) {
          if (rightHandRotate) window.mcState.weaponRotations[rightHandItem] = rightHandRotate;
          window.mc.attachWeapon?.(model as unknown as McPlayerModel, rightHandItem, scene, "RIGHT");
        }
        if (leftHandItem) {
          if (leftHandRotate) window.mcState.weaponRotations[leftHandItem] = leftHandRotate;
          window.mc.attachWeapon?.(model as unknown as McPlayerModel, leftHandItem, scene, "LEFT");
        }

        const uuidToName: Record<string, string> = {};
        bbmodel.groups.forEach((g: any) => {
          uuidToName[g.uuid] = g.name;
        });

        let angle = angleRef.current,
          autoRotate = initialAngle === undefined,
          lastInteraction = 0;
        let isDragging = false,
          dragStartX = 0,
          dragStartAngle = 0;
        let wheelDebounceTimer: ReturnType<typeof setTimeout> | null = null;

        const onMouseDown = (e: MouseEvent) => {
          isDragging = true;
          dragStartX = e.clientX;
          dragStartAngle = angle;
          lastInteraction = Date.now();
          autoRotate = false;
          overlay.style.cursor = "grabbing";
        };
        const onMouseMove = (e: MouseEvent) => {
          if (!isDragging) return;
          angle = dragStartAngle - (e.clientX - dragStartX) * 0.02;
          lastInteraction = Date.now();
        };
        const onMouseUp = () => {
          if (isDragging) onCameraChangeRef.current?.(camera.radius, angle);
          isDragging = false;
          lastInteraction = Date.now();
          overlay.style.cursor = "grab";
        };
        const onWheel = (e: WheelEvent) => {
          e.preventDefault();
          lastInteraction = Date.now();
          autoRotate = false;
          zoomBy(Math.sign(e.deltaY) * WHEEL_ZOOM_STEP);
          if (wheelDebounceTimer) clearTimeout(wheelDebounceTimer);
          wheelDebounceTimer = setTimeout(() => {
            onCameraChangeRef.current?.(camera.radius, angle);
          }, 250);
        };
        overlay.addEventListener("mousedown", onMouseDown);
        overlay.addEventListener("mousemove", onMouseMove);
        overlay.addEventListener("mouseup", onMouseUp);
        overlay.addEventListener("mouseleave", onMouseUp);
        overlay.addEventListener("wheel", onWheel, { passive: false });

        removeListeners = () => {
          overlay.removeEventListener("mousedown", onMouseDown);
          overlay.removeEventListener("mousemove", onMouseMove);
          overlay.removeEventListener("mouseup", onMouseUp);
          overlay.removeEventListener("mouseleave", onMouseUp);
          overlay.removeEventListener("wheel", onWheel);
          if (wheelDebounceTimer) clearTimeout(wheelDebounceTimer);
        };

        function eulerXYZToQuat(rx: number, ry: number, rz: number): any {
          const cx = Math.cos(rx / 2),
            sx = Math.sin(rx / 2);
          const cy = Math.cos(ry / 2),
            sy = Math.sin(ry / 2);
          const cz = Math.cos(rz / 2),
            sz = Math.sin(rz / 2);
          return new B.Quaternion(
            cz * cy * sx - sz * sy * cx,
            cz * sy * cx + sz * cy * sx,
            -cz * sy * sx + sz * cy * cx,
            cz * cy * cx + sz * sy * sx,
          );
        }

        scene.onBeforeRenderObservable.add(() => {
          if (autoRotate && !pausedRef.current) angle += 0.015;
          else if (!pausedRef.current && Date.now() - lastInteraction > 30000) autoRotate = true;
          model.root.rotation.y = angle;
          angleRef.current = angle;

          for (const boneName of Object.keys(model.pivotNodes)) {
            const entry = model.pivotNodes[boneName];
            entry.node.rotationQuaternion = null;
            entry.node.rotation.x = 0;
            entry.node.rotation.y = 0;
            entry.node.rotation.z = 0;
          }

          const animDef = bbmodel.animations?.find((a: any) => a.name === animRef.current);
          if (!animDef) return;

          const length = animDef.length || 1;
          let tSec: number;
          if (pausedRef.current) {
            if (frozenTSecRef.current === null) {
              frozenTSecRef.current = ((Date.now() % (length * 1000)) / (length * 1000)) * length;
            }
            tSec = frozenTSecRef.current;
          } else {
            frozenTSecRef.current = null;
            tSec = ((Date.now() % (length * 1000)) / (length * 1000)) * length;
          }

          for (const [uuid, animator] of Object.entries(animDef.animators) as [string, any][]) {
            const boneName = uuidToName[uuid];
            if (!boneName) continue;
            const pivot = model.pivotNodes[boneName];
            if (!pivot) continue;
            const kfs: BbModelKeyframe[] = animator.keyframes?.filter((k: any) => k.channel === "rotation") ?? [];
            if (!kfs.length) continue;

            pivot.node.rotationQuaternion = eulerXYZToQuat(
              interpAxis(kfs, tSec, "x") * DEG,
              interpAxis(kfs, tSec, "y") * DEG,
              interpAxis(kfs, tSec, "z") * DEG,
            );
          }
        });

        engine.runRenderLoop(() => scene.render());
        engineRef.current = engine;

        if (disposed) {
          removeListeners?.();
          model.root.getChildMeshes(true).forEach((m: any) => m.dispose());
          Object.values(model.pivotNodes).forEach((p: any) => (p as any).node.dispose());
          model.root.dispose();
          engine.dispose();
          engineRef.current = null;
        }
      })
      .catch(console.error);

    return () => {
      disposed = true;
      removeListeners?.();
      cameraRef.current = null;
      if (engineRef.current) {
        engineRef.current.dispose();
        engineRef.current = null;
      }
    };
    // initialZoom/initialAngle intentionally excluded — they only seed the camera at mount;
    // including them would rebuild (and visibly reset/jump) the scene on every camera-change
    // persist round-trip, since that round-trip changes the URL these props are derived from.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bbmodel, rightHandItem, leftHandItem, rightHandRotate, leftHandRotate]);

  return (
    <div style={{ position: "relative", display: "inline-block" }}>
      <canvas
        ref={canvasRef}
        width={width * 2}
        height={height * 2}
        style={{ display: "block", width, height, borderRadius: 6, background: "#0e1726" }}
      />
      <div
        ref={overlayRef}
        style={{ position: "absolute", inset: 0, cursor: "grab", userSelect: "none", borderRadius: 6 }}
      />
      <div style={{ position: "absolute", top: 6, right: 6, display: "flex", flexDirection: "column", gap: 4 }}>
        <button type="button" onClick={() => zoomBy(-BUTTON_ZOOM_STEP)} style={zoomButtonStyle} aria-label="Zoom in">
          +
        </button>
        <button type="button" onClick={() => zoomBy(BUTTON_ZOOM_STEP)} style={zoomButtonStyle} aria-label="Zoom out">
          −
        </button>
      </div>
    </div>
  );
}

const zoomButtonStyle: CSSProperties = {
  width: 22,
  height: 22,
  lineHeight: "20px",
  padding: 0,
  fontSize: 14,
  fontFamily: "monospace",
  color: "#ddd",
  background: "rgba(20, 26, 38, 0.75)",
  border: "1px solid #3a3a3a",
  borderRadius: 4,
  cursor: "pointer",
};
