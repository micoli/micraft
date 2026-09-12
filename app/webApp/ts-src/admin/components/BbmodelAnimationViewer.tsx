/* eslint-disable @typescript-eslint/no-explicit-any */
import type { CSSProperties, PointerEvent as ReactPointerEvent } from "react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { interpAxis } from "../../game/lib/player/playerModel";
import {
  applyElementPivot,
  buildGroupHierarchy,
  isMeshElement,
  placeElements,
  resolveTextureDims,
} from "../../game/lib/player/bbmodelMesh";
import { collectLimbBones } from "../../game/lib/player/limbBones";
import { ORTHO_YAW, OrthoView, OrthoViewButton } from "./OrthoViewButton";
import { NPC_WALK_ANIM_NAME } from "../../lib/animationHelpers";

// Mirrors the procedural walk in game/components/npc/npcModel.ts (setNpcTransform) — same
// collectLimbBones, so a many-legged creature's rightLegN/leftLegN bones animate here too.
const NPC_WALK_AMP_DEG = 30;

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
  // Standard walk-bone role -> real bbmodel bone name (NPC preview only), so buildGroupHierarchy
  // registers each pivot under its role name too and collectLimbBones can find it by role.
  boneAliases?: Record<string, string>,
): {
  root: any;
  pivotNodes: Record<string, { node: any; origin: [number, number, number]; restRotation: [number, number, number] }>;
  equippedWeapons: { LEFT: any; RIGHT: any };
  equippedArmors: Record<string, any>;
} {
  const SCALE = 1 / 16;

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
  const textureDims = resolveTextureDims(bbmodel);

  const root = new B.TransformNode("playerRoot", scene);

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

  const { pivotNodes, allGroupNodes, elToGroupUuid } = buildGroupHierarchy(bbmodel, scene, topParent, boneAliases);
  placeElements(bbmodel, scene, { elToGroupUuid, allGroupNodes }, topParent, materials, textureDims);

  return { root, pivotNodes, equippedWeapons: { LEFT: null, RIGHT: null }, equippedArmors: {} };
}

// Small height (Y) ticked scale gizmo (half-block graduations, 5-block arm), parented to the
// model root so it spins and tilts along with it. Origin sits at ground level (model-local y=0,
// behind and to the left of the model), running upward from there.
// GL line width is ignored by most desktop browsers (always 1px), so whole-block ticks can't be
// "thicker" than half-block ones by line width alone — they're drawn as thin cylinders instead.
function quatFromTo(B: any, from: any, to: any): any {
  const dot = B.Vector3.Dot(from, to);
  if (dot > 0.9999) return B.Quaternion.Identity();
  if (dot < -0.9999) {
    let axis = B.Vector3.Cross(B.Axis.X, from);
    if (axis.lengthSquared() < 1e-6) axis = B.Vector3.Cross(B.Axis.Y, from);
    return B.Quaternion.RotationAxis(axis.normalize(), Math.PI);
  }
  return B.Quaternion.RotationAxis(B.Vector3.Cross(from, to).normalize(), Math.acos(dot));
}

function buildAxesGizmo(B: any, scene: any, parent: any): any {
  const LENGTH = 5;
  const TICK_SPACING = 0.5;
  const TICK_SIZE = 0.04;
  const WHOLE_TICK_DIAMETER = 0.03;

  const gizmo = new B.TransformNode("axesGizmo", scene);
  gizmo.parent = parent;
  gizmo.position = new B.Vector3(-0.8, 0, -0.8);

  const axisDefs: Array<{ dir: [number, number, number]; color: any; tickDir: [number, number, number] }> = [
    { dir: [0, 1, 0], color: new B.Color3(0.3, 0.65, 0.35), tickDir: [1, 0, 0] },
  ];

  for (const { dir, color, tickDir } of axisDefs) {
    const [dx, dy, dz] = dir;
    const [tx, ty, tz] = tickDir;
    const tickAxis = new B.Vector3(tx, ty, tz);
    const lines: any[] = [[new B.Vector3(0, 0, 0), new B.Vector3(dx * LENGTH, dy * LENGTH, dz * LENGTH)]];

    let mat: any = null;
    for (let m = TICK_SPACING; m <= LENGTH + 1e-6; m += TICK_SPACING) {
      const center = new B.Vector3(dx * m, dy * m, dz * m);
      const isWholeBlock = Math.abs(Math.round(m) - m) < 1e-6;
      if (isWholeBlock) {
        if (!mat) {
          mat = new B.StandardMaterial(`axesGizmoMat_${dx}_${dy}_${dz}`, scene);
          mat.emissiveColor = color;
          mat.disableLighting = true;
        }
        const tick = B.MeshBuilder.CreateCylinder(
          `axesGizmoTick_${dx}_${dy}_${dz}_${m}`,
          { height: TICK_SIZE * 2, diameter: WHOLE_TICK_DIAMETER, tessellation: 6 },
          scene,
        );
        tick.material = mat;
        tick.position = center;
        tick.rotationQuaternion = quatFromTo(B, B.Axis.Y, tickAxis);
        tick.isPickable = false;
        tick.parent = gizmo;
      } else {
        const offset = new B.Vector3(tx * TICK_SIZE, ty * TICK_SIZE, tz * TICK_SIZE);
        lines.push([center.subtract(offset), center.add(offset)]);
      }
    }
    const mesh = B.MeshBuilder.CreateLineSystem(`axesGizmo_${dx}_${dy}_${dz}`, { lines }, scene);
    mesh.color = color;
    mesh.isPickable = false;
    mesh.parent = gizmo;
  }
  return gizmo;
}

// Virtual floor grid at model-local y=0, quarter-block cells, translucent gray. Parented to the
// model root so it spins/tilts along with it, same as the axes gizmo.
function buildGroundGrid(B: any, scene: any, parent: any): any {
  const HALF_EXTENT = 5;
  const CELL = 0.25;

  const lines: any[] = [];
  for (let i = -HALF_EXTENT; i <= HALF_EXTENT + 1e-6; i += CELL) {
    lines.push([new B.Vector3(i, 0, -HALF_EXTENT), new B.Vector3(i, 0, HALF_EXTENT)]);
    lines.push([new B.Vector3(-HALF_EXTENT, 0, i), new B.Vector3(HALF_EXTENT, 0, i)]);
  }
  const mesh = B.MeshBuilder.CreateLineSystem("groundGrid", { lines }, scene);
  // alpha and visibility both scale opacity multiplicatively — only alpha is used here so the
  // grid stays faint but actually visible (stacking both drove it down to ~0.003, invisible).
  mesh.color = new B.Color3(0.35, 0.35, 0.35);
  mesh.alpha = 0.2;
  mesh.isPickable = false;
  mesh.parent = parent;
  return mesh;
}

const DEG = Math.PI / 180;
const MIN_RADIUS = 1.0;
const MAX_RADIUS = 16.0;
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
  angle = null,
  onCameraChange,
  rightHandItem = null,
  leftHandItem = null,
  rightHandRotate = null,
  leftHandRotate = null,
  armors = [],
  standaloneItem = false,
  npcWalkAliases,
  showAxes = false,
  showGround = false,
  hideUI = false,
  background = true,
  width = 200,
  height = 280,
}: {
  bbmodel: BbModel | null;
  animFullName: string;
  paused?: boolean;
  // Shows a red/green/blue X/Y/Z axes gizmo next to the model.
  showAxes?: boolean;
  // Shows a translucent quarter-block floor grid at world y=0.
  showGround?: boolean;
  // Hides the zoom buttons, resize handle and ortho-view buttons — just the model + drag/wheel
  // interaction. For embedding the raw preview (e.g. a Storybook screenshot) without the chrome.
  hideUI?: boolean;
  // true (default) = opaque dark canvas background; false = transparent (for compositing over
  // another background, e.g. a doc page).
  background?: boolean;
  // Standard walk bone -> real bbmodel bone, for the synthetic "npc_walk" animation.
  npcWalkAliases?: Record<string, string>;
  // Camera radius (zoom) and model spin angle (radians) to restore on mount.
  initialZoom?: number;
  initialAngle?: number;
  // Fixes the model's yaw and disables the auto-rotate spin entirely (unlike initialAngle, which
  // only seeds the starting angle — auto-rotate still kicks in after the idle timeout).
  angle?: number | null;
  // Fired after a user interaction changes zoom and/or angle, so the caller can persist it.
  onCameraChange?: (zoom: number, angle: number) => void;
  rightHandItem?: string | null;
  leftHandItem?: string | null;
  // Overrides the equipment's yaml-configured `rotate` for this preview only.
  rightHandRotate?: RotateOverride;
  leftHandRotate?: RotateOverride;
  armors?: string[];
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
  // Set inside the scene effect below; lets the ortho-view buttons drive the camera/model yaw
  // from outside the closure that owns `yaw`/`autoRotate`.
  const setOrthoViewRef = useRef<((view: OrthoView) => void) | null>(null);
  // Lets the showAxes-toggle effect below drive the axes gizmo from outside the closure that
  // owns the scene/axesViewer, the same pattern as setOrthoViewRef.
  const setAxesVisibleRef = useRef<((visible: boolean) => void) | null>(null);
  useEffect(() => {
    setAxesVisibleRef.current?.(showAxes);
  }, [showAxes]);
  const setGroundVisibleRef = useRef<((visible: boolean) => void) | null>(null);
  useEffect(() => {
    setGroundVisibleRef.current?.(showGround);
  }, [showGround]);
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
  const angleRef = useRef(angle ?? initialAngle ?? 0);
  const animRef = useRef(animFullName);
  const pausedRef = useRef(paused);
  const npcWalkAliasesRef = useRef(npcWalkAliases);
  useLayoutEffect(() => {
    npcWalkAliasesRef.current = npcWalkAliases;
  }, [npcWalkAliases]);
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

  const armorsKey = armors.join(",");

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

        const engine = new B.Engine(canvas, true, {
          preserveDrawingBuffer: true,
          antialias: true,
          alpha: !background,
        });
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
        // Babylon's default minZ (~1) sits at or past MIN_RADIUS for a small/close-up model —
        // the near clip plane then cuts through the geometry near its center, exposing the far
        // inner (back-culling-disabled) faces instead of the model's outside.
        camera.minZ = 0.01;
        cameraRef.current = camera;
        const light = new B.HemisphericLight("light", new B.Vector3(1, 2, 0.5), scene);
        light.intensity = 1.1;
        light.groundColor = new B.Color3(0.2, 0.2, 0.2);

        const model = buildModel(B, bbmodel, scene, standaloneItem, npcWalkAliasesRef.current);
        if (rightHandItem) {
          if (rightHandRotate) window.mcState.weaponRotations[rightHandItem] = rightHandRotate;
          window.mc.attachWeapon?.(model as unknown as McPlayerModel, rightHandItem, scene, "RIGHT");
        }
        if (leftHandItem) {
          if (leftHandRotate) window.mcState.weaponRotations[leftHandItem] = leftHandRotate;
          window.mc.attachWeapon?.(model as unknown as McPlayerModel, leftHandItem, scene, "LEFT");
        }
        armors.forEach((a) => window.mc.attachArmor?.(model as unknown as McPlayerModel, a, scene));

        // Fit the camera to the model's actual bounds instead of a fixed target/radius tuned for
        // player-sized skins — otherwise a much taller or wider NPC gets cropped by the viewport.
        // Also re-root the spin/tilt rotation on the model's own bounding-box center: many NPC
        // bbmodels aren't authored centered on (0,0) horizontally, so spinning model.root in place
        // would otherwise swing the whole body off-center as it rotates.
        let spinPivot = model.root;
        if (!standaloneItem) {
          // false = recurse through the group-node hierarchy, not just direct children (which are
          // TransformNodes for the bbmodel's groups, not meshes).
          const meshes = model.root.getChildMeshes(false);
          meshes.forEach((m: any) => m.computeWorldMatrix(true));
          let minX = Infinity,
            maxX = -Infinity,
            minY = Infinity,
            maxY = -Infinity,
            minZ = Infinity,
            maxZ = -Infinity;
          for (const m of meshes) {
            const bb = m.getBoundingInfo().boundingBox;
            minX = Math.min(minX, bb.minimumWorld.x);
            maxX = Math.max(maxX, bb.maximumWorld.x);
            minY = Math.min(minY, bb.minimumWorld.y);
            maxY = Math.max(maxY, bb.maximumWorld.y);
            minZ = Math.min(minZ, bb.minimumWorld.z);
            maxZ = Math.max(maxZ, bb.maximumWorld.z);
          }
          if (meshes.length > 0) {
            const centerX = (minX + maxX) / 2;
            const centerY = (minY + maxY) / 2;
            const centerZ = (minZ + maxZ) / 2;

            spinPivot = new B.TransformNode("spinPivot", scene);
            spinPivot.position = new B.Vector3(centerX, 0, centerZ);
            model.root.parent = spinPivot;
            model.root.position.x -= centerX;
            model.root.position.z -= centerZ;

            camera.target.x = centerX;
            camera.target.y = centerY;
            camera.target.z = centerZ;

            if (initialZoom === undefined) {
              // camera.fov is vertical; a wide/deep model (e.g. an elephant) needs the horizontal
              // fov derived from the canvas aspect ratio, or it gets cropped left/right even
              // though its height fits fine.
              const halfHeight = Math.max((maxY - minY) / 2, 0.1);
              const halfWidth = Math.max((maxX - minX) / 2, (maxZ - minZ) / 2, 0.1);
              const vFov = camera.fov ?? 0.8;
              const aspect = aspectRef.current || 1;
              const hFov = 2 * Math.atan(Math.tan(vFov / 2) * aspect);
              const radiusForHeight = halfHeight / Math.sin(vFov / 2);
              const radiusForWidth = halfWidth / Math.sin(hFov / 2);
              const fitRadius = Math.max(radiusForHeight, radiusForWidth) * 1.15;
              camera.radius = Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, fitRadius));
            }
          }
        }

        const uuidToName: Record<string, string> = {};
        bbmodel.groups.forEach((g: any) => {
          uuidToName[g.uuid] = g.name;
        });

        // Renamed from the prop-shadowing "angle" — this tracks the live yaw each frame.
        let yaw = angleRef.current,
          autoRotate = angle == null && initialAngle === undefined,
          lastInteraction = 0;
        let isDragging = false,
          isRotateDrag = false,
          dragStartX = 0,
          dragStartY = 0,
          dragStartAngle = 0,
          dragStartHeight = 0,
          dragStartPitch = 0,
          heightOffset = 0,
          pitch = 0;
        let wheelDebounceTimer: ReturnType<typeof setTimeout> | null = null;

        const onMouseDown = (e: MouseEvent) => {
          isDragging = true;
          isRotateDrag = e.shiftKey;
          dragStartX = e.clientX;
          dragStartY = e.clientY;
          dragStartAngle = yaw;
          dragStartHeight = heightOffset;
          dragStartPitch = pitch;
          lastInteraction = Date.now();
          autoRotate = false;
          overlay.style.cursor = isRotateDrag ? "alias" : "grabbing";
        };
        const onMouseMove = (e: MouseEvent) => {
          if (!isDragging) return;
          yaw = dragStartAngle - (e.clientX - dragStartX) * 0.02;
          if (isRotateDrag) {
            // Shift + drag: vertical movement rotates the model instead of moving it.
            pitch = dragStartPitch - (e.clientY - dragStartY) * 0.02;
            spinPivot.rotation.x = pitch;
          } else {
            // Dragging up moves the model up (screen Y decreases while moving up).
            heightOffset = dragStartHeight - (e.clientY - dragStartY) * 0.02;
            model.root.position.y = heightOffset;
          }
          lastInteraction = Date.now();
        };
        const onMouseUp = () => {
          if (isDragging) onCameraChangeRef.current?.(camera.radius, yaw);
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
            onCameraChangeRef.current?.(camera.radius, yaw);
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
          if (autoRotate && !pausedRef.current) yaw += 0.015;
          // A fixed `angle` prop keeps the model still forever — no resuming spin after idle.
          else if (angle == null && !pausedRef.current && Date.now() - lastInteraction > 30000) autoRotate = true;
          spinPivot.rotation.y = yaw;
          angleRef.current = yaw;

          for (const boneName of Object.keys(model.pivotNodes)) {
            const entry = model.pivotNodes[boneName];
            const rest = entry.restRotation ?? [0, 0, 0];
            entry.node.rotationQuaternion = null;
            entry.node.rotation.x = rest[0];
            entry.node.rotation.y = rest[1];
            entry.node.rotation.z = rest[2];
          }

          if (animRef.current === NPC_WALK_ANIM_NAME) {
            const len = 1;
            let tSec: number;
            if (pausedRef.current) {
              if (frozenTSecRef.current === null) frozenTSecRef.current = (Date.now() % (len * 1000)) / 1000;
              tSec = frozenTSecRef.current;
            } else {
              frozenTSecRef.current = null;
              tSec = (Date.now() % (len * 1000)) / 1000;
            }
            const phase = tSec / len;
            for (const { name: bname, phase: limbPhase } of collectLimbBones(model.pivotNodes)) {
              const pivot = model.pivotNodes[bname];
              if (!pivot) continue;
              pivot.node.rotationQuaternion = null;
              pivot.node.rotation.x =
                (pivot.restRotation?.[0] ?? 0) + NPC_WALK_AMP_DEG * DEG * Math.sin(phase * 2 * Math.PI + limbPhase);
            }
            return;
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

            const rest = pivot.restRotation ?? [0, 0, 0];
            pivot.node.rotationQuaternion = eulerXYZToQuat(
              rest[0] + interpAxis(kfs, tSec, "x") * DEG,
              rest[1] + interpAxis(kfs, tSec, "y") * DEG,
              rest[2] + interpAxis(kfs, tSec, "z") * DEG,
            );
          }
        });

        let axesGizmo: any = null;
        setAxesVisibleRef.current = (visible: boolean) => {
          if (visible && !axesGizmo) {
            axesGizmo = buildAxesGizmo(B, scene, model.root);
          } else if (!visible && axesGizmo) {
            axesGizmo.dispose();
            axesGizmo = null;
          }
        };
        setAxesVisibleRef.current(showAxes);

        let groundGrid: any = null;
        setGroundVisibleRef.current = (visible: boolean) => {
          if (visible && !groundGrid) {
            groundGrid = buildGroundGrid(B, scene, model.root);
          } else if (!visible && groundGrid) {
            groundGrid.dispose();
            groundGrid = null;
          }
        };
        setGroundVisibleRef.current(showGround);

        setOrthoViewRef.current = (view: OrthoView) => {
          autoRotate = false;
          lastInteraction = Date.now();
          camera.alpha = -Math.PI / 2;
          [yaw, camera.beta] = ORTHO_YAW[view];
          spinPivot.rotation.y = yaw;
          angleRef.current = yaw;
          onCameraChangeRef.current?.(camera.radius, yaw);
        };

        engine.runRenderLoop(() => scene.render());
        engineRef.current = engine;

        if (disposed) {
          removeListeners?.();
          axesGizmo?.dispose();
          groundGrid?.dispose();
          spinPivot.getChildMeshes(false).forEach((m: any) => m.dispose());
          Object.values(model.pivotNodes).forEach((p: any) => (p as any).node.dispose());
          spinPivot.dispose();
          engine.dispose();
          engineRef.current = null;
        }
      })
      .catch(console.error);

    return () => {
      disposed = true;
      removeListeners?.();
      cameraRef.current = null;
      setOrthoViewRef.current = null;
      setAxesVisibleRef.current = null;
      setGroundVisibleRef.current = null;
      if (engineRef.current) {
        engineRef.current.dispose();
        engineRef.current = null;
      }
    };
    // initialZoom/initialAngle intentionally excluded — they only seed the camera at mount;
    // including them would rebuild (and visibly reset/jump) the scene on every camera-change
    // persist round-trip, since that round-trip changes the URL these props are derived from.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bbmodel, rightHandItem, leftHandItem, rightHandRotate, leftHandRotate, standaloneItem, armorsKey, background]);

  return (
    <div style={{ position: "relative", display: "inline-block" }}>
      <canvas
        ref={canvasRef}
        width={size.width * 2}
        height={size.height * 2}
        style={{
          display: "block",
          width: size.width,
          height: size.height,
          borderRadius: 6,
          background: background ? "#0e1726" : "transparent",
        }}
      />
      <div
        ref={overlayRef}
        style={{ position: "absolute", inset: 0, cursor: "grab", userSelect: "none", borderRadius: 6 }}
      />
      {!hideUI && (
        <>
          <div style={{ position: "absolute", top: 6, right: 6, display: "flex", flexDirection: "column", gap: 4 }}>
            <button
              type="button"
              onClick={() => zoomBy(-BUTTON_ZOOM_STEP)}
              style={zoomButtonStyle}
              aria-label="Zoom in"
            >
              +
            </button>
            <button
              type="button"
              onClick={() => zoomBy(BUTTON_ZOOM_STEP)}
              style={zoomButtonStyle}
              aria-label="Zoom out"
            >
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
          <div
            style={{
              position: "absolute",
              bottom: 6,
              left: 6,
              display: "grid",
              gridTemplateColumns: "repeat(3, 1fr)",
              gap: 4,
              width: 48,
            }}
          >
            <div>
              <OrthoViewButton view={"T"} setOrthoViewRef={setOrthoViewRef} />
            </div>
            <div>
              <OrthoViewButton view={"N"} setOrthoViewRef={setOrthoViewRef} />
            </div>
            <div>
              <OrthoViewButton view={"B"} setOrthoViewRef={setOrthoViewRef} />
            </div>

            <div>
              <OrthoViewButton view={"W"} setOrthoViewRef={setOrthoViewRef} />
            </div>
            <div>
              <OrthoViewButton view={"S"} setOrthoViewRef={setOrthoViewRef} />
            </div>
            <div>
              <OrthoViewButton view={"E"} setOrthoViewRef={setOrthoViewRef} />
            </div>
          </div>
        </>
      )}
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
