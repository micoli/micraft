/* eslint-disable @typescript-eslint/no-explicit-any */
import type { CSSProperties, PointerEvent as ReactPointerEvent } from "react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { interpAxis } from "../../game/lib/player/playerModel";
import {
  applyElementPivot,
  buildMeshElement,
  isMeshElement,
  resolveTextureDims,
} from "../../game/lib/player/bbmodelMesh";

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

// Absolute-space corner/vertex points of one element (mesh or cuboid), used to locate the
// standalone item's "handle" element and to compute the barycenter of all its elements.
function elementPoints(el: BbModelElement | BbModelMeshElement): Array<[number, number, number]> {
  if (isMeshElement(el)) {
    return Object.values(el.vertices).map((raw) => applyElementPivot(raw, el.origin, el.rotation));
  }
  const [fx, fy, fz] = el.from,
    [tx, ty, tz] = el.to;
  const points: Array<[number, number, number]> = [];
  for (const x of [fx, tx]) for (const y of [fy, ty]) for (const z of [fz, tz]) points.push([x, y, z]);
  return points;
}

function elementBounds(
  points: Array<[number, number, number]>,
): { min: [number, number, number]; max: [number, number, number] } | null {
  if (points.length === 0) return null;
  const min: [number, number, number] = [Infinity, Infinity, Infinity];
  const max: [number, number, number] = [-Infinity, -Infinity, -Infinity];
  for (const p of points) {
    for (let i = 0; i < 3; i++) {
      if (p[i] < min[i]) min[i] = p[i];
      if (p[i] > max[i]) max[i] = p[i];
    }
  }
  return { min, max };
}

// Quaternion for a Blockbench element's own `rotation` (degrees), composed to match THREE.js's
// "ZYX" Euler order: apply Z first, then Y, then X — built from single-axis primitives (each
// individually unambiguous) rather than a hand-rolled matrix, to avoid any convention mismatch.
function blockbenchRotationQuat(B: any, rotation: [number, number, number]): any {
  const DEG = Math.PI / 180;
  const [rx, ry, rz] = rotation;
  const qz = B.Quaternion.RotationAxis(B.Axis.Z, rz * DEG);
  const qy = B.Quaternion.RotationAxis(B.Axis.Y, ry * DEG);
  const qx = B.Quaternion.RotationAxis(B.Axis.X, rx * DEG);
  return qx.multiply(qy).multiply(qz);
}

function buildModel(
  B: any,
  bbmodel: BbModel,
  scene: any,
  // Weapon/tool codex preview: no bone rig, so orient the model by its own geometry instead —
  // "handle" element's longest axis aligned to world Y, camera orbiting the barycenter of all
  // elements (rather than the player-skin default eye-height target).
  standaloneItem = false,
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
  const textureDefs = bbmodel.textures ?? [];
  const materials: any[] =
    textureDefs.length === 0
      ? [
          // Untextured import (e.g. a bare mesh never baked/UV-mapped) — fall back to a flat
          // gray material instead of leaving mesh.material null (Babylon's stark-white default).
          (() => {
            const fallbackMat = new B.StandardMaterial("skinMat_fallback", scene);
            fallbackMat.diffuseColor = new B.Color3(0.5, 0.5, 0.5);
            fallbackMat.specularColor = new B.Color3(0, 0, 0);
            fallbackMat.backFaceCulling = false;
            fallbackMat.twoSidedLighting = true;
            return fallbackMat;
          })(),
        ]
      : textureDefs.map((texDef, i) => {
          const tex = new B.Texture(texDef.source, scene, true, true, B.Texture.NEAREST_SAMPLINGMODE);
          tex.hasAlpha = false;
          tex.wrapU = B.Texture.CLAMP_ADDRESSMODE;
          tex.wrapV = B.Texture.CLAMP_ADDRESSMODE;
          const texMat = new B.StandardMaterial(`skinMat_${i}`, scene);
          texMat.diffuseTexture = tex;
          texMat.specularColor = new B.Color3(0, 0, 0);
          // Mesh-type elements (arbitrary geometry, e.g. Blender exports) aren't guaranteed
          // consistent triangle winding — backface culling would invisibly drop some of their faces.
          texMat.backFaceCulling = false;
          // Without this, back faces are lit using the front-facing normal, so thin geometry
          // (blades, bowstrings) looks wrongly shaded from the far side instead of just visible.
          texMat.twoSidedLighting = true;
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

  // Top-level elements/groups normally parent straight to `root`; a standalone item preview
  // instead parents them under `centerNode` (translated so the elements' barycenter sits at the
  // origin) inside `alignNode` (rotated so the handle stands vertical).
  let topParent = root;
  if (standaloneItem) {
    const elements = bbmodel.elements as unknown as (BbModelElement | BbModelMeshElement)[];
    const centers: [number, number, number][] = [];
    let handleRotation: [number, number, number] | null = null;
    for (const el of elements) {
      if (el.visibility === false) continue;
      const b = elementBounds(elementPoints(el));
      if (!b) continue;
      centers.push([(b.min[0] + b.max[0]) / 2, (b.min[1] + b.max[1]) / 2, (b.min[2] + b.max[2]) / 2]);
      if (el.name === "handle" && isMeshElement(el) && el.rotation) handleRotation = el.rotation;
    }
    const barycenter: [number, number, number] = centers.length
      ? [
          centers.reduce((s, c) => s + c[0], 0) / centers.length,
          centers.reduce((s, c) => s + c[1], 0) / centers.length,
          centers.reduce((s, c) => s + c[2], 0) / centers.length,
        ]
      : [0, 0, 0];

    // Undo exactly the rotation Blockbench stored on the "handle" element, rather than deriving
    // orientation from its (possibly stale/disconnected) vertex positions — the handle is always
    // authored with its grip axis along local Y, so inverting its own `rotation` reliably restores
    // that vertical rest pose regardless of whether its absolute placement in the file is trustworthy.
    let rotationQuat = B.Quaternion.Identity();
    if (handleRotation && (handleRotation[0] !== 0 || handleRotation[1] !== 0 || handleRotation[2] !== 0)) {
      rotationQuat = B.Quaternion.Inverse(blockbenchRotationQuat(B, handleRotation));
    }

    const alignNode = new B.TransformNode("itemAlign", scene);
    alignNode.parent = root;
    alignNode.rotationQuaternion = rotationQuat;
    const centerNode = new B.TransformNode("itemCenter", scene);
    centerNode.parent = alignNode;
    centerNode.position = new B.Vector3(-barycenter[0] * SCALE, -barycenter[1] * SCALE, -barycenter[2] * SCALE);
    topParent = centerNode;
  }

  bbmodel.groups.forEach((g: any) => {
    const node = new B.TransformNode(`grp_${g.name}`, scene);
    node.parent = topParent;
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
      meshEl.parent = pg ? pg.node : topParent;
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
      mesh.parent = topParent;
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
const MIN_DIM = 100;
const MAX_DIM = 900;

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
  standaloneItem = false,
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
  // A standalone weapon/tool preview (no player skin, no bone rig): orients the model by its own
  // "handle" element instead of a fixed player-eye-height camera target.
  standaloneItem?: boolean;
  width?: number;
  height?: number;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const engineRef = useRef<any>(null);
  const cameraRef = useRef<any>(null);
  // User-driven resize via the bottom-right handle overrides the width/height props; reset
  // whenever the caller passes new props (e.g. switching to a differently-shaped preview).
  const [size, setSize] = useState({ width, height });
  useEffect(() => {
    setSize({ width, height });
  }, [width, height]);
  const aspectRef = useRef(width / height);
  useEffect(() => {
    aspectRef.current = width / height;
  }, [width, height]);
  const resizeStartRef = useRef<{ x: number; y: number; width: number; height: number } | null>(null);

  const onResizePointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.setPointerCapture(e.pointerId);
    resizeStartRef.current = { x: e.clientX, y: e.clientY, width: size.width, height: size.height };
  };
  const onResizePointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const start = resizeStartRef.current;
    if (!start) return;
    const delta = Math.max(e.clientX - start.x, e.clientY - start.y);
    let nextWidth = Math.min(MAX_DIM, Math.max(MIN_DIM, start.width + delta));
    let nextHeight = nextWidth / aspectRef.current;
    if (nextHeight < MIN_DIM || nextHeight > MAX_DIM) {
      nextHeight = Math.min(MAX_DIM, Math.max(MIN_DIM, nextHeight));
      nextWidth = nextHeight * aspectRef.current;
    }
    setSize({ width: nextWidth, height: nextHeight });
  };
  const onResizePointerUp = (e: ReactPointerEvent<HTMLDivElement>) => {
    resizeStartRef.current = null;
    e.currentTarget.releasePointerCapture(e.pointerId);
  };

  useEffect(() => {
    engineRef.current?.resize();
  }, [size.width, size.height]);
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
          new B.Vector3(0, standaloneItem ? 0 : 0.9, 0),
          scene,
        );
        camera.inputs.clear();
        cameraRef.current = camera;
        const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
        light.intensity = 1.1;
        light.groundColor = new B.Color3(0.2, 0.2, 0.2);

        const model = buildModel(B, bbmodel, scene, standaloneItem);
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
          dragStartY = 0,
          dragStartAngle = 0,
          dragStartHeight = 0,
          heightOffset = 0;
        let wheelDebounceTimer: ReturnType<typeof setTimeout> | null = null;

        const onMouseDown = (e: MouseEvent) => {
          isDragging = true;
          dragStartX = e.clientX;
          dragStartY = e.clientY;
          dragStartAngle = angle;
          dragStartHeight = heightOffset;
          lastInteraction = Date.now();
          autoRotate = false;
          overlay.style.cursor = "grabbing";
        };
        const onMouseMove = (e: MouseEvent) => {
          if (!isDragging) return;
          angle = dragStartAngle - (e.clientX - dragStartX) * 0.02;
          // Dragging up moves the model up (screen Y decreases while moving up).
          heightOffset = dragStartHeight - (e.clientY - dragStartY) * 0.02;
          model.root.position.y = heightOffset;
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
  }, [bbmodel, rightHandItem, leftHandItem, rightHandRotate, leftHandRotate, standaloneItem]);

  return (
    <div style={{ position: "relative", display: "inline-block" }}>
      <canvas
        ref={canvasRef}
        width={size.width * 2}
        height={size.height * 2}
        style={{ display: "block", width: size.width, height: size.height, borderRadius: 6, background: "#0e1726" }}
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
      <div
        onPointerDown={onResizePointerDown}
        onPointerMove={onResizePointerMove}
        onPointerUp={onResizePointerUp}
        style={resizeHandleStyle}
        aria-label="Resize preview"
      />
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

const resizeHandleStyle: CSSProperties = {
  position: "absolute",
  bottom: 2,
  right: 2,
  width: 14,
  height: 14,
  cursor: "nwse-resize",
  touchAction: "none",
  backgroundImage:
    "linear-gradient(135deg, transparent 0%, transparent 45%, #888 45%, #888 55%, transparent 55%, transparent 65%, #888 65%, #888 75%, transparent 75%)",
};
