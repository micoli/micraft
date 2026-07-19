import { useEffect, useRef, useCallback, useState } from "react";

interface VoronoiCell {
  x: number;
  z: number;
  biome: string;
  color: string;
  name: string;
  level: number;
}

interface BiomeBorderSegment {
  x1: number;
  z1: number;
  x2: number;
  z2: number;
}

interface WeatherZoneInfo {
  id: string;
  type: string;
  cx: number;
  cz: number;
  radius: number;
  intensity: number;
}

interface StaircaseMapInfo {
  name: string;
  x: number;
  z: number;
}

interface Props {
  playerX?: number;
  playerZ?: number;
  layoutStyle?: React.CSSProperties;
}

const RADIUS = 800;

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

function parseColor(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff];
}

function renderBg(canvas: HTMLCanvasElement, cells: VoronoiCell[], cx: number, cz: number, showNames: boolean) {
  const ctx = canvas.getContext("2d");
  if (!ctx || cells.length === 0) return;
  const w = canvas.width;
  const h = canvas.height;
  const scale = Math.min(w, h) / (RADIUS * 2);
  const parsed = cells.map((c) => ({ ...c, rgb: parseColor(c.color) }));

  const imageData = ctx.createImageData(w, h);
  for (let py = 0; py < h; py++) {
    for (let px = 0; px < w; px++) {
      const wx = cx + (px - w / 2) / scale;
      const wz = cz - (py - h / 2) / scale;
      let minD = Infinity;
      let r = 128,
        g = 128,
        b = 128;
      for (const cell of parsed) {
        const dx = cell.x - wx;
        const dz = cell.z - wz;
        const d = dx * dx + dz * dz;
        if (d < minD) {
          minD = d;
          [r, g, b] = cell.rgb;
        }
      }
      const i = (py * w + px) * 4;
      imageData.data[i] = r;
      imageData.data[i + 1] = g;
      imageData.data[i + 2] = b;
      imageData.data[i + 3] = 255;
    }
  }
  ctx.putImageData(imageData, 0, 0);

  ctx.textAlign = "center";
  ctx.textBaseline = "top";
  for (const cell of cells) {
    const px = Math.round(w / 2 + (cell.x - cx) * scale);
    const py = Math.round(h / 2 - (cell.z - cz) * scale);

    ctx.fillStyle = "rgba(0,0,0,0.75)";
    ctx.beginPath();
    ctx.arc(px, py, 3, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "rgba(255,255,255,0.95)";
    ctx.beginPath();
    ctx.arc(px, py, 2, 0, Math.PI * 2);
    ctx.fill();

    if (showNames) {
      const label = cell.name;
      ctx.font = "bold 10px monospace";
      ctx.fillStyle = "rgba(0,0,0,0.8)";
      ctx.fillText(label, px + 1, py + 5);
      ctx.fillStyle = "#fff";
      ctx.fillText(label, px, py + 4);
      ctx.font = "8px monospace";
      ctx.fillStyle = "rgba(0,0,0,0.7)";
      ctx.fillText("Lv " + cell.level, px + 1, py + 17);
      ctx.fillStyle = "rgba(255,210,80,0.95)";
      ctx.fillText("Lv " + cell.level, px, py + 16);
    }
  }
}

function renderBorders(
  canvas: HTMLCanvasElement,
  borderData: BiomeBorderSegment[],
  roadImg: HTMLImageElement | null,
  fetchCx: number,
  fetchCz: number,
) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);
  const scale = Math.min(w, h) / (RADIUS * 2);
  const pixSz = Math.max(1, Math.ceil(scale));

  // Voronoi border segments
  ctx.strokeStyle = "rgba(200,80,80,0.8)";
  ctx.lineWidth = Math.max(1, scale);
  ctx.beginPath();
  for (const seg of borderData) {
    const sx1 = w / 2 + (seg.x1 - fetchCx) * scale;
    const sy1 = h / 2 - (seg.z1 - fetchCz) * scale;
    const sx2 = w / 2 + (seg.x2 - fetchCx) * scale;
    const sy2 = h / 2 - (seg.z2 - fetchCz) * scale;
    if (sx1 < -1 || sx1 > w + 1 || sy1 < -1 || sy1 > h + 1) continue;
    ctx.moveTo(sx1, sy1);
    ctx.lineTo(sx2, sy2);
  }
  ctx.stroke();

  // Road raster — PNG row 0 = highest Z = top of canvas (north up), no flip needed
  if (roadImg) {
    ctx.globalAlpha = 0.85;
    ctx.drawImage(roadImg, 0, 0, w, h);
    ctx.globalAlpha = 1.0;
  }
}

function renderPoi(
  canvas: HTMLCanvasElement,
  staircases: StaircaseMapInfo[],
  weatherZones: WeatherZoneInfo[],
  cx: number,
  cz: number,
  showWeather = true,
  showStaircases = true,
) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);
  const scale = Math.min(w, h) / (RADIUS * 2);

  const visibleWeather = showWeather ? weatherZones : [];
  const visibleStaircases = showStaircases ? staircases : [];

  for (const z of visibleWeather) {
    const sx = w / 2 + (z.cx - cx) * scale;
    const sz = h / 2 - (z.cz - cz) * scale;
    const rPx = z.radius * scale;
    ctx.beginPath();
    ctx.arc(sx, sz, rPx, 0, Math.PI * 2);
    ctx.fillStyle = WEATHER_FILL[z.type] ?? "rgba(128,128,128,0.15)";
    ctx.fill();
    ctx.strokeStyle = WEATHER_STROKE[z.type] ?? "rgba(128,128,128,0.5)";
    ctx.lineWidth = 1;
    ctx.stroke();
    ctx.fillStyle = "rgba(255,255,255,0.85)";
    ctx.font = "bold 9px monospace";
    ctx.textAlign = "center";
    ctx.fillText(z.type, sx, sz + 3);
    ctx.textAlign = "left";
  }

  for (const s of visibleStaircases) {
    const sx = Math.round(w / 2 + (s.x - cx) * scale);
    const sz = Math.round(h / 2 - (s.z - cz) * scale);
    if (sx < -20 || sx > w + 20 || sz < -20 || sz > h + 20) continue;
    ctx.beginPath();
    ctx.arc(sx, sz, 5, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(160,80,220,0.35)";
    ctx.fill();
    ctx.strokeStyle = "#b060e0";
    ctx.lineWidth = 1.5;
    ctx.stroke();
    ctx.fillStyle = "#e0b0ff";
    ctx.font = "bold 9px monospace";
    ctx.textAlign = "center";
    ctx.fillText("↑", sx, sz + 3);
    ctx.fillStyle = "rgba(0,0,0,0.6)";
    const text = s.name.replace(/^Staircase - /, "");
    ctx.fillText(text, sx + 1, sz - 10);
    ctx.fillStyle = "#e0b0ff";
    ctx.fillText(text, sx, sz - 11);
    ctx.textAlign = "left";
  }
}

function renderOverlay(canvas: HTMLCanvasElement, px: number, pz: number, cx: number, cz: number) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);
  const scale = Math.min(w, h) / RADIUS;
  const sx = Math.round(w / 2 + (px - cx) * scale);
  const sz = Math.round(h / 2 - (pz - cz) * scale);
  if (sx < -10 || sx > w + 10 || sz < -10 || sz > h + 10) return;
  ctx.fillStyle = "#f44";
  ctx.beginPath();
  ctx.arc(sx, sz, 5, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = "#fff";
  ctx.lineWidth = 1.5;
  ctx.stroke();
}

export function IngameMap({ playerX = 0, playerZ = 0, layoutStyle }: Props) {
  const bgRef = useRef<HTMLCanvasElement>(null);
  const bordersRef = useRef<HTMLCanvasElement>(null);
  const poiRef = useRef<HTMLCanvasElement>(null);
  const overlayRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const cellsRef = useRef<VoronoiCell[]>([]);
  const borderDataRef = useRef<BiomeBorderSegment[]>([]);
  const roadImgRef = useRef<HTMLImageElement | null>(null);
  const staircasesRef = useRef<StaircaseMapInfo[]>([]);
  const weatherZonesRef = useRef<WeatherZoneInfo[]>([]);
  const fetchCenterRef = useRef({ x: NaN, z: NaN });
  const playerPosRef = useRef({ x: playerX, z: playerZ });
  const [layers, setLayers] = useState({
    biomes: true,
    biomeNames: true,
    borders: true,
    weather: true,
    staircases: true,
  });
  const layersRef = useRef(layers);
  layersRef.current = layers;

  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const dragStart = useRef<{ mx: number; my: number; ox: number; oy: number } | null>(null);

  const onToolbarMouseDown = useCallback(
    (e: React.MouseEvent) => {
      const tag = (e.target as HTMLElement).tagName;
      if (tag === "INPUT" || tag === "LABEL") return;
      dragStart.current = { mx: e.clientX, my: e.clientY, ox: offset.x, oy: offset.y };
      const onMove = (ev: MouseEvent) => {
        if (!dragStart.current) return;
        setOffset({
          x: dragStart.current.ox + ev.clientX - dragStart.current.mx,
          y: dragStart.current.oy + ev.clientY - dragStart.current.my,
        });
      };
      const onUp = () => {
        dragStart.current = null;
        window.removeEventListener("mousemove", onMove);
        window.removeEventListener("mouseup", onUp);
      };
      window.addEventListener("mousemove", onMove);
      window.addEventListener("mouseup", onUp);
    },
    [offset],
  );

  const rerender = useCallback(() => {
    const { x: fcx, z: fcz } = fetchCenterRef.current;
    const { x: ppx, z: ppz } = playerPosRef.current;
    const L = layersRef.current;
    if (bgRef.current) {
      bgRef.current.style.display = L.biomes ? "" : "none";
      renderBg(bgRef.current, cellsRef.current, fcx, fcz, L.biomeNames);
    }
    if (bordersRef.current) {
      bordersRef.current.style.display = L.borders ? "" : "none";
      renderBorders(bordersRef.current, borderDataRef.current, roadImgRef.current, fcx, fcz);
    }
    if (poiRef.current)
      renderPoi(poiRef.current, staircasesRef.current, weatherZonesRef.current, fcx, fcz, L.weather, L.staircases);
    if (overlayRef.current) renderOverlay(overlayRef.current, ppx, ppz, fcx, fcz);
  }, []);

  useEffect(() => {
    rerender();
  }, [layers, rerender]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const observer = new ResizeObserver((entries) => {
      const { width, height } = entries[0].contentRect;
      const w = Math.round(width);
      const h = Math.round(height);
      if (w === 0 || h === 0) return;
      for (const canvas of [bgRef.current, bordersRef.current, poiRef.current, overlayRef.current]) {
        if (canvas && (canvas.width !== w || canvas.height !== h)) {
          canvas.width = w;
          canvas.height = h;
        }
      }
      rerender();
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, [rerender]);

  useEffect(() => {
    playerPosRef.current = { x: playerX, z: playerZ };
    const fc = fetchCenterRef.current;

    if (overlayRef.current) {
      renderOverlay(overlayRef.current, playerX, playerZ, fc.x, fc.z);
    }

    const dx = playerX - fc.x;
    const dz = playerZ - fc.z;
    if (!isNaN(fc.x) && Math.abs(dx) < 100 && Math.abs(dz) < 100) return;

    fetchCenterRef.current = { x: playerX, z: playerZ };
    const cx = Math.round(playerX);
    const cz = Math.round(playerZ);

    fetch("/api/map/staircases")
      .then((r) => r.json())
      .then((data: StaircaseMapInfo[]) => {
        staircasesRef.current = data;
        const { x: fcx, z: fcz } = fetchCenterRef.current;
        const L = layersRef.current;
        if (poiRef.current) renderPoi(poiRef.current, data, weatherZonesRef.current, fcx, fcz, L.weather, L.staircases);
      })
      .catch(() => {});

    fetch("/api/map/state")
      .then((r) => r.json())
      .then((data: { weatherZones?: WeatherZoneInfo[] }) => {
        weatherZonesRef.current = data.weatherZones ?? [];
        const { x: fcx, z: fcz } = fetchCenterRef.current;
        const L = layersRef.current;
        if (poiRef.current)
          renderPoi(poiRef.current, staircasesRef.current, weatherZonesRef.current, fcx, fcz, L.weather, L.staircases);
      })
      .catch(() => {});

    fetch(`/api/map/voronoi?cx=${cx}&cz=${cz}&radius=${RADIUS * 2}`)
      .then((r) => r.json())
      .then((data: VoronoiCell[]) => {
        cellsRef.current = data;
        const { x: fcx, z: fcz } = fetchCenterRef.current;
        if (bgRef.current) renderBg(bgRef.current, data, fcx, fcz);
        const { x: ppx, z: ppz } = playerPosRef.current;
        if (overlayRef.current) renderOverlay(overlayRef.current, ppx, ppz, fcx, fcz);
      })
      .catch(() => {});

    const roadFetch = fetch(`/api/map/road-raster.png?cx=${cx}&cz=${cz}&radius=${RADIUS * 2}`)
      .then((r) => r.blob())
      .then(
        (blob) =>
          new Promise<HTMLImageElement>((resolve, reject) => {
            const url = URL.createObjectURL(blob);
            const img = new Image();
            img.onload = () => {
              URL.revokeObjectURL(url);
              resolve(img);
            };
            img.onerror = () => {
              URL.revokeObjectURL(url);
              reject(new Error("road img load failed"));
            };
            img.src = url;
          }),
      );

    Promise.all([
      fetch(`/api/map/voronoi-borders?cx=${cx}&cz=${cz}&radius=${RADIUS * 2}`).then((r) => r.json()),
      roadFetch,
    ])
      .then(([borders, roadImg]: [BiomeBorderSegment[], HTMLImageElement]) => {
        borderDataRef.current = borders;
        roadImgRef.current = roadImg;
        const { x: fcx, z: fcz } = fetchCenterRef.current;
        if (bordersRef.current) renderBorders(bordersRef.current, borders, roadImg, fcx, fcz);
      })
      .catch(() => {
        // render borders alone if road fetch failed
        const { x: fcx, z: fcz } = fetchCenterRef.current;
        if (bordersRef.current) renderBorders(bordersRef.current, borderDataRef.current, roadImgRef.current, fcx, fcz);
      });
  }, [playerX, playerZ]);

  const toggle = (key: keyof typeof layers) => setLayers((prev) => ({ ...prev, [key]: !prev[key] }));

  return (
    <div
      style={{
        ...layoutStyle,
        transform: `translate(${offset.x}px, ${offset.y}px)`,
        zIndex: 900,
        background: "rgba(0,0,0,0.7)",
        border: "3px solid rgba(128,128,128,0.8)",
        borderRadius: 10,
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        boxSizing: "border-box",
      }}
    >
      <div
        onMouseDown={onToolbarMouseDown}
        style={{
          display: "flex",
          gap: 10,
          padding: "2px 6px",
          fontSize: 10,
          color: "rgba(255,255,255,0.75)",
          borderBottom: "1px solid rgba(255,255,255,0.1)",
          flexShrink: 0,
          flexWrap: "wrap",
          cursor: "grab",
          userSelect: "none",
        }}
      >
        {(
          [
            ["biomes", "Biomes"],
            ["biomeNames", "Names"],
            ["borders", "Borders"],
            ["weather", "Weather"],
            ["staircases", "Stairs"],
          ] as [keyof typeof layers, string][]
        ).map(([key, label]) => (
          <label
            key={key}
            style={{ display: "flex", alignItems: "center", gap: 3, cursor: "pointer", userSelect: "none" }}
          >
            <input type="checkbox" checked={layers[key]} onChange={() => toggle(key)} style={{ cursor: "pointer" }} />
            {label}
          </label>
        ))}
      </div>
      <div ref={containerRef} style={{ flex: 1, position: "relative", overflow: "hidden" }}>
        <canvas ref={bgRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
        <canvas ref={bordersRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
        <canvas ref={poiRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
        <canvas ref={overlayRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
      </div>
    </div>
  );
}
