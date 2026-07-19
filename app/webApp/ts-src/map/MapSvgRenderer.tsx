import type { Camera } from "./types";
import type { MapRendererState } from "./useMapRenderer";
import { VEGETATION_TINT, WEATHER_FILL, WEATHER_STROKE } from "./useMapRenderer";

function w2s(wx: number, wz: number, cam: Camera, W: number, H: number): [number, number] {
  return [(wx - cam.x) * cam.pxPerBlock + W / 2, -(wz - cam.z) * cam.pxPerBlock + H / 2];
}

function arrowPoints(yaw: number): string {
  const adx = Math.sin(yaw),
    ady = -Math.cos(yaw);
  const perpX = -ady,
    perpY = adx;
  const len = 10,
    w = 5;
  const tipX = adx * len,
    tipY = ady * len;
  const b1x = -adx * len * 0.4 + perpX * w,
    b1y = -ady * len * 0.4 + perpY * w;
  const b2x = -adx * len * 0.4 - perpX * w,
    b2y = -ady * len * 0.4 - perpY * w;
  return `${tipX},${tipY} ${b1x},${b1y} ${b2x},${b2y}`;
}

interface Props {
  renderer: MapRendererState;
  svgRef: React.RefObject<SVGSVGElement | null>;
}

export function MapSvgRenderer({ renderer, svgRef }: Props) {
  const { layers, apiState, followTarget: ft, camera: cam, svgWidth: W, svgHeight: H } = renderer;
  const ppb = cam.pxPerBlock;

  // World-space SVG transform: maps world (x, z) → screen with Z-flip (Z+ is up)
  const worldTransform = `matrix(${ppb},0,0,${-ppb},${W / 2 - cam.x * ppb},${H / 2 + cam.z * ppb})`;

  // Visible world extents
  const wLeft = cam.x - W / (2 * ppb);
  const wRight = cam.x + W / (2 * ppb);
  const wBottom = cam.z - H / (2 * ppb);
  const wTop = cam.z + H / (2 * ppb);

  // Grid lines (world coords)
  const gridStep = Math.pow(10, Math.ceil(Math.log10(80 / ppb)));
  const gridX: number[] = [];
  for (let gx = Math.ceil(wLeft / gridStep) * gridStep; gx <= wRight; gx += gridStep) gridX.push(gx);
  const gridZ: number[] = [];
  for (let gz = Math.ceil(wBottom / gridStep) * gridStep; gz <= wTop; gz += gridStep) gridZ.push(gz);

  // Road image: compute screen-space rect to avoid Z-flip on the bitmap
  let roadX = 0,
    roadY = 0,
    roadSize = 0;
  if (renderer.roadImageUrl && renderer.roadBounds) {
    const rb = renderer.roadBounds;
    [roadX, roadY] = w2s(rb.cx - rb.radius, rb.cz + rb.radius, cam, W, H);
    roadSize = 2 * rb.radius * ppb;
  }

  // Vegetation circle radius in world units
  const vegCells = renderer.voronoiCells;
  const vegR = vegCells.length > 0 ? Math.sqrt((3200 * 3200) / vegCells.length) * 0.65 : 0;

  return (
    <svg
      ref={svgRef}
      style={{
        position: "absolute",
        inset: 0,
        overflow: "hidden",
        background: "#111",
        cursor: renderer.dragging ? "grabbing" : "grab",
      }}
      width={W || undefined}
      height={H || undefined}
    >
      {/* ── World-space group: areas, paths, fills ─────────────── */}
      <g transform={worldTransform}>
        {/* Terrain blocks — one <path> per unique color */}
        {layers.chunks && renderer.terrainPaths.map(({ color, d }) => <path key={color} d={d} fill={color} />)}

        {/* Vegetation tint circles */}
        {layers.vegetation &&
          vegCells.map((cell) => {
            const tint = VEGETATION_TINT[cell.biome];
            if (!tint) return null;
            return <circle key={cell.name} cx={cell.x} cy={cell.z} r={vegR} fill={tint} />;
          })}

        {/* Biome borders */}
        {layers.voronoi && renderer.biomeBorderPath && (
          <path d={renderer.biomeBorderPath} stroke="rgba(128,0,0,0.85)" strokeWidth={1 / ppb} fill="none" />
        )}

        {/* Contour lines */}
        {layers.contours && renderer.contourPath && (
          <path d={renderer.contourPath} stroke="rgba(220,220,220,0.45)" strokeWidth={0.6 / ppb} fill="none" />
        )}

        {/* Houses */}
        {layers.houses &&
          renderer.houses.map((h, i) => (
            <rect
              key={i}
              x={h.x}
              y={h.z}
              width={h.width}
              height={h.depth}
              fill="rgba(255,200,100,0.32)"
              stroke="#c80"
              strokeWidth={1 / ppb}
            />
          ))}

        {/* Weather zone fills */}
        {layers.weather &&
          (apiState.weatherZones ?? []).map((z, i) => (
            <circle
              key={i}
              cx={z.cx}
              cy={z.cz}
              r={z.radius}
              fill={WEATHER_FILL[z.type] ?? "rgba(128,128,128,0.15)"}
              stroke={WEATHER_STROKE[z.type] ?? "rgba(128,128,128,0.5)"}
              strokeWidth={1 / ppb}
            />
          ))}

        {/* Grid lines */}
        {gridX.map((gx) => (
          <line
            key={`gx${gx}`}
            x1={gx}
            y1={wBottom}
            x2={gx}
            y2={wTop}
            stroke="rgba(255,255,255,0.06)"
            strokeWidth={0.5 / ppb}
          />
        ))}
        {gridZ.map((gz) => (
          <line
            key={`gz${gz}`}
            x1={wLeft}
            y1={gz}
            x2={wRight}
            y2={gz}
            stroke="rgba(255,255,255,0.06)"
            strokeWidth={0.5 / ppb}
          />
        ))}
      </g>

      {/* ── Screen-space: road raster (bypasses Z-flip) ──────── */}
      {layers["precise-roads"] && renderer.roadImageUrl && (
        <image
          href={renderer.roadImageUrl}
          x={roadX}
          y={roadY}
          width={roadSize}
          height={roadSize}
          style={{ imageRendering: "pixelated" }}
        />
      )}

      {/* ── Screen-space: grid coord labels ──────────────────── */}
      {gridX.map((gx) => {
        const [sx] = w2s(gx, 0, cam, W, H);
        return (
          <text key={`gxl${gx}`} x={sx + 2} y={10} fill="rgba(255,255,255,0.28)" fontSize={9} fontFamily="monospace">
            {Math.round(gx)}
          </text>
        );
      })}
      {gridZ.map((gz) => {
        const [, sy] = w2s(0, gz, cam, W, H);
        return (
          <text key={`gzl${gz}`} x={2} y={sy - 2} fill="rgba(255,255,255,0.28)" fontSize={9} fontFamily="monospace">
            {Math.round(gz)}
          </text>
        );
      })}

      {/* ── Screen-space: weather labels ─────────────────────── */}
      {layers.weather &&
        (apiState.weatherZones ?? []).map((z, i) => {
          const [sx, sy] = w2s(z.cx, z.cz, cam, W, H);
          return (
            <text key={`wl${i}`} x={sx - 12} y={sy + 3} fill="#ccc" fontSize={10} fontFamily="monospace">
              {z.type}
            </text>
          );
        })}

      {/* ── Screen-space: voronoi zone names ─────────────────── */}
      {layers["voronoi-names"] &&
        renderer.voronoiCells.map((cell) => {
          const [px, py] = w2s(cell.x, cell.z, cam, W, H);
          if (px < -100 || px > W + 100 || py < -100 || py > H + 100) return null;
          const nfs = Math.max(9, Math.min(15, ppb * 80));
          const bfs = Math.max(7, Math.min(10, ppb * 55));
          return (
            <g key={cell.name} transform={`translate(${px},${py})`}>
              <text
                textAnchor="middle"
                dominantBaseline="middle"
                fontSize={nfs}
                fontFamily="serif"
                fontWeight="bold"
                fill="rgba(0,0,0,0.7)"
                x={1}
                y={1}
              >
                {cell.name}
              </text>
              <text
                textAnchor="middle"
                dominantBaseline="middle"
                fontSize={nfs}
                fontFamily="serif"
                fontWeight="bold"
                fill="#fff"
              >
                {cell.name}
              </text>
              <text textAnchor="middle" fontSize={bfs} fontFamily="monospace" fill="rgba(0,0,0,0.55)" x={1} y={nfs + 2}>
                {cell.biome}
              </text>
              <text textAnchor="middle" fontSize={bfs} fontFamily="monospace" fill="rgba(200,220,255,0.85)" y={nfs + 1}>
                {cell.biome}
              </text>
              <text
                textAnchor="middle"
                fontSize={bfs}
                fontFamily="monospace"
                fill="rgba(0,0,0,0.55)"
                x={1}
                y={nfs * 2 + 3}
              >
                Lv {cell.level}
              </text>
              <text
                textAnchor="middle"
                fontSize={bfs}
                fontFamily="monospace"
                fill="rgba(255,210,80,0.9)"
                y={nfs * 2 + 2}
              >
                Lv {cell.level}
              </text>
            </g>
          );
        })}

      {/* ── Screen-space: NPCs ───────────────────────────────── */}
      {layers.npcs &&
        apiState.npcs.map((n) => {
          const [nx, ny] = w2s(n.x, n.z, cam, W, H);
          return (
            <g key={n.id}>
              {ft?.type === "npc" && ft.id === n.id && (
                <rect x={nx - 8} y={ny - 8} width={16} height={16} fill="none" stroke="#ffcc44" strokeWidth={2} />
              )}
              <circle cx={nx} cy={ny} r={5} fill="#fa6" />
              <text x={nx + 7} y={ny + 4} fill="#fa6" fontSize={10} fontFamily="monospace">
                {n.type}
              </text>
            </g>
          );
        })}

      {/* ── Screen-space: Players ────────────────────────────── */}
      {layers.players &&
        apiState.players.map((p) => {
          const [px, py] = w2s(p.x, p.z, cam, W, H);
          return (
            <g key={p.id}>
              {ft?.type === "player" && ft.id === p.id && (
                <rect x={px - 10} y={py - 10} width={20} height={20} fill="none" stroke="#44aaff" strokeWidth={2} />
              )}
              <g transform={`translate(${px},${py})`}>
                <polygon points={arrowPoints(p.yaw)} fill="#6af" stroke="#003366" strokeWidth={0.8} />
              </g>
              <text x={px + 9} y={py + 4} fill="#8cf" fontSize={11} fontFamily="monospace" fontWeight="bold">
                {p.name}
              </text>
            </g>
          );
        })}

      {/* ── Screen-space: Staircases ─────────────────────────── */}
      {layers.staircases &&
        renderer.staircases.map((s) => {
          const [sx, sy] = w2s(s.x, s.z, cam, W, H);
          const r = Math.max(4, Math.min(10, ppb * 3));
          return (
            <g key={s.name}>
              <circle cx={sx} cy={sy} r={r} fill="rgba(160,80,220,0.35)" stroke="#b060e0" strokeWidth={1.5} />
              {ppb >= 0.5 && (
                <text x={sx - 3} y={sy + 3} fill="#d090f0" fontSize={9} fontFamily="monospace">
                  ↑
                </text>
              )}
              {ppb >= 1.5 && (
                <>
                  <text
                    textAnchor="middle"
                    x={sx + 1}
                    y={sy - r - 4}
                    fill="rgba(0,0,0,0.6)"
                    fontSize={9}
                    fontFamily="serif"
                  >
                    {s.name}
                  </text>
                  <text textAnchor="middle" x={sx} y={sy - r - 5} fill="#e0b0ff" fontSize={9} fontFamily="serif">
                    {s.name}
                  </text>
                </>
              )}
            </g>
          );
        })}
    </svg>
  );
}
