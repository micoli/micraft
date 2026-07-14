import { useEffect, useRef, useState, useCallback } from "react";
import type {
  Camera,
  ChunkTerrainInfo,
  FollowTarget,
  HouseMapInfo,
  LayerKey,
  Layers,
  MapApiState,
  StaircaseMapInfo,
  VoronoiCellInfo,
} from "./types";
import { LAYER_KEYS } from "./types";

const LAYERS_STORAGE_KEY = "micraft-map-layers";

const VEGETATION_TINT: Record<string, string> = {
  forest: "rgba(30,120,30,0.28)",
  plains: "rgba(80,160,80,0.14)",
  tundra: "rgba(180,210,200,0.12)",
};

const WEATHER_FILL: Record<string, string> = {
  RAIN: "rgba(80,120,255,0.18)",
  STORM: "rgba(100,0,200,0.22)",
  SNOW: "rgba(200,230,255,0.2)",
  FOG: "rgba(150,150,150,0.18)",
};

const WEATHER_STROKE: Record<string, string> = {
  RAIN: "rgba(80,120,255,0.55)",
  STORM: "rgba(100,0,200,0.55)",
  SNOW: "rgba(200,230,255,0.6)",
  FOG: "rgba(140,140,140,0.5)",
};

function loadLayerState(): Layers {
  try {
    const saved = JSON.parse(localStorage.getItem(LAYERS_STORAGE_KEY) || "{}") as Partial<Layers>;
    const result = {} as Layers;
    for (const k of LAYER_KEYS) result[k] = k in saved ? (saved[k] ?? true) : true;
    return result;
  } catch {
    const result = {} as Layers;
    for (const k of LAYER_KEYS) result[k] = true;
    return result;
  }
}

function ticksToTime(ticks: number): string {
  const DAY = 72000;
  const t = ((ticks % DAY) + DAY) % DAY;
  const h = Math.floor(t / 3000);
  const m = Math.floor((t % 3000) / 50);
  return String(h).padStart(2, "0") + ":" + String(m).padStart(2, "0");
}

export interface MapRendererState {
  layers: Layers;
  apiState: MapApiState;
  followTarget: FollowTarget;
  time: string;
  coords: string;
  status: string;
  dragging: boolean;
  onLayerToggle: (key: LayerKey, checked: boolean) => void;
  onSetFollow: (type: "player" | "npc", id: string) => void;
  onFitAll: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
}

function drawContours(
  terrainData: React.RefObject<ChunkTerrainInfo[]>,
  ctx: CanvasRenderingContext2D,
  worldToCanvas: (wx: number, wz: number) => [number, number],
) {
  const heightMap: Record<string, number> = {};
  for (const c of terrainData.current) {
    if (c.avgHeight != null) heightMap[`${c.cx},${c.cz}`] = c.avgHeight;
  }
  ctx.strokeStyle = "rgba(220,220,220,0.45)";
  ctx.lineWidth = 0.6;
  for (const chunk of terrainData.current) {
    if (chunk.avgHeight == null) continue;
    const hBand = Math.floor(chunk.avgHeight / 10);
    const rh = heightMap[`${chunk.cx + 1},${chunk.cz}`];
    if (rh != null && Math.floor(rh / 10) !== hBand) {
      const [ax, az] = worldToCanvas((chunk.cx + 1) * 16, chunk.cz * 16);
      const [bx, bz] = worldToCanvas((chunk.cx + 1) * 16, (chunk.cz + 1) * 16);
      ctx.beginPath();
      ctx.moveTo(ax, az);
      ctx.lineTo(bx, bz);
      ctx.stroke();
    }
    const bh = heightMap[`${chunk.cx},${chunk.cz + 1}`];
    if (bh != null && Math.floor(bh / 10) !== hBand) {
      const [ax, az] = worldToCanvas(chunk.cx * 16, (chunk.cz + 1) * 16);
      const [bx, bz] = worldToCanvas((chunk.cx + 1) * 16, (chunk.cz + 1) * 16);
      ctx.beginPath();
      ctx.moveTo(ax, az);
      ctx.lineTo(bx, bz);
      ctx.stroke();
    }
  }
}

function drawPreciseRoads(
  roadImgRadius: React.RefObject<number>,
  worldToCanvas: (wx: number, wz: number) => [number, number],
  roadImgCx: React.RefObject<number>,
  roadImgCz: React.RefObject<number>,
  cam: Camera,
  ctx: CanvasRenderingContext2D,
  roadImg: React.RefObject<HTMLImageElement | null>,
) {
  if (roadImg.current === null) return;
  const r = roadImgRadius.current;
  const [tx, tz] = worldToCanvas(roadImgCx.current - r, roadImgCz.current + r);
  const px = 2 * r * cam.pxPerBlock;
  ctx.drawImage(roadImg.current, tx, tz, px, px);
}

function drawHouses(
  cam: Camera,
  housesData: React.RefObject<HouseMapInfo[]>,
  worldToCanvas: (wx: number, wz: number) => [number, number],
  ctx: CanvasRenderingContext2D,
) {
  const ppb = cam.pxPerBlock;
  for (const h of housesData.current) {
    const [ax, az] = worldToCanvas(h.x, h.z);
    const w = h.width * ppb,
      d = h.depth * ppb;
    ctx.fillStyle = "rgba(255,200,100,0.32)";
    ctx.fillRect(ax, az - d, w, d);
    ctx.strokeStyle = "#c80";
    ctx.lineWidth = 1;
    ctx.strokeRect(ax, az - d, w, d);
  }
}

function drawVoronoi(
  biomeDirty: React.RefObject<boolean>,
  renderBiomeBorders: () => void,
  ctx: CanvasRenderingContext2D,
  bc: HTMLCanvasElement,
  voronoiCells: React.RefObject<VoronoiCellInfo[]>,
  cam: Camera,
  worldToCanvas: (wx: number, wz: number) => [number, number],
  W: number,
  H: number,
) {
  if (biomeDirty.current) {
    renderBiomeBorders();
    biomeDirty.current = false;
  }
  ctx.drawImage(bc, 0, 0);
  if (voronoiCells.current.length) {
    const fontSize = Math.max(9, Math.min(14, cam.pxPerBlock * 80));
    ctx.font = "bold " + Math.round(fontSize) + "px monospace";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    for (const cell of voronoiCells.current) {
      const [px, pz] = worldToCanvas(cell.x, cell.z);
      if (px < -100 || px > W + 100 || pz < -100 || pz > H + 100) continue;
      ctx.fillStyle = "rgba(0,0,0,0.65)";
      ctx.fillText(cell.biome, px + 1, pz + 1);
      ctx.fillStyle = "#fff";
      ctx.fillText(cell.biome, px, pz);
    }
    ctx.textAlign = "left";
    ctx.textBaseline = "alphabetic";
  }
}

function drawVegetation(
  voronoiCells: React.RefObject<VoronoiCellInfo[]>,
  cam: Camera,
  worldToCanvas: (wx: number, wz: number) => [number, number],
  ctx: CanvasRenderingContext2D,
) {
  const cells = voronoiCells.current;
  const estR = Math.sqrt((3200 * 3200) / cells.length) * 0.65 * cam.pxPerBlock;
  for (const cell of cells) {
    const tint = VEGETATION_TINT[cell.biome];
    if (!tint) continue;
    const [px, pz] = worldToCanvas(cell.x, cell.z);
    ctx.fillStyle = tint;
    ctx.beginPath();
    ctx.arc(px, pz, estR, 0, Math.PI * 2);
    ctx.fill();
  }
}

function drawGrids(
  canvasToWorld: (cx: number, cz: number) => [number, number],
  W: number,
  H: number,
  cam: Camera,
  ctx: CanvasRenderingContext2D,
  worldToCanvas: (wx: number, wz: number) => [number, number],
) {
  const [worldLeft, worldTop] = canvasToWorld(0, 0);
  const [worldRight, worldBottom] = canvasToWorld(W, H);
  const gridStep = Math.pow(10, Math.ceil(Math.log10(80 / cam.pxPerBlock)));
  ctx.strokeStyle = "rgba(255,255,255,0.06)";
  ctx.lineWidth = 0.5;
  ctx.fillStyle = "rgba(255,255,255,0.28)";
  ctx.font = "9px monospace";
  for (let gx = Math.ceil(worldLeft / gridStep) * gridStep; gx <= worldRight; gx += gridStep) {
    const cx = worldToCanvas(gx, 0)[0];
    ctx.beginPath();
    ctx.moveTo(cx, 0);
    ctx.lineTo(cx, H);
    ctx.stroke();
    ctx.fillText(String(Math.round(gx)), cx + 2, 10);
  }
  for (let gz = Math.ceil(worldTop / gridStep) * gridStep; gz <= worldBottom; gz += gridStep) {
    const cz = worldToCanvas(0, gz)[1];
    ctx.beginPath();
    ctx.moveTo(0, cz);
    ctx.lineTo(W, cz);
    ctx.stroke();
    ctx.fillText(String(Math.round(gz)), 2, cz - 2);
  }
}

function drawWeathers(
  state: MapApiState,
  worldToCanvas: (wx: number, wz: number) => [number, number],
  cam: Camera,
  ctx: CanvasRenderingContext2D,
) {
  for (const z of state.weatherZones ?? []) {
    const [wx, wz] = worldToCanvas(z.cx, z.cz);
    const rPx = z.radius * cam.pxPerBlock;
    ctx.beginPath();
    ctx.arc(wx, wz, rPx, 0, Math.PI * 2);
    ctx.fillStyle = WEATHER_FILL[z.type] ?? "rgba(128,128,128,0.15)";
    ctx.fill();
    ctx.strokeStyle = WEATHER_STROKE[z.type] ?? "rgba(128,128,128,0.5)";
    ctx.lineWidth = 1;
    ctx.stroke();
    ctx.fillStyle = "#ccc";
    ctx.font = "10px monospace";
    ctx.fillText(z.type, wx - 12, wz + 3);
  }
}

function drawNPCs(
  state: MapApiState,
  worldToCanvas: (wx: number, wz: number) => [number, number],
  ft:
    | {
        type: "player" | "npc" | undefined;
        id: string | undefined;
      }
    | { type: "player" | "npc"; id: string }
    | null,
  ctx: CanvasRenderingContext2D,
) {
  for (const n of state.npcs) {
    const [nx, nz] = worldToCanvas(n.x, n.z);
    if (ft?.type === "npc" && ft.id === n.id) {
      ctx.strokeStyle = "#ffcc44";
      ctx.lineWidth = 2;
      ctx.strokeRect(nx - 8, nz - 8, 16, 16);
    }
    ctx.fillStyle = "#fa6";
    ctx.beginPath();
    ctx.arc(nx, nz, 5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#fa6";
    ctx.font = "10px monospace";
    ctx.fillText(n.type, nx + 7, nz + 4);
  }
}

function drawStaircases(
  staircases: StaircaseMapInfo[],
  worldToCanvas: (wx: number, wz: number) => [number, number],
  cam: Camera,
  ctx: CanvasRenderingContext2D,
) {
  const r = Math.max(4, Math.min(10, cam.pxPerBlock * 3));
  for (const s of staircases) {
    const [sx, sz] = worldToCanvas(s.x, s.z);
    ctx.beginPath();
    ctx.arc(sx, sz, r, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(160,80,220,0.35)";
    ctx.fill();
    ctx.strokeStyle = "#b060e0";
    ctx.lineWidth = 1.5;
    ctx.stroke();
    if (cam.pxPerBlock >= 0.5) {
      ctx.fillStyle = "#d090f0";
      ctx.font = "9px monospace";
      ctx.fillText("↑", sx - 3, sz + 3);
    }
  }
}

function drawPlayers(
  state: MapApiState,
  worldToCanvas: (wx: number, wz: number) => [number, number],
  ft:
    | {
        type: "player" | "npc" | undefined;
        id: string | undefined;
      }
    | { type: "player" | "npc"; id: string }
    | null,
  ctx: CanvasRenderingContext2D,
) {
  for (const p of state.players) {
    const [px, pz] = worldToCanvas(p.x, p.z);
    const yawRad = p.yaw;
    if (ft?.type === "player" && ft.id === p.id) {
      ctx.strokeStyle = "#44aaff";
      ctx.lineWidth = 2;
      ctx.strokeRect(px - 10, pz - 10, 20, 20);
    }
    ctx.save();
    ctx.translate(px, pz);
    const adx = Math.sin(yawRad);
    const ady = -Math.cos(yawRad);
    const perpX = -ady;
    const perpY = adx;
    const arrowLen = 10;
    const arrowWidth = 5;
    const tipX = adx * arrowLen;
    const tipY = ady * arrowLen;
    const b1x = -adx * arrowLen * 0.4 + perpX * arrowWidth;
    const b1y = -ady * arrowLen * 0.4 + perpY * arrowWidth;
    const b2x = -adx * arrowLen * 0.4 - perpX * arrowWidth;
    const b2y = -ady * arrowLen * 0.4 - perpY * arrowWidth;
    ctx.fillStyle = "#6af";
    ctx.beginPath();
    ctx.moveTo(tipX, tipY);
    ctx.lineTo(b1x, b1y);
    ctx.lineTo(b2x, b2y);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = "#003366";
    ctx.lineWidth = 0.8;
    ctx.stroke();
    ctx.restore();
    ctx.fillStyle = "#8cf";
    ctx.font = "bold 11px monospace";
    ctx.fillText(p.name, px + 9, pz + 4);
  }
}

export function useMapRenderer(canvasRef: React.RefObject<HTMLCanvasElement | null>): MapRendererState {
  const [layers, setLayers] = useState<Layers>(loadLayerState);
  const [apiState, setApiState] = useState<MapApiState>({
    gameTicks: 0,
    players: [],
    npcs: [],
    weatherZones: [],
  });
  const [followTarget, setFollowTarget] = useState<FollowTarget>(null);
  const [coords, setCoords] = useState("x: — z: —");
  const [status, setStatus] = useState("connecting...");
  const [dragging, setDragging] = useState(false);

  const layersRef = useRef<Layers>(layers);
  layersRef.current = layers;

  useEffect(() => {
    localStorage.setItem(LAYERS_STORAGE_KEY, JSON.stringify(layers));
  }, [layers]);

  const camera = useRef<Camera>({ x: 0, z: 0, pxPerBlock: 2 });
  const terrainData = useRef<ChunkTerrainInfo[]>([]);
  const voronoiCells = useRef<VoronoiCellInfo[]>([]);
  const housesData = useRef<HouseMapInfo[]>([]);
  const staircasesData = useRef<StaircaseMapInfo[]>([]);
  const staircasesFetched = useRef(false);
  const roadImg = useRef<HTMLImageElement | null>(null);
  const roadImgCx = useRef(0);
  const roadImgCz = useRef(0);
  const roadImgRadius = useRef(0);
  const biomeBorderData = useRef<Array<{ cx: number; cz: number; mask: boolean[] }>>([]);
  const apiStateRef = useRef<MapApiState>({ gameTicks: 0, players: [], npcs: [], weatherZones: [] });
  const followTargetRef = useRef<FollowTarget>(null);
  const autoFitDone = useRef(false);
  const housesFetchCenter = useRef({ x: NaN, z: NaN });
  const roadsFetchCenter = useRef({ x: NaN, z: NaN });
  const roadsFetching = useRef(false);
  const biomeFetchCenter = useRef({ x: NaN, z: NaN });
  const isDragging = useRef(false);
  const dragLast = useRef({ x: 0, y: 0 });
  const terrainDirty = useRef(true);
  const biomeDirty = useRef(true);
  const terrainCanvas = useRef(document.createElement("canvas"));
  const biomeCanvas = useRef(document.createElement("canvas"));

  const drawRef = useRef<() => void>(() => {});
  const zoomAtRef = useRef<(f: number, mx: number, mz: number) => void>(() => {});
  const fitAllRef = useRef<() => void>(() => {});
  const setFollowRef = useRef<(type: "player" | "npc", id: string) => void>(() => {});

  useEffect(() => {
    const canvas = canvasRef.current!;
    const ctx = canvas.getContext("2d")!;
    const tc = terrainCanvas.current;
    const bc = biomeCanvas.current;

    function worldToCanvas(wx: number, wz: number): [number, number] {
      const cam = camera.current;
      return [(wx - cam.x) * cam.pxPerBlock + canvas.width / 2, -(wz - cam.z) * cam.pxPerBlock + canvas.height / 2];
    }

    function canvasToWorld(cx: number, cz: number): [number, number] {
      const cam = camera.current;
      return [(cx - canvas.width / 2) / cam.pxPerBlock + cam.x, -(cz - canvas.height / 2) / cam.pxPerBlock + cam.z];
    }

    function renderTerrain() {
      const W = tc.width,
        H = tc.height;
      const tCtx = tc.getContext("2d")!;
      tCtx.clearRect(0, 0, W, H);
      const size = Math.ceil(Math.max(1, camera.current.pxPerBlock));
      for (const chunk of terrainData.current) {
        for (let lx = 0; lx < 16; lx++) {
          for (let lz = 0; lz < 16; lz++) {
            const color = chunk.colors[lx * 16 + lz];
            if (!color) continue;
            const [px, pz] = worldToCanvas(chunk.cx * 16 + lx, chunk.cz * 16 + lz);
            if (px + size < 0 || px >= W || pz + size < 0 || pz >= H) continue;
            tCtx.fillStyle = color;
            tCtx.fillRect(px, pz, size, size);
          }
        }
      }
    }

    function renderBiomeBorders() {
      const W = bc.width,
        H = bc.height;
      const bCtx = bc.getContext("2d")!;
      bCtx.clearRect(0, 0, W, H);
      if (!biomeBorderData.current.length) return;
      const size = Math.ceil(Math.max(1, camera.current.pxPerBlock));
      bCtx.fillStyle = "rgba(128,0,0,0.85)";
      for (const chunk of biomeBorderData.current) {
        for (let lx = 0; lx < 16; lx++) {
          for (let lz = 0; lz < 16; lz++) {
            if (!chunk.mask[lx * 16 + lz]) continue;
            const [px, pz] = worldToCanvas(chunk.cx * 16 + lx, chunk.cz * 16 + lz);
            if (px + size < 0 || px >= W || pz + size < 0 || pz >= H) continue;
            bCtx.fillRect(px, pz, size, size);
          }
        }
      }
    }

    function draw() {
      const W = canvas.width,
        H = canvas.height;
      const cam = camera.current;
      const L = layersRef.current;
      const state = apiStateRef.current;
      const ft = followTargetRef.current;

      if (ft) {
        const entity =
          ft.type === "player" ? state.players.find((p) => p.id === ft.id) : state.npcs.find((n) => n.id === ft.id);
        if (entity && (entity.x !== cam.x || entity.z !== cam.z)) {
          cam.x = entity.x;
          cam.z = entity.z;
          terrainDirty.current = true;
          biomeDirty.current = true;
        }
      }

      if (terrainDirty.current) {
        renderTerrain();
        terrainDirty.current = false;
      }

      ctx.fillStyle = "#111";
      ctx.fillRect(0, 0, W, H);
      if (L.chunks) ctx.drawImage(tc, 0, 0);

      // Vegetation
      if (L.vegetation && voronoiCells.current.length) {
        drawVegetation(voronoiCells, cam, worldToCanvas, ctx);
      }

      // Voronoi biome borders
      if (L.voronoi) {
        drawVoronoi(biomeDirty, renderBiomeBorders, ctx, bc, voronoiCells, cam, worldToCanvas, W, H);
      }

      // Contours
      if (L.contours && terrainData.current.length) {
        drawContours(terrainData, ctx, worldToCanvas);
      }

      // Precise roads (PNG overlay)
      if (L["precise-roads"] && roadImg.current) {
        drawPreciseRoads(roadImgRadius, worldToCanvas, roadImgCx, roadImgCz, cam, ctx, roadImg);
      }

      // Houses
      if (L.houses) {
        drawHouses(cam, housesData, worldToCanvas, ctx);
      }

      // Grid
      drawGrids(canvasToWorld, W, H, cam, ctx, worldToCanvas);

      // Weather zones
      if (L.weather) {
        drawWeathers(state, worldToCanvas, cam, ctx);
      }

      // NPCs
      if (L.npcs) {
        drawNPCs(state, worldToCanvas, ft, ctx);
      }

      // Players
      if (L.players) {
        drawPlayers(state, worldToCanvas, ft, ctx);
      }

      // Staircases
      if (L.staircases) {
        drawStaircases(staircasesData.current, worldToCanvas, cam, ctx);
      }
    }

    function autoFitView() {
      const terrain = terrainData.current;
      const state = apiStateRef.current;
      let minX: number, maxX: number, minZ: number, maxZ: number;
      if (terrain.length > 0) {
        minX = Math.min(...terrain.map((c) => c.cx)) * 16;
        maxX = (Math.max(...terrain.map((c) => c.cx)) + 1) * 16;
        minZ = Math.min(...terrain.map((c) => c.cz)) * 16;
        maxZ = (Math.max(...terrain.map((c) => c.cz)) + 1) * 16;
      } else {
        const entities = [...state.players, ...state.npcs];
        if (entities.length > 0) {
          minX = Math.min(...entities.map((e) => e.x)) - 50;
          maxX = Math.max(...entities.map((e) => e.x)) + 50;
          minZ = Math.min(...entities.map((e) => e.z)) - 50;
          maxZ = Math.max(...entities.map((e) => e.z)) + 50;
        } else {
          minX = -100;
          maxX = 100;
          minZ = -100;
          maxZ = 100;
        }
      }
      camera.current.x = (minX + maxX) / 2;
      camera.current.z = (minZ + maxZ) / 2;
      camera.current.pxPerBlock = Math.min(canvas.width / (maxX - minX + 40), canvas.height / (maxZ - minZ + 40));
      terrainDirty.current = true;
      biomeDirty.current = true;
    }

    function zoomAt(factor: number, mx: number, mz: number) {
      const [wx, wz] = canvasToWorld(mx, mz);
      camera.current.pxPerBlock = Math.max(0.05, Math.min(64, camera.current.pxPerBlock * factor));
      camera.current.x = wx - (mx - canvas.width / 2) / camera.current.pxPerBlock;
      camera.current.z = wz + (mz - canvas.height / 2) / camera.current.pxPerBlock;
      terrainDirty.current = true;
      biomeDirty.current = true;
      draw();
    }

    function resize() {
      const wrap = canvas.parentElement!;
      canvas.width = wrap.clientWidth;
      canvas.height = wrap.clientHeight;
      tc.width = canvas.width;
      tc.height = canvas.height;
      bc.width = canvas.width;
      bc.height = canvas.height;
      terrainDirty.current = true;
      biomeDirty.current = true;
      draw();
    }

    async function fetchStaircases() {
      if (staircasesFetched.current) return;
      staircasesFetched.current = true;
      try {
        const r = await fetch("/api/map/staircases");
        if (r.ok) {
          staircasesData.current = await r.json();
          draw();
        }
      } catch {
        staircasesFetched.current = false;
      }
    }

    async function fetchHouses() {
      const cx = Math.round(camera.current.x),
        cz = Math.round(camera.current.z);
      const { x, z } = housesFetchCenter.current;
      if (!isNaN(x) && Math.hypot(cx - x, cz - z) < 300) return;
      housesFetchCenter.current = { x: cx, z: cz };
      try {
        const r = await fetch(`/api/map/houses?cx=${cx}&cz=${cz}&radius=1200`);
        if (r.ok) {
          housesData.current = await r.json();
          draw();
        }
      } catch {
        /* non-critical */
      }
    }

    async function fetchPreciseRoads() {
      if (roadsFetching.current) return;
      const cx = Math.round(camera.current.x),
        cz = Math.round(camera.current.z);
      const { x, z } = roadsFetchCenter.current;
      if (!isNaN(x) && Math.hypot(cx - x, cz - z) < 300) return;
      roadsFetchCenter.current = { x: cx, z: cz };
      const radius = 1200;
      roadsFetching.current = true;
      try {
        const r = await fetch(`/api/map/road-raster.png?cx=${cx}&cz=${cz}&radius=${radius}`);
        if (r.ok) {
          const blob = await r.blob();
          const url = URL.createObjectURL(blob);
          const img = new Image();
          img.onload = () => {
            if (roadImg.current?.src) URL.revokeObjectURL(roadImg.current.src);
            roadImg.current = img;
            roadImgCx.current = cx;
            roadImgCz.current = cz;
            roadImgRadius.current = radius;
            draw();
          };
          img.src = url;
        }
      } catch {
        /* non-critical */
      } finally {
        roadsFetching.current = false;
      }
    }

    async function fetchBiomeBorders() {
      const cx = Math.round(camera.current.x),
        cz = Math.round(camera.current.z);
      const { x, z } = biomeFetchCenter.current;
      if (!isNaN(x) && Math.hypot(cx - x, cz - z) < 300) return;
      biomeFetchCenter.current = { x: cx, z: cz };
      try {
        const r = await fetch(`/api/map/biome-borders?cx=${cx}&cz=${cz}&radius=2000`);
        if (r.ok) {
          biomeBorderData.current = await r.json();
          biomeDirty.current = true;
          draw();
        }
      } catch {
        /* non-critical */
      }
    }

    async function pollState() {
      try {
        const r = await fetch("/api/map/state");
        if (r.ok) {
          const data: MapApiState = await r.json();
          apiStateRef.current = data;
          setApiState(data);
          draw();
          setStatus("updated " + new Date().toLocaleTimeString());
        }
      } catch (e) {
        setStatus("error: " + (e instanceof Error ? e.message : String(e)));
      }
    }

    async function pollTerrain() {
      try {
        const r = await fetch("/api/map/terrain");
        if (r.ok) {
          terrainData.current = await r.json();
          terrainDirty.current = true;
          biomeDirty.current = true;
          if (!autoFitDone.current && terrainData.current.length > 0) {
            autoFitDone.current = true;
            autoFitView();
            fetchHouses();
            fetchPreciseRoads();
            fetchStaircases();
          }
          draw();
        }
      } catch {
        /* non-critical */
      }
    }

    async function fetchVoronoi() {
      try {
        const r = await fetch("/api/map/voronoi?cx=0&cz=0&radius=3200");
        if (r.ok) {
          voronoiCells.current = await r.json();
          draw();
        }
      } catch {
        /* non-critical */
      }
    }

    // Wire up refs for external callers
    drawRef.current = draw;
    zoomAtRef.current = zoomAt;
    fitAllRef.current = () => {
      followTargetRef.current = null;
      setFollowTarget(null);
      autoFitView();
      draw();
    };
    setFollowRef.current = (type, id) => {
      const current = followTargetRef.current;
      if (current?.type === type && current.id === id) {
        followTargetRef.current = null;
        setFollowTarget(null);
      } else {
        const ft = { type, id };
        followTargetRef.current = ft;
        setFollowTarget(ft);
        const state = apiStateRef.current;
        const entity = type === "player" ? state.players.find((p) => p.id === id) : state.npcs.find((n) => n.id === id);
        if (entity) {
          camera.current.x = entity.x;
          camera.current.z = entity.z;
          terrainDirty.current = true;
          biomeDirty.current = true;
        }
      }
      draw();
    };

    // Canvas event handlers
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      if (followTargetRef.current) {
        followTargetRef.current = null;
        setFollowTarget(null);
      }
      const rect = canvas.getBoundingClientRect();
      zoomAt(e.deltaY < 0 ? 1.25 : 0.8, e.clientX - rect.left, e.clientY - rect.top);
    };

    const onMouseDown = (e: MouseEvent) => {
      if (e.button !== 0) return;
      isDragging.current = true;
      dragLast.current = { x: e.clientX, y: e.clientY };
      setDragging(true);
      if (followTargetRef.current) {
        followTargetRef.current = null;
        setFollowTarget(null);
      }
    };

    const onMouseMove = (e: MouseEvent) => {
      if (isDragging.current) {
        const dx = e.clientX - dragLast.current.x;
        const dy = e.clientY - dragLast.current.y;
        camera.current.x -= dx / camera.current.pxPerBlock;
        camera.current.z += dy / camera.current.pxPerBlock;
        dragLast.current = { x: e.clientX, y: e.clientY };
        terrainDirty.current = true;
        biomeDirty.current = true;
        draw();
      }
      const rect = canvas.getBoundingClientRect();
      const [wx, wz] = canvasToWorld(e.clientX - rect.left, e.clientY - rect.top);
      setCoords(`x:${Math.round(wx)}  z:${Math.round(wz)}`);
    };

    const onMouseUp = () => {
      isDragging.current = false;
      setDragging(false);
    };

    canvas.addEventListener("wheel", onWheel, { passive: false });
    canvas.addEventListener("mousedown", onMouseDown);
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    window.addEventListener("resize", resize);
    resize();

    pollState();
    pollTerrain();
    fetchVoronoi();
    fetchBiomeBorders();

    const intervals = [
      setInterval(pollState, 1000),
      setInterval(pollTerrain, 5000),
      setInterval(fetchHouses, 10000),
      setInterval(fetchPreciseRoads, 10000),
      setInterval(fetchBiomeBorders, 10000),
      setInterval(fetchStaircases, 30000),
    ];

    return () => {
      canvas.removeEventListener("wheel", onWheel);
      canvas.removeEventListener("mousedown", onMouseDown);
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
      window.removeEventListener("resize", resize);
      intervals.forEach(clearInterval);
    };
  }, []);

  // Redraw when layers change (layersRef.current already updated synchronously above)
  useEffect(() => {
    drawRef.current();
  }, [layers]);

  const onLayerToggle = useCallback((key: LayerKey, checked: boolean) => {
    setLayers((prev) => ({ ...prev, [key]: checked }));
  }, []);

  const onSetFollow = useCallback((type: "player" | "npc", id: string) => {
    setFollowRef.current(type, id);
  }, []);

  const onFitAll = useCallback(() => {
    fitAllRef.current();
  }, []);

  const onZoomIn = useCallback(() => {
    const canvas = canvasRef.current!;
    zoomAtRef.current(1.5, canvas.width / 2, canvas.height / 2);
  }, [canvasRef]);

  const onZoomOut = useCallback(() => {
    const canvas = canvasRef.current!;
    zoomAtRef.current(0.67, canvas.width / 2, canvas.height / 2);
  }, [canvasRef]);

  return {
    layers,
    apiState,
    followTarget,
    time: ticksToTime(apiState.gameTicks),
    coords,
    status,
    dragging,
    onLayerToggle,
    onSetFollow,
    onFitAll,
    onZoomIn,
    onZoomOut,
  };
}
