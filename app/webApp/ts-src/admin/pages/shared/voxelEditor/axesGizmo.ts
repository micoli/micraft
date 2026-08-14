const GIZMO_SIZE_PX = 90;
const GIZMO_MARGIN_PX = 12;

// Axis tint colors reused for view-cube face labels (kept consistent with prior arrow gizmo).
const AXIS_COLORS: Record<"x" | "y" | "z", string> = { x: "#e5484d", y: "#46a758", z: "#3d63dd" };

const CUBE_HALF = 0.32; // base cube half-extent (kept small so the axis arrows read clearly around it)
const CORNER_HALF = 0.07; // corner cubelet half-extent
const INNER_HALF = CUBE_HALF - CORNER_HALF; // half-length of edge/face pickable regions
const FACE_THICKNESS = 0.05;
const PUSH_FACTOR = 1.07; // pushes corner/edge cubelets slightly proud of the base cube to avoid z-fighting

const AXES: Array<{ dir: [number, number, number]; hex: string; label: string }> = [
  { dir: [1, 0, 0], hex: "#e5484d", label: "X" },
  { dir: [0, 1, 0], hex: "#46a758", label: "Y" },
  { dir: [0, 0, 1], hex: "#3d63dd", label: "Z" },
];

type Vec3 = InstanceType<typeof BABYLON.Vector3>;
type Mesh = InstanceType<typeof BABYLON.Mesh>;

interface GizmoMeshMetadata {
  gizmoDir: Vec3;
  baseColor: InstanceType<typeof BABYLON.Color3>;
}

// Bottom-right ViewCube gizmo — a small always-on-top orthographic scene sharing the main
// canvas/engine (Babylon "picture in picture" technique: a second Scene with a viewport-restricted
// camera, rendered after the main scene each frame). Its camera's alpha/beta are copied from the
// main orbit camera every frame so the cube always reflects current orientation. Clicking a face,
// edge, or corner cubelet smoothly snaps the main camera to look from that direction.
export function createAxesGizmo(
  B: typeof BABYLON,
  engine: InstanceType<typeof BABYLON.Engine>,
  mainCamera: InstanceType<typeof BABYLON.ArcRotateCamera>,
) {
  const scene = new B.Scene(engine);
  // autoClear off: Scene/Engine.clear() clears the *whole* canvas — WebGL glClear ignores
  // gl.viewport and only respects an explicit scissor rect. Left on, this scene's clear would
  // wipe out the main scene's already-rendered pixels every frame, leaving only the gizmo corner
  // visible. We only scissor-clear the *depth* buffer in the gizmo's rectangle before rendering it
  // (below) — never the color buffer, so the main scene's pixels stay as the gizmo's backdrop
  // (transparent overlay look) instead of it painting an opaque square behind the cube.
  scene.autoClear = false;
  scene.autoClearDepthAndStencil = false;

  const camera = new B.ArcRotateCamera("gizmoCam", mainCamera.alpha, mainCamera.beta, 4, B.Vector3.Zero(), scene);
  camera.mode = B.Camera.ORTHOGRAPHIC_CAMERA;
  const ortho = 1.5;
  camera.orthoLeft = -ortho;
  camera.orthoRight = ortho;
  camera.orthoTop = ortho;
  camera.orthoBottom = -ortho;

  new B.HemisphericLight("gizmoLight", new B.Vector3(0.3, 1, 0.2), scene);

  const shaftLength = 0.8;
  const headLength = 0.35;

  for (const { dir, hex, label } of AXES) {
    const direction = new B.Vector3(...dir);
    const color = B.Color3.FromHexString(hex);

    const mat = new B.StandardMaterial(`gizmoArrowMat${label}`, scene);
    mat.diffuseColor = color;
    mat.emissiveColor = color;
    mat.specularColor = B.Color3.Black();
    mat.disableLighting = true;

    const shaft = B.MeshBuilder.CreateCylinder(`gizmoShaft${label}`, { height: shaftLength, diameter: 0.09 }, scene);
    const head = B.MeshBuilder.CreateCylinder(
      `gizmoHead${label}`,
      { height: headLength, diameterTop: 0, diameterBottom: 0.22 },
      scene,
    );
    shaft.material = mat;
    head.material = mat;

    // Cylinders default to pointing along +Y — rotate onto `direction`, then slide out along it.
    const up = B.Vector3.Up();
    const axis = B.Vector3.Cross(up, direction);
    if (axis.length() > 1e-6) {
      const angle = Math.acos(B.Vector3.Dot(up, direction));
      const rotation = B.Quaternion.RotationAxis(axis.normalize(), angle);
      shaft.rotationQuaternion = rotation;
      head.rotationQuaternion = rotation.clone();
    } else if (direction.y < 0) {
      const flip = B.Quaternion.RotationAxis(B.Vector3.Right(), Math.PI);
      shaft.rotationQuaternion = flip;
      head.rotationQuaternion = flip.clone();
    }
    shaft.position = direction.scale(CUBE_HALF + shaftLength / 2);
    head.position = direction.scale(CUBE_HALF + shaftLength + headLength / 2);

    const labelPlane = B.MeshBuilder.CreatePlane(`gizmoArrowLabel${label}`, { size: 0.4 }, scene);
    labelPlane.position = direction.scale(CUBE_HALF + shaftLength + headLength + 0.25);
    labelPlane.billboardMode = B.Mesh.BILLBOARDMODE_ALL;
    const labelTex = new B.DynamicTexture(`gizmoArrowLabelTex${label}`, { width: 64, height: 64 }, scene, false);
    labelTex.hasAlpha = true;
    const labelCtx = labelTex.getContext() as CanvasRenderingContext2D;
    labelCtx.clearRect(0, 0, 64, 64);
    labelCtx.fillStyle = hex;
    labelCtx.font = "bold 44px sans-serif";
    labelCtx.textAlign = "center";
    labelCtx.textBaseline = "middle";
    labelCtx.fillText(label, 32, 34);
    labelTex.update();
    const labelMat = new B.StandardMaterial(`gizmoArrowLabelMat${label}`, scene);
    labelMat.diffuseTexture = labelTex;
    labelMat.emissiveColor = B.Color3.White();
    labelMat.specularColor = B.Color3.Black();
    labelMat.backFaceCulling = false;
    labelMat.disableLighting = true;
    labelPlane.material = labelMat;
  }

  function makeFlatMaterial(name: string, color: InstanceType<typeof BABYLON.Color3>) {
    const mat = new B.StandardMaterial(name, scene);
    mat.diffuseColor = color;
    mat.emissiveColor = color;
    mat.specularColor = B.Color3.Black();
    mat.disableLighting = true;
    return mat;
  }

  function makeFaceMaterial(name: string, hex: string, label: string) {
    const tex = new B.DynamicTexture(`${name}Tex`, { width: 128, height: 128 }, scene, false);
    const ctx = tex.getContext() as CanvasRenderingContext2D;
    ctx.fillStyle = hex;
    ctx.fillRect(0, 0, 128, 128);
    ctx.fillStyle = "#000000aa";
    ctx.font = "bold 40px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(label, 64, 64);
    tex.update();
    const mat = new B.StandardMaterial(name, scene);
    mat.diffuseTexture = tex;
    mat.emissiveColor = B.Color3.White();
    mat.specularColor = B.Color3.Black();
    mat.disableLighting = true;
    return mat;
  }

  function registerPickable(mesh: Mesh, dir: Vec3, material: InstanceType<typeof BABYLON.StandardMaterial>) {
    mesh.material = material;
    const metadata: GizmoMeshMetadata = { gizmoDir: dir.normalizeToNew(), baseColor: material.emissiveColor.clone() };
    mesh.metadata = metadata;
  }

  // Base cube — purely visual backdrop the pickable face/edge/corner cubelets sit on.
  const baseCube = B.MeshBuilder.CreateBox("gizmoBaseCube", { size: CUBE_HALF * 2 - 0.01 }, scene);
  baseCube.material = makeFlatMaterial("gizmoBaseMat", B.Color3.FromHexString("#2b2f36"));

  const neutralHex = "#9aa1ab";

  // Faces — 6 colored/labelled plates, tinted by axis like the previous arrow gizmo.
  const faceAxes: Array<{ axis: "x" | "y" | "z"; dir: [number, number, number]; label: string }> = [
    { axis: "x", dir: [1, 0, 0], label: "+X" },
    { axis: "x", dir: [-1, 0, 0], label: "-X" },
    { axis: "y", dir: [0, 1, 0], label: "+Y" },
    { axis: "y", dir: [0, -1, 0], label: "-Y" },
    { axis: "z", dir: [0, 0, 1], label: "+Z" },
    { axis: "z", dir: [0, 0, -1], label: "-Z" },
  ];
  for (const { axis, dir, label } of faceAxes) {
    const [nx, ny, nz] = dir;
    const size = INNER_HALF * 2;
    const box = B.MeshBuilder.CreateBox(
      `gizmoFace${label}`,
      {
        width: nx !== 0 ? FACE_THICKNESS : size,
        height: ny !== 0 ? FACE_THICKNESS : size,
        depth: nz !== 0 ? FACE_THICKNESS : size,
      },
      scene,
    );
    box.position = new B.Vector3(nx, ny, nz).scale(CUBE_HALF + FACE_THICKNESS / 2);
    const mat = makeFaceMaterial(`gizmoFaceMat${label}`, AXIS_COLORS[axis], label);
    registerPickable(box, new B.Vector3(nx, ny, nz), mat);
  }

  // Edges — 12 neutral cubelets, one per cube edge midpoint.
  const axisNames: Array<"x" | "y" | "z"> = ["x", "y", "z"];
  for (let free = 0; free < 3; free++) {
    const [a, b] = axisNames.filter((_, i) => i !== free);
    for (const sa of [-1, 1]) {
      for (const sb of [-1, 1]) {
        const dims: Record<"x" | "y" | "z", number> = { x: CORNER_HALF * 2, y: CORNER_HALF * 2, z: CORNER_HALF * 2 };
        dims[axisNames[free]] = INNER_HALF * 2;
        const box = B.MeshBuilder.CreateBox(
          `gizmoEdge${free}${sa}${sb}`,
          { width: dims.x, height: dims.y, depth: dims.z },
          scene,
        );
        const pos: Record<"x" | "y" | "z", number> = { x: 0, y: 0, z: 0 };
        pos[a] = sa * CUBE_HALF * PUSH_FACTOR;
        pos[b] = sb * CUBE_HALF * PUSH_FACTOR;
        box.position = new B.Vector3(pos.x, pos.y, pos.z);
        const dir: Record<"x" | "y" | "z", number> = { x: 0, y: 0, z: 0 };
        dir[a] = sa;
        dir[b] = sb;
        const mat = makeFlatMaterial(`gizmoEdgeMat${free}${sa}${sb}`, B.Color3.FromHexString(neutralHex));
        registerPickable(box, new B.Vector3(dir.x, dir.y, dir.z), mat);
      }
    }
  }

  // Corners — 8 neutral cubelets, one per cube vertex.
  for (const sx of [-1, 1]) {
    for (const sy of [-1, 1]) {
      for (const sz of [-1, 1]) {
        const box = B.MeshBuilder.CreateBox(`gizmoCorner${sx}${sy}${sz}`, { size: CORNER_HALF * 2 }, scene);
        box.position = new B.Vector3(sx, sy, sz).scale(CUBE_HALF * PUSH_FACTOR);
        const mat = makeFlatMaterial(`gizmoCornerMat${sx}${sy}${sz}`, B.Color3.FromHexString(neutralHex));
        registerPickable(box, new B.Vector3(sx, sy, sz), mat);
      }
    }
  }

  function isPickable(mesh: InstanceType<typeof BABYLON.AbstractMesh>): boolean {
    return !!(mesh.metadata as GizmoMeshMetadata | undefined)?.gizmoDir;
  }

  // --- camera navigation -----------------------------------------------------------------

  let navTarget: { alpha: number; beta: number } | null = null;

  function angleDiff(from: number, to: number): number {
    return ((((to - from + Math.PI) % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI)) - Math.PI;
  }

  function navigateTo(dir: Vec3) {
    const y = Math.max(-1, Math.min(1, dir.y));
    let beta = Math.acos(y);
    const sinBeta = Math.sin(beta);
    const alpha = sinBeta > 1e-4 ? Math.atan2(dir.z, dir.x) : mainCamera.alpha;
    if (mainCamera.lowerBetaLimit != null) beta = Math.max(mainCamera.lowerBetaLimit, beta);
    if (mainCamera.upperBetaLimit != null) beta = Math.min(mainCamera.upperBetaLimit, beta);
    navTarget = { alpha, beta };
  }

  // --- pointer interaction (hover highlight + click to navigate) -------------------------

  const canvas = engine.getRenderingCanvas();
  let hovered: InstanceType<typeof BABYLON.AbstractMesh> | null = null;

  function setHovered(mesh: InstanceType<typeof BABYLON.AbstractMesh> | null) {
    if (hovered === mesh) return;
    if (hovered) {
      const meta = hovered.metadata as GizmoMeshMetadata;
      (hovered.material as InstanceType<typeof BABYLON.StandardMaterial>).emissiveColor = meta.baseColor;
    }
    if (mesh) {
      const meta = mesh.metadata as GizmoMeshMetadata;
      (mesh.material as InstanceType<typeof BABYLON.StandardMaterial>).emissiveColor = meta.baseColor.scale(1.5);
    }
    hovered = mesh;
    if (canvas) canvas.style.cursor = mesh ? "pointer" : "";
  }

  function pixelFromEvent(evt: PointerEvent): { x: number; y: number } | null {
    if (!canvas) return null;
    const rect = canvas.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return null;
    return {
      x: (evt.clientX - rect.left) * (engine.getRenderWidth() / rect.width),
      y: (evt.clientY - rect.top) * (engine.getRenderHeight() / rect.height),
    };
  }

  // xPx/yPx are in WebGL viewport convention (origin bottom-left) — what camera.viewport and
  // engine.enableScissor expect. Pointer events are top-down (origin top-left), so hit-testing
  // uses `topY`, yPx flipped across the canvas height.
  function gizmoRect() {
    const w = engine.getRenderWidth();
    const h = engine.getRenderHeight();
    const xPx = w - GIZMO_SIZE_PX - GIZMO_MARGIN_PX;
    const yPx = GIZMO_MARGIN_PX;
    const topY = h - yPx - GIZMO_SIZE_PX;
    return { xPx, yPx, topY };
  }

  function inGizmoRect(x: number, y: number): boolean {
    const { xPx, topY } = gizmoRect();
    return x >= xPx && x <= xPx + GIZMO_SIZE_PX && y >= topY && y <= topY + GIZMO_SIZE_PX;
  }

  function pick(x: number, y: number) {
    return scene.pick(x, y, isPickable, false, camera);
  }

  function onPointerMove(evt: PointerEvent) {
    const px = pixelFromEvent(evt);
    if (!px || !inGizmoRect(px.x, px.y)) {
      setHovered(null);
      return;
    }
    const info = pick(px.x, px.y);
    setHovered(info?.hit ? info.pickedMesh : null);
  }

  // A plain click on a face/edge/corner snaps to that view; a click-and-drag instead orbits the
  // main camera live (mirroring the main viewport's own rotate drag), so the cube also behaves as
  // a drag handle. The main viewport's orbit controller listens via its own scene.onPointerObservable
  // (see orbitPointerController.ts) rather than Babylon's built-in camera input manager, so the only
  // reliable way to stop it from also reacting to the same pointerdown is to intercept the event in
  // the window's capture phase — before it ever reaches the canvas where that listener lives.
  const DRAG_THRESHOLD_PX = 5;
  const ROTATE_SENSITIVITY = 0.005;
  let drag: {
    downX: number;
    downY: number;
    lastX: number;
    lastY: number;
    dragging: boolean;
    clickDir: Vec3 | null;
  } | null = null;

  function onWindowPointerMove(evt: PointerEvent) {
    if (!drag) return;
    evt.preventDefault();
    evt.stopPropagation();
    const totalDx = evt.clientX - drag.downX;
    const totalDy = evt.clientY - drag.downY;
    if (!drag.dragging) {
      if (totalDx * totalDx + totalDy * totalDy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) return;
      drag.dragging = true;
      navTarget = null;
    }
    const dx = evt.clientX - drag.lastX;
    const dy = evt.clientY - drag.lastY;
    drag.lastX = evt.clientX;
    drag.lastY = evt.clientY;
    mainCamera.alpha -= dx * ROTATE_SENSITIVITY;
    const lower = mainCamera.lowerBetaLimit ?? 0.01;
    const upper = mainCamera.upperBetaLimit ?? Math.PI - 0.01;
    mainCamera.beta = Math.min(upper, Math.max(lower, mainCamera.beta - dy * ROTATE_SENSITIVITY));
  }

  function onWindowPointerUp(evt: PointerEvent) {
    if (!drag) return;
    evt.preventDefault();
    evt.stopPropagation();
    if (!drag.dragging && drag.clickDir) navigateTo(drag.clickDir);
    drag = null;
    window.removeEventListener("pointermove", onWindowPointerMove, true);
    window.removeEventListener("pointerup", onWindowPointerUp, true);
  }

  function onWindowPointerDown(evt: PointerEvent) {
    const px = pixelFromEvent(evt);
    if (!px || !inGizmoRect(px.x, px.y)) return;
    evt.preventDefault();
    evt.stopPropagation();
    const info = pick(px.x, px.y);
    const clickDir = info?.hit && info.pickedMesh ? (info.pickedMesh.metadata as GizmoMeshMetadata).gizmoDir : null;
    drag = {
      downX: evt.clientX,
      downY: evt.clientY,
      lastX: evt.clientX,
      lastY: evt.clientY,
      dragging: false,
      clickDir,
    };
    window.addEventListener("pointermove", onWindowPointerMove, true);
    window.addEventListener("pointerup", onWindowPointerUp, true);
  }

  window.addEventListener("pointerdown", onWindowPointerDown, true);
  canvas?.addEventListener("pointermove", onPointerMove);

  function render() {
    if (navTarget) {
      const da = angleDiff(mainCamera.alpha, navTarget.alpha);
      const db = navTarget.beta - mainCamera.beta;
      if (Math.abs(da) < 0.002 && Math.abs(db) < 0.002) {
        mainCamera.alpha = navTarget.alpha;
        mainCamera.beta = navTarget.beta;
        navTarget = null;
      } else {
        mainCamera.alpha += da * 0.25;
        mainCamera.beta += db * 0.25;
      }
    }

    camera.alpha = mainCamera.alpha;
    camera.beta = mainCamera.beta;
    const w = engine.getRenderWidth();
    const h = engine.getRenderHeight();
    const { xPx, yPx } = gizmoRect();
    camera.viewport = new B.Viewport(xPx / w, yPx / h, GIZMO_SIZE_PX / w, GIZMO_SIZE_PX / h);
    // Depth-only scissor clear (see comment above `scene.autoClear`) — leaves the main scene's
    // already-rendered color in place so the gizmo reads as a transparent overlay.
    engine.enableScissor(xPx, yPx, GIZMO_SIZE_PX, GIZMO_SIZE_PX);
    engine.clear(new B.Color4(0, 0, 0, 0), false, true, true);
    engine.disableScissor();
    scene.render();
  }

  function dispose() {
    canvas?.removeEventListener("pointermove", onPointerMove);
    window.removeEventListener("pointerdown", onWindowPointerDown, true);
    window.removeEventListener("pointermove", onWindowPointerMove, true);
    window.removeEventListener("pointerup", onWindowPointerUp, true);
    scene.dispose();
  }

  return { render, dispose };
}
