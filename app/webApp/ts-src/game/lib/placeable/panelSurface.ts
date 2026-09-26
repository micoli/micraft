import type { Scene, UniversalCamera } from "@babylonjs/core";
import { Matrix, Vector3 } from "@babylonjs/core";

// Interactive panel — an HTML sign projected in 3D as a real DOM layer (an <iframe>), not a mesh
// texture: it needs to stay navigable (links, forms), which a DynamicTexture can't offer. The
// panel div sits in its own layer (#mc-panels) above #renderCanvas and below #mc-ui.
//
// Each frame, the panel's 4 real-world corners are projected to screen pixels with Babylon's own
// `Vector3.Project` — the same projection the engine uses to render the 3D scene, so the 4 points
// are trustworthy (no hand-derived camera math). A CSS `matrix3d` is then solved so the element's
// own box (its native pixel size, `transform-origin: 0 0`) lands exactly on those 4 points — a
// planar projective transform ("map a square to an arbitrary quadrilateral"), the same closed-form
// used for keystone-correction / texture-mapping (Heckbert 1989). This gives true perspective
// skew (the sign foreshortens correctly at an angle) without needing `perspective` on any
// ancestor — the matrix already encodes the full projective divide via its last row.
//
// Occlusion (a panel behind a wall) isn't implemented — the DOM layer draws above the canvas
// regardless of voxels in front of it. Left as a follow-up (see the plan's risk notes).

interface PanelRecord {
  homeUrl: string;
  external: boolean;
}

interface PanelTransform {
  x: number;
  y: number;
  z: number;
  rotationStep: number;
}

// Single hardcoded panel size for v1 — PlaceableRegistry (generic, shared with every placeable
// kind) doesn't carry panel-specific dimensions; matches PanelDefinition's defaults server-side.
const PANEL_WIDTH_BLOCKS = 2.0;
const PANEL_HEIGHT_BLOCKS = 1.5;
// The placeable's own position is ground level (post base), but PANEL_WOOD.bbmodel's board is
// centered ~1.75 blocks above that (board spans y=1..2.5 in the model) — offset the anchor up to
// the board's actual center instead of the post's foot.
const PANEL_CENTER_Y_OFFSET = 1.75;
// World-unit offset off the board's face, toward the front (see cornersOf).
const PANEL_NORMAL_OFFSET = 0.05;
// Native size the iframe document itself lays out at (a CSS `transform` doesn't reflow an
// embedded document's own viewport, only its rendered size — see `PanelConstants` server-side).
// Kept higher than the on-screen apparent size at typical viewing distance so close-up/oblique
// viewing (where the near part of the quad is magnified well past 1:1) doesn't visibly pixelate.
const PANEL_PIXEL_WIDTH = 1280;
const PANEL_PIXEL_HEIGHT = 960;

const ACTIVE_RADIUS = 16;
const MAX_ACTIVE = 4;
const FOCUS_DISTANCE = 4;
const MIN_VIEW_DIST = 0.15; // behind-camera / degenerate-perspective guard

const panels = new Map<string, PanelRecord>();
const transforms = new Map<string, PanelTransform>();
const mounted = new Map<string, HTMLDivElement>();

let sceneRef: Scene | null = null;
let cameraRef: UniversalCamera | null = null;
let containerEl: HTMLDivElement | null = null;
let focusedId: string | null = null;
let escapeListenerInstalled = false;

function ensureContainer(): HTMLDivElement {
  if (containerEl && document.body.contains(containerEl)) return containerEl;
  const canvas = document.getElementById("renderCanvas");
  const div = document.createElement("div");
  div.id = "mc-panels";
  div.style.position = "fixed";
  div.style.inset = "0";
  div.style.pointerEvents = "none";
  div.style.zIndex = "5"; // above the canvas (0), below #mc-ui overlays
  div.style.overflow = "hidden";
  if (canvas?.parentElement) canvas.parentElement.insertBefore(div, canvas.nextSibling);
  else document.body.appendChild(div);
  containerEl = div;
  return div;
}

/** World-space center of the panel's board (see [PANEL_CENTER_Y_OFFSET]). */
function centerOf(t: PanelTransform): Vector3 {
  return new Vector3(t.x, t.y + PANEL_CENTER_Y_OFFSET, t.z);
}

/**
 * The panel's 4 board corners in world space, matching the mesh's own yaw
 * (`placeableModel.ts` sets `rotation.y = -(rotationStep * PI/6)`, and Babylon's left-handed
 * `RotationY(angle)` maps local +X to world `(cos(angle), 0, -sin(angle))`).
 */
function cornersOf(t: PanelTransform): { tl: Vector3; tr: Vector3; br: Vector3; bl: Vector3 } {
  const angle = -(t.rotationStep * (Math.PI / 6));
  const rightX = Math.cos(angle) * (PANEL_WIDTH_BLOCKS / 2);
  const rightZ = -Math.sin(angle) * (PANEL_WIDTH_BLOCKS / 2);
  // Nudge the DOM plane slightly off the board's own face (along its outward normal) so it sits
  // proud of the wood mesh instead of coplanar with it — avoids the iframe visually fighting with
  // the frame/board geometry right at the surface.
  const normalX = -Math.sin(angle) * PANEL_NORMAL_OFFSET;
  const normalZ = -Math.cos(angle) * PANEL_NORMAL_OFFSET;
  const c = centerOf(t);
  const cx = c.x + normalX;
  const cz = c.z + normalZ;
  const halfH = PANEL_HEIGHT_BLOCKS / 2;
  return {
    tl: new Vector3(cx - rightX, c.y + halfH, cz - rightZ),
    tr: new Vector3(cx + rightX, c.y + halfH, cz + rightZ),
    br: new Vector3(cx + rightX, c.y - halfH, cz + rightZ),
    bl: new Vector3(cx - rightX, c.y - halfH, cz - rightZ),
  };
}

interface ScreenPoint {
  x: number;
  y: number;
}

/**
 * Closed-form planar projective transform mapping the unit square `(0,0)-(1,0)-(1,1)-(0,1)` to
 * the 4 given destination points, in that same corner order (Heckbert, "Fundamentals of Texture
 * Mapping and Image Warping", 1989) — returns the 3×3 homogeneous matrix as `{a..i}` such that
 * `x = (a·u + b·v + c) / (g·u + h·v + i)`, `y = (d·u + e·v + f) / (g·u + h·v + i)`.
 */
function squareToQuad(
  p0: ScreenPoint,
  p1: ScreenPoint,
  p2: ScreenPoint,
  p3: ScreenPoint,
): { a: number; b: number; c: number; d: number; e: number; f: number; g: number; h: number; i: number } {
  const A = p2.x - p1.x;
  const B = p2.x - p3.x;
  const C = p1.x - p0.x + (p3.x - p2.x);
  const D = p2.y - p1.y;
  const E = p2.y - p3.y;
  const F = p1.y - p0.y + (p3.y - p2.y);
  const det = A * E - B * D;

  const g = Math.abs(det) < 1e-9 ? 0 : (C * E - B * F) / det;
  const h = Math.abs(det) < 1e-9 ? 0 : (A * F - C * D) / det;
  return {
    c: p0.x,
    f: p0.y,
    i: 1,
    a: p1.x * (g + 1) - p0.x,
    d: p1.y * (g + 1) - p0.y,
    b: p3.x * (h + 1) - p0.x,
    e: p3.y * (h + 1) - p0.y,
    g,
    h,
  };
}

function createPanelElement(id: string, record: PanelRecord): HTMLDivElement {
  const el = document.createElement("div");
  el.dataset.panelId = id;
  el.style.position = "absolute";
  el.style.left = "0";
  el.style.top = "0";
  el.style.width = `${PANEL_PIXEL_WIDTH}px`;
  el.style.height = `${PANEL_PIXEL_HEIGHT}px`;
  el.style.transformOrigin = "0 0";
  el.style.pointerEvents = "none";
  const iframe = document.createElement("iframe");
  iframe.src = record.homeUrl;
  iframe.style.width = "100%";
  iframe.style.height = "100%";
  iframe.style.border = "none";
  iframe.style.background = "#1b1b1f";
  // Local pages already get a strict CSP (sandbox; …) from the server — this attribute is
  // defense in depth. External URLs get scripts/forms but never allow-same-origin (no cookie /
  // storage access to this origin, no reaching back into the parent document).
  iframe.setAttribute("sandbox", record.external ? "allow-scripts allow-forms" : "");
  el.appendChild(iframe);
  ensureContainer().appendChild(el);
  return el;
}

function destroyPanelElement(id: string): void {
  mounted.get(id)?.remove();
  mounted.delete(id);
  if (focusedId === id) unfocus();
}

function updateFrame(): void {
  if (!sceneRef || !cameraRef) return;
  const canvas = sceneRef.getEngine().getRenderingCanvas();
  if (!canvas) return;
  ensureContainer();
  const view = cameraRef.getViewMatrix();
  const globalViewport = cameraRef.viewport.toGlobal(canvas.clientWidth, canvas.clientHeight);
  const transformMatrix = sceneRef.getTransformMatrix();
  const identity = Matrix.Identity();

  const withDist: Array<{ id: string; dist: number }> = [];
  transforms.forEach((t, id) => {
    if (!panels.has(id)) return;
    const viewPos = Vector3.TransformCoordinates(centerOf(t), view);
    if (viewPos.z <= MIN_VIEW_DIST) return;
    withDist.push({ id, dist: viewPos.z });
  });
  withDist.sort((a, b) => a.dist - b.dist);

  const shouldBeMounted = new Set<string>();
  let count = 0;
  for (const { id, dist } of withDist) {
    const keep = id === focusedId || (count < MAX_ACTIVE && dist <= ACTIVE_RADIUS);
    if (!keep) continue;
    shouldBeMounted.add(id);
    if (id !== focusedId) count++;
  }
  mounted.forEach((_el, id) => {
    if (!shouldBeMounted.has(id)) destroyPanelElement(id);
  });

  for (const id of shouldBeMounted) {
    const t = transforms.get(id);
    const record = panels.get(id);
    if (!t || !record) continue;
    let el = mounted.get(id);
    if (!el) {
      el = createPanelElement(id, record);
      mounted.set(id, el);
    }
    const corners = cornersOf(t);
    // Any corner behind (or right at) the camera plane makes the projection meaningless —
    // hide the panel for this frame instead of rendering a garbage-warped box.
    const behindCamera = [corners.tl, corners.tr, corners.br, corners.bl].some(
      (c) => Vector3.TransformCoordinates(c, view).z <= MIN_VIEW_DIST,
    );
    if (behindCamera) {
      el.style.display = "none";
      continue;
    }
    el.style.display = "";
    const tl = Vector3.Project(corners.tl, identity, transformMatrix, globalViewport);
    const tr = Vector3.Project(corners.tr, identity, transformMatrix, globalViewport);
    const br = Vector3.Project(corners.br, identity, transformMatrix, globalViewport);
    const bl = Vector3.Project(corners.bl, identity, transformMatrix, globalViewport);
    const m = squareToQuad(tl, tr, br, bl);
    // See squareToQuad's docstring: it maps the *unit* square. The element's own box is
    // PANEL_PIXEL_WIDTH×PANEL_PIXEL_HEIGHT, so pre-scale the u/v columns down to match.
    const a = m.a / PANEL_PIXEL_WIDTH;
    const d = m.d / PANEL_PIXEL_WIDTH;
    const g = m.g / PANEL_PIXEL_WIDTH;
    const b = m.b / PANEL_PIXEL_HEIGHT;
    const e = m.e / PANEL_PIXEL_HEIGHT;
    const h = m.h / PANEL_PIXEL_HEIGHT;
    el.style.transform = `matrix3d(${a},${d},0,${g}, ${b},${e},0,${h}, 0,0,1,0, ${m.c},${m.f},0,${m.i})`;
  }
}

function onEscapeCapture(e: KeyboardEvent): void {
  if (e.key !== "Escape" || !focusedId) return;
  e.preventDefault();
  e.stopImmediatePropagation();
  unfocus();
}

function resumePointerLockIfNeeded(): void {
  if (window.mcState?.freeCursor) return;
  const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
  canvas?.requestPointerLock();
}

function unfocus(): void {
  if (!focusedId) return;
  const el = mounted.get(focusedId);
  if (el) {
    el.style.pointerEvents = "none";
    el.style.zIndex = "";
  }
  focusedId = null;
  if (window.mcState) {
    window.mcState.modalOpen = false;
    window.mcState.panelFocusedId = null;
  }
  resumePointerLockIfNeeded();
}

export function registerPanelSurface(): Pick<
  McBindings,
  "initPanelSurface" | "panelSync" | "panelUpsert" | "panelRemove" | "setPanelTransform" | "focusPanel" | "unfocusPanel"
> {
  return {
    initPanelSurface: (scene: Scene, camera: UniversalCamera): void => {
      sceneRef = scene;
      cameraRef = camera;
      ensureContainer();
      scene.onBeforeRenderObservable.add(updateFrame);
      if (!escapeListenerInstalled) {
        document.addEventListener("keydown", onEscapeCapture, true);
        escapeListenerInstalled = true;
      }
    },

    panelSync: (json: string): void => {
      let list: Array<{ placeableId: string; homeUrl: string; external: boolean }>;
      try {
        list = JSON.parse(json);
      } catch {
        return;
      }
      const ids = new Set(list.map((p) => p.placeableId));
      panels.clear();
      list.forEach((p) => panels.set(p.placeableId, { homeUrl: p.homeUrl, external: p.external }));
      mounted.forEach((_el, id) => {
        if (!ids.has(id)) destroyPanelElement(id);
      });
    },

    panelUpsert: (json: string): void => {
      let info: { placeableId: string; homeUrl: string; external: boolean };
      try {
        info = JSON.parse(json);
      } catch {
        return;
      }
      const prev = panels.get(info.placeableId);
      panels.set(info.placeableId, { homeUrl: info.homeUrl, external: info.external });
      // Content changed (new URL/pages) — drop the mounted iframe so it remounts with fresh src.
      if (prev && (prev.homeUrl !== info.homeUrl || prev.external !== info.external)) {
        destroyPanelElement(info.placeableId);
      }
    },

    panelRemove: (placeableId: string): void => {
      panels.delete(placeableId);
      transforms.delete(placeableId);
      destroyPanelElement(placeableId);
    },

    setPanelTransform: (placeableId: string, x: number, y: number, z: number, rotationStep: number): void => {
      transforms.set(placeableId, { x, y, z, rotationStep });
    },

    focusPanel: (placeableId: string): boolean => {
      if (focusedId === placeableId) return true;
      if (focusedId) unfocus();
      const t = transforms.get(placeableId);
      if (!t || !cameraRef) return false;
      const dx = t.x - cameraRef.position.x;
      const dy = t.y - cameraRef.position.y;
      const dz = t.z - cameraRef.position.z;
      if (Math.sqrt(dx * dx + dy * dy + dz * dz) > FOCUS_DISTANCE) return false;
      const el = mounted.get(placeableId);
      if (!el) return false;
      el.style.pointerEvents = "auto";
      el.style.zIndex = "10";
      focusedId = placeableId;
      document.exitPointerLock();
      if (window.mcState) {
        window.mcState.modalOpen = true;
        window.mcState.panelFocusedId = placeableId;
      }
      return true;
    },

    unfocusPanel: (): void => unfocus(),
  };
}
