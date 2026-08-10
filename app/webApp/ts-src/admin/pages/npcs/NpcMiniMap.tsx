import { NpcAdminDto } from "../../api";
import { useEffect, useRef, useState } from "react";
import type { VoronoiCellInfo } from "../../../map/types";
import { AttackLine, PlayerAdminDto } from "./NpcsPage";
export const ATTACK_LINE_TTL = 700;

function typeColor(type: string): string {
  let h = 0;
  for (let i = 0; i < type.length; i++) h = (h * 31 + type.charCodeAt(i)) & 0xffff;
  return `hsl(${h % 360},65%,60%)`;
}

function arrowPoints(yaw: number): string {
  const adx = Math.sin(yaw),
    ady = -Math.cos(yaw);
  const perpX = -ady,
    perpY = adx;
  const len = 9,
    w = 4;
  return [
    `${adx * len},${ady * len}`,
    `${-adx * len * 0.35 + perpX * w},${-ady * len * 0.35 + perpY * w}`,
    `${-adx * len * 0.35 - perpX * w},${-ady * len * 0.35 - perpY * w}`,
  ].join(" ");
}
export interface Camera {
  x: number;
  z: number;
  pxPerBlock: number;
}

function w2s(wx: number, wz: number, cam: Camera, W: number, H: number): [number, number] {
  return [(wx - cam.x) * cam.pxPerBlock + W / 2, -(wz - cam.z) * cam.pxPerBlock + H / 2];
}

interface BorderSeg {
  x1: number;
  z1: number;
  x2: number;
  z2: number;
}

function buildVoronoiPolygonPaths(
  segs: BorderSeg[],
  cells: VoronoiCellInfo[],
): { name: string; color: string; path: string }[] {
  if (segs.length === 0 || cells.length === 0) return [];

  // Assign each segment to its 2 nearest cell centers (by midpoint proximity)
  const cellSegs = new Map<string, BorderSeg[]>();
  for (const cell of cells) cellSegs.set(cell.name, []);
  for (const seg of segs) {
    const mx = (seg.x1 + seg.x2) / 2;
    const mz = (seg.z1 + seg.z2) / 2;
    let best1 = "",
      best2 = "",
      d1 = Infinity,
      d2 = Infinity;
    for (const c of cells) {
      const d = (c.x - mx) ** 2 + (c.z - mz) ** 2;
      if (d < d1) {
        d2 = d1;
        best2 = best1;
        d1 = d;
        best1 = c.name;
      } else if (d < d2) {
        d2 = d;
        best2 = c.name;
      }
    }
    if (best1) cellSegs.get(best1)!.push(seg);
    if (best2) cellSegs.get(best2)!.push(seg);
  }

  const PREC = 100;
  const vkey = (x: number, z: number) => `${Math.round(x * PREC)},${Math.round(z * PREC)}`;

  const result: { name: string; color: string; path: string }[] = [];
  for (const cell of cells) {
    const cs = cellSegs.get(cell.name) ?? [];
    if (cs.length < 3) continue;

    const coords = new Map<string, [number, number]>();
    const adj = new Map<string, string[]>();
    const addEdge = (x1: number, z1: number, x2: number, z2: number) => {
      const k1 = vkey(x1, z1),
        k2 = vkey(x2, z2);
      coords.set(k1, [x1, z1]);
      coords.set(k2, [x2, z2]);
      if (!adj.has(k1)) adj.set(k1, []);
      if (!adj.has(k2)) adj.set(k2, []);
      if (!adj.get(k1)!.includes(k2)) adj.get(k1)!.push(k2);
      if (!adj.get(k2)!.includes(k1)) adj.get(k2)!.push(k1);
    };
    for (const s of cs) addEdge(s.x1, s.z1, s.x2, s.z2);

    const startKey = adj.keys().next().value!;
    const polygon: [number, number][] = [];
    const visited = new Set<string>();
    let curKey = startKey;
    let prevKey: string | null = null;
    while (!visited.has(curKey)) {
      visited.add(curKey);
      polygon.push(coords.get(curKey)!);
      const next = (adj.get(curKey) ?? []).find((k) => k !== prevKey);
      if (!next) break;
      prevKey = curKey;
      curKey = next;
    }

    if (polygon.length >= 3) {
      result.push({
        name: cell.name,
        color: cell.color,
        path: "M" + polygon.map(([x, z]) => `${x} ${z}`).join("L") + "Z",
      });
    }
  }
  return result;
}

export function NpcMiniMap({
  npcs,
  players,
  selectedId,
  attackLines,
}: {
  npcs: NpcAdminDto[];
  players: PlayerAdminDto[];
  selectedId: string | null;
  attackLines: AttackLine[];
}) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [camera, setCamera] = useState<Camera>({ x: 0, z: 0, pxPerBlock: 0.5 });
  const [dragging, setDragging] = useState(false);
  const [voronoiCells, setVoronoiCells] = useState<VoronoiCellInfo[]>([]);
  const [borderSegs, setBorderSegs] = useState<BorderSeg[]>([]);
  const [now, setNow] = useState(() => Date.now());
  const dragStart = useRef<{ mx: number; my: number; cx: number; cz: number } | null>(null);

  // Drive fade animation while there are active attack lines
  useEffect(() => {
    if (attackLines.length === 0) return;
    const id = setInterval(() => setNow(Date.now()), 40);
    return () => clearInterval(id);
  }, [attackLines.length]);

  useEffect(() => {
    fetch("/api/map/voronoi?cx=0&cz=0&radius=3200")
      .then((r) => (r.ok ? r.json() : []))
      .then(setVoronoiCells)
      .catch(() => {});
    fetch("/api/map/voronoi-borders?cx=0&cz=0&radius=3200")
      .then((r) => (r.ok ? r.json() : []))
      .then(setBorderSegs)
      .catch(() => {});
  }, []);

  const hasCenteredRef = useRef(false);
  const npcsMapRef = useRef(npcs);
  useEffect(() => {
    npcsMapRef.current = npcs;
  }, [npcs]);

  // Centre on the NPC barycentre for the first frame
  useEffect(() => {
    if (hasCenteredRef.current || npcs.length === 0) return;
    hasCenteredRef.current = true;
    const sx = npcs.reduce((a, n) => a + n.x, 0) / npcs.length;
    const sz = npcs.reduce((a, n) => a + n.z, 0) / npcs.length;
    setCamera((c) => ({ ...c, x: sx, z: sz }));
  }, [npcs]);

  // Re-centre on the selected NPC
  useEffect(() => {
    if (!selectedId) return;
    const npc = npcsMapRef.current.find((n) => n.id === selectedId);
    if (!npc) return;
    setCamera((c) => ({ ...c, x: npc.x, z: npc.z }));
  }, [selectedId]);

  const [svgDims, setSvgDims] = useState<[number, number]>([320, 500]);
  useEffect(() => {
    const el = svgRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setSvgDims([el.clientWidth, el.clientHeight]));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15;
    setCamera((c) => ({ ...c, pxPerBlock: Math.max(0.05, Math.min(20, c.pxPerBlock * factor)) }));
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    setDragging(true);
    dragStart.current = { mx: e.clientX, my: e.clientY, cx: camera.x, cz: camera.z };
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!dragging || !dragStart.current) return;
    const dx = e.clientX - dragStart.current.mx;
    const dy = e.clientY - dragStart.current.my;
    setCamera((c) => ({
      ...c,
      x: dragStart.current!.cx - dx / c.pxPerBlock,
      z: dragStart.current!.cz + dy / c.pxPerBlock,
    }));
  };

  const handleMouseUp = () => setDragging(false);

  const zoom = (factor: number) =>
    setCamera((c) => ({ ...c, pxPerBlock: Math.max(0.05, Math.min(20, c.pxPerBlock * factor)) }));

  const [W, H] = svgDims;
  const ppb = camera.pxPerBlock;
  const worldTransform = `matrix(${ppb},0,0,${-ppb},${W / 2 - camera.x * ppb},${H / 2 + camera.z * ppb})`;
  const voronoiPolygons = buildVoronoiPolygonPaths(borderSegs, voronoiCells);

  return (
    <div className="relative rounded-xl border border-[#2E3A4E] overflow-hidden bg-[#0E1726]" style={{ height: 500 }}>
      <svg
        ref={svgRef}
        width="100%"
        height="100%"
        style={{ display: "block", background: "#111", cursor: dragging ? "grabbing" : "grab" }}
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        {/* ── World-space group: Voronoi polygons + borders ── */}
        <g transform={worldTransform}>
          {voronoiPolygons.map((p, i) => (
            <path key={i} d={p.path} fill={p.color + "28"} stroke={p.color + "90"} strokeWidth={1 / ppb} />
          ))}
        </g>

        {/* Zone names (screen-space) */}
        {ppb >= 0.3 &&
          voronoiCells.map((cell, i) => {
            const [cx, cy] = w2s(cell.x, cell.z, camera, W, H);
            if (cx < -60 || cx > W + 60 || cy < -20 || cy > H + 20) return null;
            return (
              <text
                key={i}
                x={cx}
                y={cy}
                textAnchor="middle"
                dominantBaseline="middle"
                fill="rgba(255,255,255,0.4)"
                fontSize={10}
                fontFamily="serif"
              >
                {cell.name}
              </text>
            );
          })}

        {/* Attack lines */}
        {attackLines.map((line) => {
          const age = now - line.ts;
          const opacity = Math.max(0, 1 - age / ATTACK_LINE_TTL);
          if (opacity <= 0) return null;
          const [ax, ay] = w2s(line.ax, line.az, camera, W, H);
          const [bx, by] = w2s(line.bx, line.bz, camera, W, H);
          return (
            <g key={line.id} strokeOpacity={opacity}>
              <line x1={ax} y1={ay} x2={bx} y2={by} stroke="#ff4444" strokeWidth={3} strokeLinecap="round" />
              <line x1={ax} y1={ay} x2={bx} y2={by} stroke="#ffaaaa" strokeWidth={1} strokeLinecap="round" />
            </g>
          );
        })}

        {/* NPC arrows */}
        {npcs.map((npc) => {
          const [nx, ny] = w2s(npc.x, npc.z, camera, W, H);
          const sel = npc.id === selectedId;
          const color = sel ? "#ffcc44" : typeColor(npc.type);
          return (
            <g key={npc.id} transform={`translate(${nx},${ny})`}>
              {sel && (
                <circle cx={0} cy={0} r={13} fill="none" stroke="#ffcc44" strokeWidth={1.5} strokeDasharray="3 2" />
              )}
              <polygon
                points={arrowPoints(npc.yaw)}
                fill={color}
                stroke={sel ? "#000" : "rgba(0,0,0,0.5)"}
                strokeWidth={0.8}
              />
              {sel && (
                <text x={14} y={4} fill="#ffcc44" fontSize={11} fontFamily="monospace" fontWeight="bold">
                  {npc.name}
                </text>
              )}
            </g>
          );
        })}

        {/* Player markers — rendered above NPCs */}
        {players.map((p) => {
          const [px, py] = w2s(p.x, p.z, camera, W, H);
          const rad = 6;
          const yawRad = (p.yaw * Math.PI) / 180;
          const tx = Math.sin(yawRad) * (rad + 4);
          const ty = -Math.cos(yawRad) * (rad + 4);
          return (
            <g key={p.id} transform={`translate(${px},${py})`}>
              <circle cx={0} cy={0} r={rad + 3} fill="rgba(59,130,246,0.18)" stroke="#3b82f6" strokeWidth={1} />
              <circle cx={0} cy={0} r={rad} fill="#3b82f6" stroke="#fff" strokeWidth={1.2} />
              <line x1={0} y1={0} x2={tx} y2={ty} stroke="#fff" strokeWidth={1.5} strokeLinecap="round" />
              <text x={rad + 6} y={4} fill="#93c5fd" fontSize={11} fontFamily="monospace" fontWeight="bold">
                {p.name}
              </text>
            </g>
          );
        })}
      </svg>
      <div className="absolute bottom-2 right-2 flex flex-col gap-1">
        <button
          onClick={() => zoom(1.5)}
          className="w-7 h-7 rounded bg-[#1C2434] border border-[#2E3A4E] text-white text-sm font-bold hover:bg-[#2E3A4E] transition-colors leading-none"
        >
          +
        </button>
        <button
          onClick={() => zoom(1 / 1.5)}
          className="w-7 h-7 rounded bg-[#1C2434] border border-[#2E3A4E] text-white text-sm font-bold hover:bg-[#2E3A4E] transition-colors leading-none"
        >
          −
        </button>
      </div>
    </div>
  );
}
