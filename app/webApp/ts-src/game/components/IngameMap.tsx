import { useEffect, useRef } from "react";

interface VoronoiCell {
  x: number;
  z: number;
  biome: string;
  color: string;
  name: string;
  level: number;
}

interface BiomeBorderChunk {
  cx: number;
  cz: number;
  mask: boolean[];
}

interface Props {
  playerX?: number;
  playerZ?: number;
  layoutStyle?: React.CSSProperties;
}

const RADIUS = 800;
const CANVAS_SIZE = 512;

function parseColor(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff];
}

function renderBg(canvas: HTMLCanvasElement, cells: VoronoiCell[], cx: number, cz: number) {
  const ctx = canvas.getContext("2d");
  if (!ctx || cells.length === 0) return;
  const s = CANVAS_SIZE;
  const scale = s / (RADIUS * 2);
  const parsed = cells.map((c) => ({ ...c, rgb: parseColor(c.color) }));

  const imageData = ctx.createImageData(s, s);
  for (let py = 0; py < s; py++) {
    for (let px = 0; px < s; px++) {
      const wx = cx + (px - s / 2) / scale;
      const wz = cz + (py - s / 2) / scale;
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
      const i = (py * s + px) * 4;
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
    const px = Math.round(s / 2 + (cell.x - cx) * scale);
    const py = Math.round(s / 2 + (cell.z - cz) * scale);

    ctx.fillStyle = "rgba(0,0,0,0.75)";
    ctx.beginPath();
    ctx.arc(px, py, 3, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "rgba(255,255,255,0.95)";
    ctx.beginPath();
    ctx.arc(px, py, 2, 0, Math.PI * 2);
    ctx.fill();

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

function renderBorders(
  canvas: HTMLCanvasElement,
  borderData: BiomeBorderChunk[],
  roadImg: HTMLImageElement | null,
  fetchCx: number,
  fetchCz: number,
) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const s = CANVAS_SIZE;
  ctx.clearRect(0, 0, s, s);
  const scale = s / (RADIUS * 2);
  const pixSz = Math.max(1, Math.ceil(scale));

  // Voronoi border pixels
  ctx.fillStyle = "rgba(200,80,80,0.8)";
  for (const chunk of borderData) {
    for (let lx = 0; lx < 16; lx++) {
      for (let lz = 0; lz < 16; lz++) {
        if (!chunk.mask[lx * 16 + lz]) continue;
        const wx = chunk.cx * 16 + lx;
        const wz = chunk.cz * 16 + lz;
        const px = s / 2 + (wx - fetchCx) * scale;
        const pz = s / 2 + (wz - fetchCz) * scale;
        if (px < -pixSz || px > s + pixSz || pz < -pixSz || pz > s + pixSz) continue;
        ctx.fillRect(Math.round(px), Math.round(pz), pixSz, pixSz);
      }
    }
  }

  // Road raster — PNG has row 0 = highest Z, IngameMap has higher Z = lower canvas Y → flip vertically
  if (roadImg) {
    ctx.globalAlpha = 0.85;
    ctx.save();
    ctx.translate(0, s);
    ctx.scale(1, -1);
    ctx.drawImage(roadImg, 0, 0, s, s);
    ctx.restore();
    ctx.globalAlpha = 1.0;
  }
}

function renderOverlay(canvas: HTMLCanvasElement, px: number, pz: number, cx: number, cz: number) {
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const s = CANVAS_SIZE;
  ctx.clearRect(0, 0, s, s);
  const scale = s / (RADIUS * 2);
  const sx = Math.round(s / 2 + (px - cx) * scale);
  const sz = Math.round(s / 2 + (pz - cz) * scale);
  if (sx < -10 || sx > s + 10 || sz < -10 || sz > s + 10) return;
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
  const overlayRef = useRef<HTMLCanvasElement>(null);
  const cellsRef = useRef<VoronoiCell[]>([]);
  const borderDataRef = useRef<BiomeBorderChunk[]>([]);
  const roadImgRef = useRef<HTMLImageElement | null>(null);
  const fetchCenterRef = useRef({ x: NaN, z: NaN });
  const playerPosRef = useRef({ x: playerX, z: playerZ });

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

    fetch(`/api/map/voronoi?cx=${cx}&cz=${cz}&radius=${RADIUS}`)
      .then((r) => r.json())
      .then((data: VoronoiCell[]) => {
        cellsRef.current = data;
        const { x: fcx, z: fcz } = fetchCenterRef.current;
        if (bgRef.current) renderBg(bgRef.current, data, fcx, fcz);
        const { x: ppx, z: ppz } = playerPosRef.current;
        if (overlayRef.current) renderOverlay(overlayRef.current, ppx, ppz, fcx, fcz);
      })
      .catch(() => {});

    const roadFetch = fetch(`/api/map/road-raster.png?cx=${cx}&cz=${cz}&radius=${RADIUS}`)
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

    Promise.all([fetch(`/api/map/biome-borders?cx=${cx}&cz=${cz}&radius=${RADIUS}`).then((r) => r.json()), roadFetch])
      .then(([borders, roadImg]: [BiomeBorderChunk[], HTMLImageElement]) => {
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

  return (
    <div
      style={{
        ...layoutStyle,
        zIndex: 900,
        background: "rgba(0,0,0,0.7)",
        border: "1px solid rgba(255,255,255,0.2)",
        borderRadius: 4,
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        boxSizing: "border-box",
      }}
    >
      <div
        style={{
          color: "rgba(255,255,255,0.7)",
          font: "bold 9px monospace",
          padding: "2px 6px",
          flexShrink: 0,
          borderBottom: "1px solid rgba(255,255,255,0.1)",
        }}
      >
        BIOMES
      </div>
      <div style={{ flex: 1, position: "relative", overflow: "hidden" }}>
        <canvas
          ref={bgRef}
          width={CANVAS_SIZE}
          height={CANVAS_SIZE}
          style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
        />
        <canvas
          ref={bordersRef}
          width={CANVAS_SIZE}
          height={CANVAS_SIZE}
          style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
        />
        <canvas
          ref={overlayRef}
          width={CANVAS_SIZE}
          height={CANVAS_SIZE}
          style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
        />
      </div>
    </div>
  );
}
