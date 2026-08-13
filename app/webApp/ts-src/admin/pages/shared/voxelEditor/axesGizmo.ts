const GIZMO_SIZE_PX = 90;
const GIZMO_MARGIN_PX = 12;
const AXES: Array<{ dir: [number, number, number]; hex: string; label: string }> = [
  { dir: [1, 0, 0], hex: "#e5484d", label: "X" },
  { dir: [0, 1, 0], hex: "#46a758", label: "Y" },
  { dir: [0, 0, 1], hex: "#3d63dd", label: "Z" },
];

// Bottom-right XYZ orientation gizmo — a small always-on-top orthographic scene sharing the main
// canvas/engine (Babylon "picture in picture" technique: a second Scene with a viewport-restricted
// camera, rendered after the main scene each frame). Its camera's alpha/beta are copied from the
// main orbit camera every frame so the arrows always reflect current orientation; position/target
// are irrelevant since only rotation is shown.
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
  // (transparent overlay look) instead of it painting an opaque square behind the arrows.
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

    const mat = new B.StandardMaterial(`gizmoMat${label}`, scene);
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
    shaft.position = direction.scale(shaftLength / 2);
    head.position = direction.scale(shaftLength + headLength / 2);

    const labelPlane = B.MeshBuilder.CreatePlane(`gizmoLabel${label}`, { size: 0.4 }, scene);
    labelPlane.position = direction.scale(shaftLength + headLength + 0.25);
    labelPlane.billboardMode = B.Mesh.BILLBOARDMODE_ALL;
    const labelTex = new B.DynamicTexture(`gizmoLabelTex${label}`, { width: 64, height: 64 }, scene, false);
    labelTex.hasAlpha = true;
    const ctx = labelTex.getContext() as CanvasRenderingContext2D;
    ctx.clearRect(0, 0, 64, 64);
    ctx.fillStyle = hex;
    ctx.font = "bold 44px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(label, 32, 34);
    labelTex.update();
    const labelMat = new B.StandardMaterial(`gizmoLabelMat${label}`, scene);
    labelMat.diffuseTexture = labelTex;
    labelMat.emissiveColor = B.Color3.White();
    labelMat.specularColor = B.Color3.Black();
    labelMat.backFaceCulling = false;
    labelMat.disableLighting = true;
    labelPlane.material = labelMat;
  }

  function render() {
    camera.alpha = mainCamera.alpha;
    camera.beta = mainCamera.beta;
    const w = engine.getRenderWidth();
    const h = engine.getRenderHeight();
    const xPx = w - GIZMO_SIZE_PX - GIZMO_MARGIN_PX;
    const yPx = GIZMO_MARGIN_PX;
    camera.viewport = new B.Viewport(xPx / w, yPx / h, GIZMO_SIZE_PX / w, GIZMO_SIZE_PX / h);
    // Depth-only scissor clear (see comment above `scene.autoClear`) — leaves the main scene's
    // already-rendered color in place so the gizmo reads as a transparent overlay.
    engine.enableScissor(xPx, yPx, GIZMO_SIZE_PX, GIZMO_SIZE_PX);
    engine.clear(new B.Color4(0, 0, 0, 0), false, true, true);
    engine.disableScissor();
    scene.render();
  }

  function dispose() {
    scene.dispose();
  }

  return { render, dispose };
}
