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

export const VEGETATION_TINT: Record<string, string> = {
  forest: "rgba(30,120,30,0.28)",
  plains: "rgba(80,160,80,0.14)",
  tundra: "rgba(180,210,200,0.12)",
};

export const WEATHER_FILL: Record<string, string> = {
  RAIN: "rgba(80,120,255,0.18)",
  STORM: "rgba(100,0,200,0.22)",
  SNOW: "rgba(200,230,255,0.2)",
  FOG: "rgba(150,150,150,0.18)",
};

export const WEATHER_STROKE: Record<string, string> = {
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

export function computeBiomeBorderPath(segs: Array<{ x1: number; z1: number; x2: number; z2: number }>): string {
  return segs.map((s) => `M${s.x1} ${s.z1}L${s.x2} ${s.z2}`).join("");
}

export function computeTerrainPaths(data: ChunkTerrainInfo[]): Array<{ color: string; d: string }> {
  const byColor = new Map<string, string>();
  for (const chunk of data) {
    for (let lx = 0; lx < 16; lx++) {
      for (let lz = 0; lz < 16; lz++) {
        const color = chunk.colors[lx * 16 + lz];
        if (!color) continue;
        const wx = chunk.cx * 16 + lx;
        const wz = chunk.cz * 16 + lz;
        byColor.set(color, (byColor.get(color) ?? "") + `M${wx} ${wz}h1v1h-1z`);
      }
    }
  }
  return Array.from(byColor.entries()).map(([color, d]) => ({ color, d }));
}

export function computeContourPath(data: ChunkTerrainInfo[]): string {
  const heightMap: Record<string, number> = {};
  for (const c of data) {
    if (c.avgHeight != null) heightMap[`${c.cx},${c.cz}`] = c.avgHeight;
  }
  let d = "";
  for (const chunk of data) {
    if (chunk.avgHeight == null) continue;
    const hBand = Math.floor(chunk.avgHeight / 10);
    const rh = heightMap[`${chunk.cx + 1},${chunk.cz}`];
    if (rh != null && Math.floor(rh / 10) !== hBand) {
      d += `M${(chunk.cx + 1) * 16} ${chunk.cz * 16}L${(chunk.cx + 1) * 16} ${(chunk.cz + 1) * 16}`;
    }
    const bh = heightMap[`${chunk.cx},${chunk.cz + 1}`];
    if (bh != null && Math.floor(bh / 10) !== hBand) {
      d += `M${chunk.cx * 16} ${(chunk.cz + 1) * 16}L${(chunk.cx + 1) * 16} ${(chunk.cz + 1) * 16}`;
    }
  }
  return d;
}

export interface RoadBounds {
  cx: number;
  cz: number;
  radius: number;
}

export interface MapRendererState {
  layers: Layers;
  apiState: MapApiState;
  followTarget: FollowTarget;
  time: string;
  coords: string;
  status: string;
  dragging: boolean;

  svgWidth: number;
  svgHeight: number;
  camera: Camera;

  voronoiCells: VoronoiCellInfo[];
  biomeBorderPath: string;
  contourPath: string;
  staircases: StaircaseMapInfo[];
  houses: HouseMapInfo[];
  roadImageUrl: string | null;
  roadBounds: RoadBounds | null;
  terrainPaths: Array<{ color: string; d: string }>;

  onLayerToggle: (key: LayerKey, checked: boolean) => void;
  onSetFollow: (type: "player" | "npc", id: string) => void;
  onFitAll: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
}

export function useMapRenderer(svgRef: React.RefObject<SVGSVGElement>): MapRendererState {
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
  const [svgWidth, setSvgWidth] = useState(0);
  const [svgHeight, setSvgHeight] = useState(0);
  const [, setRenderKey] = useState(0);

  const [voronoiCells, setVoronoiCells] = useState<VoronoiCellInfo[]>([]);
  const [biomeBorderPath, setBiomeBorderPath] = useState("");
  const [contourPath, setContourPath] = useState("");
  const [staircases, setStaircases] = useState<StaircaseMapInfo[]>([]);
  const [houses, setHouses] = useState<HouseMapInfo[]>([]);
  const [roadImageUrl, setRoadImageUrl] = useState<string | null>(null);
  const [roadBounds, setRoadBounds] = useState<RoadBounds | null>(null);
  const [terrainPaths, setTerrainPaths] = useState<Array<{ color: string; d: string }>>([]);

  useEffect(() => {
    localStorage.setItem(LAYERS_STORAGE_KEY, JSON.stringify(layers));
  }, [layers]);

  const camera = useRef<Camera>({ x: 0, z: 0, pxPerBlock: 2 });
  const terrainData = useRef<ChunkTerrainInfo[]>([]);
  const apiStateRef = useRef<MapApiState>({ gameTicks: 0, players: [], npcs: [], weatherZones: [] });
  const followTargetRef = useRef<FollowTarget>(null);
  const autoFitDone = useRef(false);
  const housesFetchCenter = useRef({ x: NaN, z: NaN });
  const roadsFetchCenter = useRef({ x: NaN, z: NaN });
  const roadsFetching = useRef(false);
  const biomeFetchCenter = useRef({ x: NaN, z: NaN });
  const staircasesFetched = useRef(false);
  const isDragging = useRef(false);
  const dragLast = useRef({ x: 0, y: 0 });
  const rafPending = useRef(false);
  const roadObjUrl = useRef<string | null>(null);

  const fitAllRef = useRef<() => void>(() => {});
  const setFollowRef = useRef<(type: "player" | "npc", id: string) => void>(() => {});
  const zoomAtRef = useRef<(f: number, mx: number, mz: number) => void>(() => {});

  useEffect(() => {
    const svg = svgRef.current!;

    function canvasToWorld(cx: number, cz: number): [number, number] {
      const cam = camera.current;
      const W = svg.clientWidth,
        H = svg.clientHeight;
      return [(cx - W / 2) / cam.pxPerBlock + cam.x, -(cz - H / 2) / cam.pxPerBlock + cam.z];
    }


    function scheduleUpdate() {
      if (rafPending.current) return;
      rafPending.current = true;
      requestAnimationFrame(() => {
        rafPending.current = false;
        const ft = followTargetRef.current;
        if (ft) {
          const state = apiStateRef.current;
          const entity =
            ft.type === "player" ? state.players.find((p) => p.id === ft.id) : state.npcs.find((n) => n.id === ft.id);
          if (entity) {
            camera.current.x = entity.x;
            camera.current.z = entity.z;
          }
        }
        setRenderKey((k) => k + 1);
      });
    }

    function autoFitView() {
      const terrain = terrainData.current;
      const state = apiStateRef.current;
      const W = svg.clientWidth,
        H = svg.clientHeight;
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
      camera.current.pxPerBlock = Math.min(W / (maxX - minX + 40), H / (maxZ - minZ + 40));
    }

    zoomAtRef.current = (factor, mx, mz) => {
      const [wx, wz] = canvasToWorld(mx, mz);
      const W = svg.clientWidth,
        H = svg.clientHeight;
      camera.current.pxPerBlock = Math.max(0.05, Math.min(64, camera.current.pxPerBlock * factor));
      camera.current.x = wx - (mx - W / 2) / camera.current.pxPerBlock;
      camera.current.z = wz + (mz - H / 2) / camera.current.pxPerBlock;
      scheduleUpdate();
    };

    fitAllRef.current = () => {
      followTargetRef.current = null;
      setFollowTarget(null);
      autoFitView();
      scheduleUpdate();
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
        }
      }
      scheduleUpdate();
    };

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      if (followTargetRef.current) {
        followTargetRef.current = null;
        setFollowTarget(null);
      }
      const rect = svg.getBoundingClientRect();
      zoomAtRef.current(e.deltaY < 0 ? 1.25 : 0.8, e.clientX - rect.left, e.clientY - rect.top);
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
        scheduleUpdate();
      }
      const rect = svg.getBoundingClientRect();
      const [wx, wz] = canvasToWorld(e.clientX - rect.left, e.clientY - rect.top);
      setCoords(`x:${Math.round(wx)}  z:${Math.round(wz)}`);
    };

    const onMouseUp = () => {
      isDragging.current = false;
      setDragging(false);
    };

    async function pollState() {
      try {
        const r = await fetch("/api/map/state");
        if (r.ok) {
          const data: MapApiState = await r.json();
          apiStateRef.current = data;
          setApiState(data);
          scheduleUpdate();
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
          setContourPath(computeContourPath(terrainData.current));
          if (!autoFitDone.current && terrainData.current.length > 0) {
            autoFitDone.current = true;
            autoFitView();
            fetchHouses();
            fetchPreciseRoads();
            fetchStaircases();
          }
          setTerrainPaths(computeTerrainPaths(terrainData.current));
        }
      } catch {
        /* non-critical */
      }
    }

    async function fetchVoronoi() {
      try {
        const r = await fetch("/api/map/voronoi?cx=0&cz=0&radius=3200");
        if (r.ok) setVoronoiCells(await r.json());
      } catch {
        /* non-critical */
      }
    }

    async function fetchBiomeBorders() {
      const cx = Math.round(camera.current.x),
        cz = Math.round(camera.current.z);
      const { x, z } = biomeFetchCenter.current;
      if (!isNaN(x) && Math.hypot(cx - x, cz - z) < 300) return;
      biomeFetchCenter.current = { x: cx, z: cz };
      try {
        const r = await fetch(`/api/map/voronoi-borders?cx=${cx}&cz=${cz}&radius=2000`);
        if (r.ok) setBiomeBorderPath(computeBiomeBorderPath(await r.json()));
      } catch {
        /* non-critical */
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
        if (r.ok) setHouses(await r.json());
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
          if (roadObjUrl.current) URL.revokeObjectURL(roadObjUrl.current);
          roadObjUrl.current = url;
          setRoadImageUrl(url);
          setRoadBounds({ cx, cz, radius });
        }
      } catch {
        /* non-critical */
      } finally {
        roadsFetching.current = false;
      }
    }

    async function fetchStaircases() {
      if (staircasesFetched.current) return;
      staircasesFetched.current = true;
      try {
        const r = await fetch("/api/map/staircases");
        if (r.ok) setStaircases(await r.json());
      } catch {
        staircasesFetched.current = false;
      }
    }

    const ro = new ResizeObserver((entries) => {
      const { width, height } = entries[0].contentRect;
      setSvgWidth(width);
      setSvgHeight(height);
      scheduleUpdate();
    });
    const container = svg.parentElement!;
    ro.observe(container);

    const initW = container.clientWidth,
      initH = container.clientHeight;
    setSvgWidth(initW);
    setSvgHeight(initH);

    svg.addEventListener("wheel", onWheel, { passive: false });
    svg.addEventListener("mousedown", onMouseDown);
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);

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
      svg.removeEventListener("wheel", onWheel);
      svg.removeEventListener("mousedown", onMouseDown);
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
      ro.disconnect();
      intervals.forEach(clearInterval);
      if (roadObjUrl.current) URL.revokeObjectURL(roadObjUrl.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- svgRef is stable; mount-only setup
  }, []);

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
    const svg = svgRef.current!;
    zoomAtRef.current(1.5, svg.clientWidth / 2, svg.clientHeight / 2);
  }, [svgRef]);

  const onZoomOut = useCallback(() => {
    const svg = svgRef.current!;
    zoomAtRef.current(0.67, svg.clientWidth / 2, svg.clientHeight / 2);
  }, [svgRef]);

  return {
    layers,
    apiState,
    followTarget,
    time: ticksToTime(apiState.gameTicks),
    coords,
    status,
    dragging,
    svgWidth,
    svgHeight,
    camera: camera.current,
    voronoiCells,
    biomeBorderPath,
    contourPath,
    staircases,
    houses,
    roadImageUrl,
    roadBounds,
    terrainPaths,
    onLayerToggle,
    onSetFollow,
    onFitAll,
    onZoomIn,
    onZoomOut,
  };
}
