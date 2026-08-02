import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { TranslationKey } from "../i18n";

// ── Layers ────────────────────────────────────────────────────────────────────

/**
 * Versioned: a stored blob holds every key, so changing a default would be invisible to anyone who
 * already visited the page. Bumping the suffix hands out the new defaults once, at the cost of
 * dropping previous layer choices — cheap, since layers are a per-session view setting.
 */
const LAYER_STORAGE_KEY = "micraft-simulator-layers-v2";

export const LAYER_KEYS = ["food", "grid", "aggro", "hunger", "gestation", "names", "players"] as const;
export type LayerKey = (typeof LAYER_KEYS)[number];
export type Layers = Record<LayerKey, boolean>;

/**
 * Layers on for a fresh visit. Names are off: one label per NPC is unreadable past a few dozen of
 * them, which is the normal case in an arena that auto-spawns up to its cap.
 */
export const LAYER_DEFAULTS: Layers = {
  food: true,
  grid: true,
  aggro: true,
  hunger: true,
  gestation: true,
  names: false,
  players: true,
};

export const LAYER_LABEL_KEYS: Record<LayerKey, TranslationKey> = {
  food: "sim.layers.food",
  grid: "sim.layers.grid",
  aggro: "sim.layers.aggro",
  hunger: "sim.layers.hunger",
  gestation: "sim.layers.gestation",
  names: "sim.layers.names",
  players: "sim.layers.players",
};

export function loadLayers(): Layers {
  try {
    const raw = localStorage.getItem(LAYER_STORAGE_KEY);
    return raw ? { ...LAYER_DEFAULTS, ...(JSON.parse(raw) as Partial<Layers>) } : LAYER_DEFAULTS;
  } catch {
    return LAYER_DEFAULTS;
  }
}

export function saveLayers(layers: Layers) {
  try {
    localStorage.setItem(LAYER_STORAGE_KEY, JSON.stringify(layers));
  } catch {
    /* storage unavailable — layers stay session-only */
  }
}

// ── Camera ────────────────────────────────────────────────────────────────────

export interface Camera {
  x: number;
  z: number;
  pxPerBlock: number;
}

export interface ArenaCamera {
  camera: Camera;
  width: number;
  height: number;
  /** world (x, z) → screen pixels, with the Z flip so Z+ points up. */
  w2s: (wx: number, wz: number) => [number, number];
  /** World rectangle currently on screen, with a margin so markers near the edge stay drawn. */
  visibleBounds: { minX: number; minZ: number; maxX: number; maxZ: number };
  /**
   * Report the rendered node so the camera can measure it. Deliberately not used as a `ref`
   * callback: that would make the whole camera object look like a ref to the lint rules.
   */
  onElement: (element: HTMLElement | SVGElement | null) => void;
  onWheel: (e: React.WheelEvent) => void;
  /** Zoom about the middle of the view; used by the on-map +/- buttons. */
  zoomBy: (factor: number) => void;
  /** Recentre and zoom so the whole arena is on screen. */
  fitAll: () => void;
  onMouseDown: (e: React.MouseEvent) => void;
  onPanMove: (e: React.MouseEvent) => void;
  endPan: () => void;
  panning: boolean;
}

const MIN_PPB = 0.5;
const MAX_PPB = 64;
const INITIAL_CAMERA: Camera = { x: 0, z: 0, pxPerBlock: 3 };
const VIEWPORT_MARGIN_BLOCKS = 8;

/** Blocks of breathing room kept around the arena when fitting it, so the walls are not flush. */
const FIT_PADDING_BLOCKS = 8;

/**
 * Scale that puts the whole arena inside a [width]×[height] box.
 *
 * The smaller of the two ratios wins: fitting the wider side would push the other off screen. A
 * degenerate box (before the first measure) falls back to the initial scale rather than 0 or
 * Infinity, both of which propagate into every world↔screen conversion.
 */
export function fitScale(width: number, height: number, arenaHalfSize: number): number {
  if (width <= 1 || height <= 1) return INITIAL_CAMERA.pxPerBlock;
  const span = arenaHalfSize * 2 + FIT_PADDING_BLOCKS;
  return Math.min(MAX_PPB, Math.max(MIN_PPB, Math.min(width, height) / span));
}

/**
 * Pan/zoom camera over the arena. Mutations live in a ref and a requestAnimationFrame turns them
 * into a single re-render.
 *
 * Everything handed back is memoised, including the object itself: the arena re-renders at frame
 * rate, and a camera that looked new on every one of those frames would re-run the canvas draw
 * effect and the viewport effect for nothing. The identity must change only when the camera, the
 * measured size, or the pan state actually changes.
 */
export function useArenaCamera(arenaHalfSize: number): ArenaCamera {
  const cameraRef = useRef<Camera>(INITIAL_CAMERA);
  const dragRef = useRef<{ x: number; z: number; sx: number; sy: number } | null>(null);
  const frameRef = useRef<number | null>(null);
  const fittedRef = useRef(false);
  const [size, setSize] = useState({ w: 800, h: 600 });
  // element in state, not a ref: switching renderer swaps the node and the observer must follow
  const [element, setElement] = useState<HTMLElement | SVGElement | null>(null);
  // Pointer handlers mutate cameraRef at input rate; one rAF publishes it here, so renderers read a
  // plain value instead of a ref during render.
  const [camera, setCamera] = useState<Camera>(INITIAL_CAMERA);
  const [panning, setPanning] = useState(false);

  const redraw = useCallback(() => {
    if (frameRef.current !== null) return;
    frameRef.current = requestAnimationFrame(() => {
      frameRef.current = null;
      setCamera(cameraRef.current);
    });
  }, []);

  useEffect(
    () => () => {
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
    },
    [],
  );

  useEffect(() => {
    if (!element) return;
    const measure = () => {
      const rect = element.getBoundingClientRect();
      setSize({ w: Math.max(1, rect.width), h: Math.max(1, rect.height) });
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [element]);

  const fitAll = useCallback(() => {
    cameraRef.current = { x: 0, z: 0, pxPerBlock: fitScale(size.w, size.h, arenaHalfSize) };
    redraw();
  }, [arenaHalfSize, size.w, size.h, redraw]);

  // fit the whole arena once we know the viewport; afterwards it is the operator's call
  useEffect(() => {
    if (fittedRef.current || size.w <= 1) return;
    fittedRef.current = true;
    fitAll();
  }, [size.w, fitAll]);

  const cam = camera;
  const ppb = cam.pxPerBlock;
  const { w: W, h: H } = size;

  const w2s = useCallback(
    (wx: number, wz: number): [number, number] => [(wx - cam.x) * ppb + W / 2, -(wz - cam.z) * ppb + H / 2],
    [cam.x, cam.z, ppb, W, H],
  );

  // The pointer handlers read cameraRef rather than the published camera: the ref is the live value
  // — the state trails it by up to one frame — and it keeps them out of the memo dependencies, so
  // dragging does not hand out a new handler on every published frame.
  const onWheel = useCallback(
    (e: React.WheelEvent) => {
      e.preventDefault();
      const rect = element?.getBoundingClientRect();
      if (!rect) return;
      const live = cameraRef.current;
      const sx = e.clientX - rect.left;
      const sy = e.clientY - rect.top;
      const next = Math.min(MAX_PPB, Math.max(MIN_PPB, live.pxPerBlock * (e.deltaY < 0 ? 1.15 : 1 / 1.15)));
      // keep the world point under the cursor fixed
      const wx = (sx - W / 2) / live.pxPerBlock + live.x;
      const wz = -(sy - H / 2) / live.pxPerBlock + live.z;
      cameraRef.current = {
        pxPerBlock: next,
        x: wx - (sx - W / 2) / next,
        z: wz + (sy - H / 2) / next,
      };
      redraw();
    },
    [element, W, H, redraw],
  );

  /** Same clamp as the wheel, but centred on the viewport: the middle of the view stays put. */
  const zoomBy = useCallback(
    (factor: number) => {
      const live = cameraRef.current;
      const next = Math.min(MAX_PPB, Math.max(MIN_PPB, live.pxPerBlock * factor));
      if (next === live.pxPerBlock) return;
      cameraRef.current = { ...live, pxPerBlock: next };
      redraw();
    },
    [redraw],
  );

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    const live = cameraRef.current;
    dragRef.current = { x: live.x, z: live.z, sx: e.clientX, sy: e.clientY };
    setPanning(true);
  }, []);

  const onPanMove = useCallback(
    (e: React.MouseEvent) => {
      const drag = dragRef.current;
      if (!drag) return;
      const live = cameraRef.current;
      cameraRef.current = {
        ...live,
        x: drag.x - (e.clientX - drag.sx) / live.pxPerBlock,
        z: drag.z + (e.clientY - drag.sy) / live.pxPerBlock,
      };
      redraw();
    },
    [redraw],
  );

  const endPan = useCallback(() => {
    dragRef.current = null;
    setPanning(false);
  }, []);

  const visibleBounds = useMemo(
    () => ({
      minX: cam.x - W / (2 * ppb) - VIEWPORT_MARGIN_BLOCKS,
      maxX: cam.x + W / (2 * ppb) + VIEWPORT_MARGIN_BLOCKS,
      minZ: cam.z - H / (2 * ppb) - VIEWPORT_MARGIN_BLOCKS,
      maxZ: cam.z + H / (2 * ppb) + VIEWPORT_MARGIN_BLOCKS,
    }),
    [cam.x, cam.z, ppb, W, H],
  );

  return useMemo(
    () => ({
      camera,
      width: W,
      height: H,
      w2s,
      visibleBounds,
      onElement: setElement,
      onWheel,
      zoomBy,
      fitAll,
      onMouseDown,
      onPanMove,
      endPan,
      panning,
    }),
    [camera, W, H, w2s, visibleBounds, onWheel, zoomBy, fitAll, onMouseDown, onPanMove, endPan, panning],
  );
}

// ── Shared drawing metrics ────────────────────────────────────────────────────

/**
 * Grazing food is a backdrop, not a subject: it can cover thousands of cells and at full strength it
 * competes with the NPC markers drawn on top of it. Shared by both renderers so they cannot drift.
 */
export const FOOD_OPACITY = 0.35;

export function markerRadiusFor(pxPerBlock: number): number {
  return Math.max(3, Math.min(9, pxPerBlock * 0.7));
}

export function gridLinesFor(halfSize: number, pxPerBlock: number): number[] {
  const step = Math.max(10, Math.pow(10, Math.ceil(Math.log10(60 / pxPerBlock))));
  const lines: number[] = [];
  for (let g = -halfSize; g <= halfSize; g += step) lines.push(g);
  return lines;
}

/** Radius in pixels within which a pointer counts as hovering an NPC marker. */
export function hitRadiusFor(pxPerBlock: number): number {
  return markerRadiusFor(pxPerBlock) + 4;
}

/**
 * Nearest marker under the pointer, or null when nothing is close enough. The canvas view has no DOM
 * node per NPC, so hover and click go through this instead of mouse events.
 */
export function pickNpcAt<T extends { x: number; z: number }>(
  npcs: readonly T[],
  w2s: (wx: number, wz: number) => [number, number],
  pointerX: number,
  pointerY: number,
  pxPerBlock: number,
): { npc: T; sx: number; sy: number } | null {
  const radius = hitRadiusFor(pxPerBlock);
  let best: { npc: T; sx: number; sy: number } | null = null;
  let bestDistSq = radius * radius;
  for (const npc of npcs) {
    const [nx, ny] = w2s(npc.x, npc.z);
    const distSq = (nx - pointerX) * (nx - pointerX) + (ny - pointerY) * (ny - pointerY);
    if (distSq <= bestDistSq) {
      bestDistSq = distSq;
      best = { npc, sx: nx, sy: ny };
    }
  }
  return best;
}
