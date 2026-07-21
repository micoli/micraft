import { useEffect, useLayoutEffect, useRef, useCallback, useState } from "react";

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
  playerYaw?: number;
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

function renderBg(
  canvas: HTMLCanvasElement,
  cells: VoronoiCell[],
  cx: number,
  cz: number,
  showNames: boolean,
  panX = 0,
  panZ = 0,
  zoom = 1,
) {
  const ctx = canvas.getContext("2d");
  if (!ctx || cells.length === 0) return;
  const w = canvas.width;
  const h = canvas.height;
  const viewCx = cx + panX;
  const viewCz = cz + panZ;
  const scale = (Math.min(w, h) / (RADIUS * 2)) * zoom;
  const parsed = cells.map((c) => ({ ...c, rgb: parseColor(c.color) }));

  const imageData = ctx.createImageData(w, h);
  for (let py = 0; py < h; py++) {
    for (let px = 0; px < w; px++) {
      const wx = viewCx + (px - w / 2) / scale;
      const wz = viewCz - (py - h / 2) / scale;
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
    const px = Math.round(w / 2 + (cell.x - viewCx) * scale);
    const py = Math.round(h / 2 - (cell.z - viewCz) * scale);

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
  panX = 0,
  panZ = 0,
  zoom = 1,
) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);
  const baseScale = Math.min(w, h) / (RADIUS * 2);
  const scale = baseScale * zoom;
  const viewCx = fetchCx + panX;
  const viewCz = fetchCz + panZ;

  // Voronoi border segments
  ctx.strokeStyle = "rgba(200,80,80,0.8)";
  ctx.lineWidth = Math.max(1, scale);
  ctx.beginPath();
  for (const seg of borderData) {
    const sx1 = w / 2 + (seg.x1 - viewCx) * scale;
    const sy1 = h / 2 - (seg.z1 - viewCz) * scale;
    const sx2 = w / 2 + (seg.x2 - viewCx) * scale;
    const sy2 = h / 2 - (seg.z2 - viewCz) * scale;
    if (sx1 < -1 || sx1 > w + 1 || sy1 < -1 || sy1 > h + 1) continue;
    ctx.moveTo(sx1, sy1);
    ctx.lineTo(sx2, sy2);
  }
  ctx.stroke();

  // Road raster — only draw when pan=0 and zoom=1 (raster is baked for fetch center)
  if (roadImg && panX === 0 && panZ === 0 && zoom === 1) {
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
  panX = 0,
  panZ = 0,
  zoom = 1,
  showNames = true,
) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);
  const scale = (Math.min(w, h) / (RADIUS * 2)) * zoom;
  const viewCx = cx + panX;
  const viewCz = cz + panZ;

  const visibleWeather = showWeather ? weatherZones : [];
  const visibleStaircases = showStaircases ? staircases : [];

  for (const z of visibleWeather) {
    const sx = w / 2 + (z.cx - viewCx) * scale;
    const sz = h / 2 - (z.cz - viewCz) * scale;
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
    const sx = Math.round(w / 2 + (s.x - viewCx) * scale);
    const sz = Math.round(h / 2 - (s.z - viewCz) * scale);
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
    if (showNames) {
      const text = s.name.replace(/^Staircase - /, "");
      ctx.fillStyle = "rgba(0,0,0,0.6)";
      ctx.fillText(text, sx + 1, sz - 10);
      ctx.fillStyle = "#e0b0ff";
      ctx.fillText(text, sx, sz - 11);
    }
    ctx.textAlign = "left";
  }
}

function renderOverlay(
  canvas: HTMLCanvasElement,
  px: number,
  pz: number,
  cx: number,
  cz: number,
  yaw: number,
  panX: number,
  panZ: number,
  zoom: number,
) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);
  const baseScale = Math.min(w, h) / RADIUS;
  const scale = baseScale * zoom;
  const viewCx = cx + panX;
  const viewCz = cz + panZ;
  const sx = Math.round(w / 2 + (px - viewCx) * scale);
  const sz = Math.round(h / 2 - (pz - viewCz) * scale);
  if (sx < -20 || sx > w + 20 || sz < -20 || sz > h + 20) return;

  const len = 10;
  const wing = 5;
  const yawRad = (yaw * Math.PI) / 180;
  const adx = Math.sin(yawRad);
  const ady = -Math.cos(yawRad);
  const perpX = -ady;
  const perpY = adx;

  ctx.save();
  ctx.translate(sx, sz);
  ctx.beginPath();
  ctx.moveTo(adx * len, ady * len);
  ctx.lineTo(-adx * len * 0.4 + perpX * wing, -ady * len * 0.4 + perpY * wing);
  ctx.lineTo(-adx * len * 0.15, -ady * len * 0.15);
  ctx.lineTo(-adx * len * 0.4 - perpX * wing, -ady * len * 0.4 - perpY * wing);
  ctx.closePath();
  ctx.fillStyle = "#f44";
  ctx.fill();
  ctx.strokeStyle = "#fff";
  ctx.lineWidth = 1.2;
  ctx.stroke();
  ctx.restore();
}

export function IngameMap({ playerX = 0, playerZ = 0, playerYaw = 0, layoutStyle }: Props) {
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
  const playerPosRef = useRef({ x: playerX, z: playerZ, yaw: playerYaw });
  const [layers, setLayers] = useState({
    biomes: true,
    biomeNames: true,
    borders: true,
    weather: true,
    staircases: true,
  });
  const layersRef = useRef(layers);
  useLayoutEffect(() => {
    layersRef.current = layers;
  });

  // Widget position (drag from toolbar)
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const widgetDragStart = useRef<{ mx: number; my: number; ox: number; oy: number } | null>(null);

  // Map pan/zoom (drag on map canvas, wheel)
  const panRef = useRef({ x: 0, z: 0 }); // world offset from player
  const zoomRef = useRef(1);
  const mapDragStart = useRef<{ mx: number; my: number; px: number; pz: number } | null>(null);
  const [mapDragging, setMapDragging] = useState(false);

  const onToolbarMouseDown = useCallback(
    (e: React.MouseEvent) => {
      const tag = (e.target as HTMLElement).tagName;
      if (tag === "INPUT" || tag === "LABEL") return;
      widgetDragStart.current = { mx: e.clientX, my: e.clientY, ox: offset.x, oy: offset.y };
      const onMove = (ev: MouseEvent) => {
        if (!widgetDragStart.current) return;
        setOffset({
          x: widgetDragStart.current.ox + ev.clientX - widgetDragStart.current.mx,
          y: widgetDragStart.current.oy + ev.clientY - widgetDragStart.current.my,
        });
      };
      const onUp = () => {
        widgetDragStart.current = null;
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
    const { x: ppx, z: ppz, yaw } = playerPosRef.current;
    const { x: panX, z: panZ } = panRef.current;
    const zoom = zoomRef.current;
    const L = layersRef.current;
    if (bgRef.current) {
      bgRef.current.style.display = L.biomes ? "" : "none";
      renderBg(bgRef.current, cellsRef.current, fcx, fcz, L.biomeNames, panX, panZ, zoom);
    }
    if (bordersRef.current) {
      bordersRef.current.style.display = L.borders ? "" : "none";
      renderBorders(bordersRef.current, borderDataRef.current, roadImgRef.current, fcx, fcz, panX, panZ, zoom);
    }
    if (poiRef.current)
      renderPoi(
        poiRef.current,
        staircasesRef.current,
        weatherZonesRef.current,
        fcx,
        fcz,
        L.weather,
        L.staircases,
        panX,
        panZ,
        zoom,
        L.biomeNames,
      );
    if (overlayRef.current) renderOverlay(overlayRef.current, ppx, ppz, fcx, fcz, yaw, panX, panZ, zoom);
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

  const adjustZoom = useCallback(
    (factor: number) => {
      zoomRef.current = Math.max(0.25, Math.min(8, zoomRef.current * factor));
      rerender();
    },
    [rerender],
  );

  // Map pan (left-drag on canvas)
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const onMouseDown = (e: MouseEvent) => {
      if (e.button !== 0) return;
      mapDragStart.current = { mx: e.clientX, my: e.clientY, px: panRef.current.x, pz: panRef.current.z };
      setMapDragging(true);
    };

    const onMouseMove = (e: MouseEvent) => {
      if (!mapDragStart.current) return;
      const canvas = overlayRef.current;
      if (!canvas) return;
      const canvasSize = Math.min(canvas.width, canvas.height);
      const baseScale = canvasSize / RADIUS;
      const scale = baseScale * zoomRef.current;
      const dx = e.clientX - mapDragStart.current.mx;
      const dy = e.clientY - mapDragStart.current.my;
      panRef.current = {
        x: mapDragStart.current.px - dx / scale,
        z: mapDragStart.current.pz + dy / scale,
      };
      rerender();
    };

    const onMouseUp = () => {
      mapDragStart.current = null;
      setMapDragging(false);
    };

    const onDblClick = () => {
      panRef.current = { x: 0, z: 0 };
      zoomRef.current = 1;
      rerender();
    };

    container.addEventListener("mousedown", onMouseDown);
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    container.addEventListener("dblclick", onDblClick);

    return () => {
      container.removeEventListener("mousedown", onMouseDown);
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
      container.removeEventListener("dblclick", onDblClick);
    };
  }, [rerender]);

  useEffect(() => {
    playerPosRef.current = { x: playerX, z: playerZ, yaw: playerYaw };
    const fc = fetchCenterRef.current;
    const { x: panX, z: panZ } = panRef.current;
    const zoom = zoomRef.current;

    if (overlayRef.current) {
      renderOverlay(overlayRef.current, playerX, playerZ, fc.x, fc.z, playerYaw, panX, panZ, zoom);
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
        const { x: px2, z: pz2 } = panRef.current;
        const z2 = zoomRef.current;
        if (poiRef.current)
          renderPoi(poiRef.current, data, weatherZonesRef.current, fcx, fcz, L.weather, L.staircases, px2, pz2, z2, L.biomeNames);
      })
      .catch(() => {});

    fetch("/api/map/state")
      .then((r) => r.json())
      .then((data: { weatherZones?: WeatherZoneInfo[] }) => {
        weatherZonesRef.current = data.weatherZones ?? [];
        const { x: fcx, z: fcz } = fetchCenterRef.current;
        const L = layersRef.current;
        const { x: px2, z: pz2 } = panRef.current;
        const z2 = zoomRef.current;
        if (poiRef.current)
          renderPoi(
            poiRef.current,
            staircasesRef.current,
            weatherZonesRef.current,
            fcx,
            fcz,
            L.weather,
            L.staircases,
            px2,
            pz2,
            z2,
            L.biomeNames,
          );
      })
      .catch(() => {});

    fetch(`/api/map/voronoi?cx=${cx}&cz=${cz}&radius=${RADIUS * 2}`)
      .then((r) => r.json())
      .then((data: VoronoiCell[]) => {
        cellsRef.current = data;
        const { x: fcx, z: fcz } = fetchCenterRef.current;
        const { x: px2, z: pz2 } = panRef.current;
        const z2 = zoomRef.current;
        if (bgRef.current) renderBg(bgRef.current, data, fcx, fcz, layersRef.current.biomeNames, px2, pz2, z2);
        const { x: ppx, z: ppz, yaw } = playerPosRef.current;
        if (overlayRef.current) renderOverlay(overlayRef.current, ppx, ppz, fcx, fcz, yaw, px2, pz2, z2);
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
        const { x: px2, z: pz2 } = panRef.current;
        const z2 = zoomRef.current;
        if (bordersRef.current) renderBorders(bordersRef.current, borders, roadImg, fcx, fcz, px2, pz2, z2);
      })
      .catch(() => {
        const { x: fcx, z: fcz } = fetchCenterRef.current;
        const { x: px2, z: pz2 } = panRef.current;
        const z2 = zoomRef.current;
        if (bordersRef.current)
          renderBorders(bordersRef.current, borderDataRef.current, roadImgRef.current, fcx, fcz, px2, pz2, z2);
      });
  }, [playerX, playerZ, playerYaw]);

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
      <div
        ref={containerRef}
        style={{ flex: 1, position: "relative", overflow: "hidden", cursor: mapDragging ? "grabbing" : "grab" }}
      >
        <canvas ref={bgRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
        <canvas ref={bordersRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
        <canvas ref={poiRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
        <canvas ref={overlayRef} style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} />
        <div
          style={{
            position: "absolute",
            top: 6,
            right: 6,
            display: "flex",
            flexDirection: "column",
            gap: 3,
            zIndex: 10,
          }}
        >
          {[
            ["+", 1.2],
            ["−", 1 / 1.2],
          ].map(([label, factor]) => (
            <button
              key={label as string}
              onMouseDown={(e) => e.stopPropagation()}
              onClick={() => adjustZoom(factor as number)}
              style={{
                width: 22,
                height: 22,
                background: "rgba(0,0,0,0.6)",
                border: "1px solid rgba(255,255,255,0.3)",
                borderRadius: 4,
                color: "rgba(255,255,255,0.9)",
                fontSize: 14,
                lineHeight: 1,
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                padding: 0,
              }}
            >
              {label as string}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
