// Real chunk/scene meshes are merged per-material geometry, not one pickable object per block —
// the targeted block coordinate is derived from the hit point nudged across the face along its
// normal (standard voxel-picking technique), same idea the in-game block targeting uses.
//
// Drag mode is decided by whichever modifier key was held at POINTERDOWN: no modifier =
// place/break (picking must happen on POINTERUP so a drag-start doesn't fire it early — a move
// threshold distinguishes an actual click from the start of a drag), Cmd/Meta = rotate, Option/Alt
// = pan, Ctrl = zoom. Shift held during that click breaks instead of placing, overriding the
// persistent place/break mode for just that click (select mode ignores this override — there,
// shift instead extends the selection to the clicked block, handled by the caller via the
// shiftKey passed to onClick). Shared by the Instance and Scene editors — only the actual hit
// resolution (bounds-check, API call, ghost/overlay update) differs between them, supplied here
// via callbacks.
export interface OrbitPointerControllerOptions {
  B: typeof BABYLON;
  scene: InstanceType<typeof BABYLON.Scene>;
  camera: InstanceType<typeof BABYLON.ArcRotateCamera>;
  canvas: HTMLCanvasElement;
  getMode: () => "place" | "break" | "select";
  onHoverMove: (evt: PointerEvent) => void;
  onClick: (ctx: {
    pick: NonNullable<ReturnType<InstanceType<typeof BABYLON.Scene>["pick"]>>;
    normal: InstanceType<typeof BABYLON.Vector3> | null;
    mode: "place" | "break" | "select";
    shiftKey: boolean;
  }) => void;
  // Ctrl is claimed by the zoom-drag modifier at POINTERDOWN, so a Ctrl+click never reaches
  // onClick (dragMode stays "zoom", not "place") — this fires instead, on POINTERUP, whenever
  // that zoom press moved less than the click threshold (a held-still Ctrl+click, not a drag).
  onCtrlClick?: (pick: NonNullable<ReturnType<InstanceType<typeof BABYLON.Scene>["pick"]>>) => void;
  // Creative in-game mode only: holding the button in "break" mode keeps breaking whatever block
  // is under the cursor, like the survival mining hold. Off by default — the admin Instance/Scene
  // editors keep one-click-one-block for placement precision.
  continuousBreak?: boolean;
}

export function setupOrbitPointerController(opts: OrbitPointerControllerOptions): () => void {
  const { B, scene, camera, canvas, getMode, onHoverMove, onClick, onCtrlClick, continuousBreak } = opts;

  const preventContextMenu = (e: Event) => e.preventDefault();
  canvas.addEventListener("contextmenu", preventContextMenu);

  let downX = 0;
  let downY = 0;
  let lastX = 0;
  let lastY = 0;
  let downShift = false;
  let downCtrl = false;
  let dragMode: "rotate" | "pan" | "zoom" | "place" | null = null;
  const CLICK_MOVE_THRESHOLD = 5;
  const ROTATE_SENSITIVITY = 0.005;
  const PAN_SENSITIVITY = 0.0015;
  const ZOOM_SENSITIVITY = 0.01;
  const BREAK_REPEAT_MS = 150;

  let breakRepeatId: ReturnType<typeof setInterval> | null = null;
  function stopBreakRepeat() {
    if (breakRepeatId !== null) {
      clearInterval(breakRepeatId);
      breakRepeatId = null;
    }
  }
  function tryContinuousBreak() {
    const pick = scene.pick(scene.pointerX, scene.pointerY);
    if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) return;
    const normal = pick.getNormal(true);
    onClick({ pick, normal, mode: "break", shiftKey: downShift });
  }

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
      downCtrl = evt.ctrlKey;
      dragMode = evt.metaKey ? "rotate" : evt.altKey ? "pan" : evt.ctrlKey ? "zoom" : "place";
      // Re-pivot the orbit around whatever is under the SCREEN CENTER at the start of the
      // rotation drag (not the cursor — a corner-of-screen drag start shouldn't put an off-center
      // point at the pivot), instead of always orbiting around the current (possibly stale,
      // panned-away-from) camera.target. Re-projected onto the current view ray AT THE CAMERA'S
      // CURRENT RADIUS (rather than using the raw, possibly much nearer/farther pick.pickedPoint
      // directly) so setTarget()'s rebuilt radius comes out unchanged — using the raw pick
      // distance let a grazing-angle pick far out on the ground plane balloon the radius, which
      // then turned the very next small mouse move into a huge swing that read as a pan instead
      // of a rotate.
      if (dragMode === "rotate") {
        const engine = scene.getEngine();
        const cx = engine.getRenderWidth() / 2;
        const cy = engine.getRenderHeight() / 2;
        const pick = scene.pick(cx, cy);
        if (pick?.hit && pick.pickedPoint) {
          const direction = pick.pickedPoint.subtract(camera.position).normalize();
          camera.setTarget(camera.position.add(direction.scale(camera.radius)));
        }
      } else if (dragMode === "place" && continuousBreak) {
        const baseMode = getMode();
        const mode = baseMode !== "select" && downShift ? "break" : baseMode;
        if (mode === "break") {
          tryContinuousBreak();
          breakRepeatId = setInterval(tryContinuousBreak, BREAK_REPEAT_MS);
        }
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
    const wasCtrlClick = dragMode === "zoom" && downCtrl;
    dragMode = null;
    if (breakRepeatId !== null) {
      // Continuous break already fired on this press; don't double-fire on release.
      stopBreakRepeat();
      return;
    }
    if (wasCtrlClick) {
      const dx = scene.pointerX - downX;
      const dy = scene.pointerY - downY;
      if (dx * dx + dy * dy > CLICK_MOVE_THRESHOLD * CLICK_MOVE_THRESHOLD) return;
      const pick = scene.pick(scene.pointerX, scene.pointerY);
      if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) return;
      onCtrlClick?.(pick);
      return;
    }
    if (!wasPlaceDrag) return;
    const dx = scene.pointerX - downX;
    const dy = scene.pointerY - downY;
    if (dx * dx + dy * dy > CLICK_MOVE_THRESHOLD * CLICK_MOVE_THRESHOLD) return;
    const pick = scene.pick(scene.pointerX, scene.pointerY);
    if (!pick?.hit || !pick.pickedMesh || !pick.pickedPoint) return;
    const normal = pick.getNormal(true);
    const baseMode = getMode();
    // Shift-to-break only makes sense as a place/break override; in select mode shift instead
    // extends the selection to the clicked block, handled by the caller via shiftKey.
    const mode = baseMode !== "select" && downShift ? "break" : baseMode;
    onClick({ pick, normal, mode, shiftKey: downShift });
  });

  return () => {
    stopBreakRepeat();
    scene.onPointerObservable.remove(clickObserver);
    canvas.removeEventListener("contextmenu", preventContextMenu);
  };
}
