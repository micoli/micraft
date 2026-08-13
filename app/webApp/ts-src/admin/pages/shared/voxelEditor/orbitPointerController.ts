// Real chunk/scene meshes are merged per-material geometry, not one pickable object per block —
// the targeted block coordinate is derived from the hit point nudged across the face along its
// normal (standard voxel-picking technique), same idea the in-game block targeting uses.
//
// Drag mode is decided by whichever modifier key was held at POINTERDOWN: no modifier =
// place/break (picking must happen on POINTERUP so a drag-start doesn't fire it early — a move
// threshold distinguishes an actual click from the start of a drag), Cmd/Meta = rotate, Option/Alt
// = pan, Ctrl = zoom. Shift held during that click breaks instead of placing, overriding the
// persistent place/break mode for just that click. Shared by the Instance and Scene editors — only
// the actual hit resolution (bounds-check, API call, ghost/overlay update) differs between them,
// supplied here via callbacks.
export interface OrbitPointerControllerOptions {
  B: typeof BABYLON;
  scene: InstanceType<typeof BABYLON.Scene>;
  camera: InstanceType<typeof BABYLON.ArcRotateCamera>;
  canvas: HTMLCanvasElement;
  getMode: () => "place" | "break";
  onHoverMove: (evt: PointerEvent) => void;
  onClick: (ctx: {
    pick: NonNullable<ReturnType<InstanceType<typeof BABYLON.Scene>["pick"]>>;
    normal: InstanceType<typeof BABYLON.Vector3> | null;
    mode: "place" | "break";
  }) => void;
}

export function setupOrbitPointerController(opts: OrbitPointerControllerOptions): () => void {
  const { B, scene, camera, canvas, getMode, onHoverMove, onClick } = opts;

  const preventContextMenu = (e: Event) => e.preventDefault();
  canvas.addEventListener("contextmenu", preventContextMenu);

  let downX = 0;
  let downY = 0;
  let lastX = 0;
  let lastY = 0;
  let downShift = false;
  let dragMode: "rotate" | "pan" | "zoom" | "place" | null = null;
  const CLICK_MOVE_THRESHOLD = 5;
  const ROTATE_SENSITIVITY = 0.005;
  const PAN_SENSITIVITY = 0.0015;
  const ZOOM_SENSITIVITY = 0.01;

  function panCamera(dx: number, dy: number) {
    const m = camera.getWorldMatrix();
    const right = B.Vector3.TransformNormal(new B.Vector3(1, 0, 0), m).normalize();
    const up = B.Vector3.TransformNormal(new B.Vector3(0, 1, 0), m).normalize();
    const scale = camera.radius * PAN_SENSITIVITY;
    camera.target.addInPlace(right.scale(-dx * scale)).addInPlace(up.scale(dy * scale));
  }

  const clickObserver = scene.onPointerObservable.add((pointerInfo) => {
    if (pointerInfo.type === B.PointerEventTypes.POINTERDOWN) {
      const evt = pointerInfo.event as PointerEvent;
      downX = lastX = scene.pointerX;
      downY = lastY = scene.pointerY;
      downShift = evt.shiftKey;
      dragMode = evt.metaKey ? "rotate" : evt.altKey ? "pan" : evt.ctrlKey ? "zoom" : "place";
      // Re-pivot the orbit around whatever was under the cursor at the start of the rotation drag,
      // instead of always orbiting around the current (possibly stale, panned-away-from)
      // camera.target. setTarget() recomputes alpha/beta/radius from the camera's current position
      // so the view doesn't jump — only the pivot point changes.
      if (dragMode === "rotate") {
        const pick = scene.pick(scene.pointerX, scene.pointerY);
        if (pick?.hit && pick.pickedPoint) camera.setTarget(pick.pickedPoint);
      }
      return;
    }
    if (pointerInfo.type === B.PointerEventTypes.POINTERMOVE) {
      if (!dragMode || dragMode === "place") {
        onHoverMove(pointerInfo.event as PointerEvent);
        return;
      }
      const dx = scene.pointerX - lastX;
      const dy = scene.pointerY - lastY;
      lastX = scene.pointerX;
      lastY = scene.pointerY;
      if (dragMode === "rotate") {
        camera.alpha -= dx * ROTATE_SENSITIVITY;
        camera.beta = Math.min(Math.PI - 0.01, Math.max(0.01, camera.beta - dy * ROTATE_SENSITIVITY));
      } else if (dragMode === "pan") {
        panCamera(dx, dy);
      } else if (dragMode === "zoom") {
        camera.radius = Math.max(camera.lowerRadiusLimit ?? 2, camera.radius + dy * camera.radius * ZOOM_SENSITIVITY);
      }
      return;
    }
    if (pointerInfo.type !== B.PointerEventTypes.POINTERUP) return;
    const wasPlaceDrag = dragMode === "place";
    dragMode = null;
    if (!wasPlaceDrag) return;
    const dx = scene.pointerX - downX;
    const dy = scene.pointerY - downY;
    if (dx * dx + dy * dy > CLICK_MOVE_THRESHOLD * CLICK_MOVE_THRESHOLD) return;
    const pick = scene.pick(scene.pointerX, scene.pointerY);
    if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) return;
    const normal = pick.getNormal(true);
    const mode = downShift ? "break" : getMode();
    onClick({ pick, normal, mode });
  });

  return () => {
    scene.onPointerObservable.remove(clickObserver);
    canvas.removeEventListener("contextmenu", preventContextMenu);
  };
}
