import { useEffect, useMemo, useRef, useState } from "react";
import { api, NpcAdminDto } from "../api";
import type { VoronoiCellInfo } from "../../map/types";

interface Camera {
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

interface AttackLine {
  id: string;
  ax: number;
  az: number;
  bx: number;
  bz: number;
  ts: number;
}

const ATTACK_LINE_TTL = 700;

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

function NpcMiniMap({
  npcs,
  selectedId,
  attackLines,
}: {
  npcs: NpcAdminDto[];
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

  // Centre initial sur le barycentre des NPCs
  useEffect(() => {
    if (hasCenteredRef.current || npcs.length === 0) return;
    hasCenteredRef.current = true;
    const sx = npcs.reduce((a, n) => a + n.x, 0) / npcs.length;
    const sz = npcs.reduce((a, n) => a + n.z, 0) / npcs.length;
    setCamera((c) => ({ ...c, x: sx, z: sz }));
  }, [npcs]);

  // Recentre sur le NPC sélectionné
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

function Badge({ label, color }: { label: string; color: string }) {
  return (
    <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide ${color}`}>
      {label}
    </span>
  );
}

function tierColor(tier: string) {
  switch (tier) {
    case "ELITE":
      return "bg-purple-900/60 text-purple-300";
    case "BOSS":
      return "bg-red-900/60 text-red-300";
    case "RARE":
      return "bg-yellow-900/60 text-yellow-300";
    default:
      return "bg-[#2E3A4E] text-[#8A99AF]";
  }
}

function aggroColor(mode: string) {
  switch (mode) {
    case "AGGRESSIVE":
      return "bg-red-900/40 text-red-400";
    case "PASSIVE_COOPERATIVE":
      return "bg-orange-900/40 text-orange-400";
    default:
      return "bg-[#1C2434] text-[#8A99AF]";
  }
}

function HpBar({ current, max }: { current: number; max: number }) {
  const pct = max > 0 ? Math.round((current / max) * 100) : 0;
  const color = pct > 50 ? "bg-emerald-500" : pct > 25 ? "bg-yellow-500" : "bg-red-500";
  return (
    <div className="flex items-center gap-2 min-w-[80px]">
      <div className="flex-1 h-1.5 bg-[#2E3A4E] rounded-full overflow-hidden">
        <div className={`h-full ${color} rounded-full`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-[10px] text-[#8A99AF] shrink-0">
        {current}/{max}
      </span>
    </div>
  );
}

function Detail({ npc }: { npc: NpcAdminDto }) {
  const teleport = `/teleport ${Math.round(npc.x)} ${Math.round(npc.y)} ${Math.round(npc.z)}`;
  return (
    <div className="bg-[#0E1726] border-t border-[#2E3A4E] px-6 py-4 grid grid-cols-2 gap-x-8 gap-y-3 text-sm">
      <div>
        <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-1">Position</p>
        <code className="text-xs text-emerald-400 font-mono select-all">{teleport}</code>
        <p className="text-[10px] text-[#8A99AF] mt-0.5">
          x={npc.x.toFixed(1)} y={npc.y.toFixed(1)} z={npc.z.toFixed(1)} · zone {npc.zone}
        </p>
      </div>

      {npc.skills.length > 0 && (
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-1">Skills</p>
          <div className="flex flex-wrap gap-1">
            {npc.skills.map((s) => (
              <span key={s} className="px-2 py-0.5 rounded bg-[#1C2434] text-[11px] text-[#8A99AF]">
                {s}
              </span>
            ))}
          </div>
        </div>
      )}

      {npc.parentIds.length > 0 && (
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-1">Parents</p>
          <div className="flex flex-col gap-0.5">
            {npc.parentIds.map((pid) => (
              <span key={pid} className="text-[11px] font-mono text-[#8A99AF]">
                {pid.slice(0, 8)}…
              </span>
            ))}
          </div>
        </div>
      )}

      {npc.ageGameDays != null && (
        <div className="col-span-2">
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-2">Animal State</p>
          <div className="grid grid-cols-2 gap-x-8 gap-y-1.5">
            <div className="flex items-center gap-2">
              <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">Age</span>
              <span className="text-xs text-white">{npc.ageGameDays.toFixed(1)} game days</span>
            </div>
            {npc.hunger != null && (
              <div className="flex items-center gap-2">
                <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">Hunger</span>
                <div className="flex items-center gap-1.5 flex-1">
                  <div className="flex-1 h-1.5 bg-[#2E3A4E] rounded-full overflow-hidden max-w-[80px]">
                    <div
                      className={`h-full rounded-full ${npc.hunger > 0.6 ? "bg-emerald-500" : npc.hunger > 0.3 ? "bg-yellow-500" : "bg-red-500"}`}
                      style={{ width: `${Math.round(npc.hunger * 100)}%` }}
                    />
                  </div>
                  <span className="text-[10px] text-[#8A99AF]">{Math.round(npc.hunger * 100)}%</span>
                </div>
              </div>
            )}
            {npc.motherLevel != null && npc.motherLevel > 0 && (
              <div className="flex items-center gap-2">
                <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">Mother level</span>
                <span className="text-xs text-white">{npc.motherLevel}</span>
              </div>
            )}
            {npc.gestationRemainingDays != null && (
              <div className="flex items-center gap-2">
                <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">Gestation</span>
                <span className="text-xs text-amber-400">{npc.gestationRemainingDays.toFixed(1)} days left</span>
              </div>
            )}
            {npc.lastReproductionDay != null && (
              <div className="flex items-center gap-2">
                <span className="text-[10px] text-[#8A99AF] w-24 shrink-0">Last repro.</span>
                <span className="text-xs text-[#8A99AF]">day {npc.lastReproductionDay.toFixed(1)}</span>
              </div>
            )}
            {npc.animalStats != null && (
              <div className="col-span-2 mt-1">
                <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-1.5">Stats</p>
                <div className="flex flex-wrap gap-x-4 gap-y-1">
                  {(["str", "dex", "intel", "wis", "con", "cha"] as const).map((k) => (
                    <div key={k} className="flex items-center gap-1">
                      <span className="text-[10px] uppercase text-[#8A99AF] w-8">{k}</span>
                      <span className="text-xs font-mono text-white">{npc.animalStats![k]}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export function NpcsPage() {
  const [npcs, setNpcs] = useState<NpcAdminDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [filter, setFilter] = useState("");
  const [filterType, setFilterType] = useState("");
  const [filterGender, setFilterGender] = useState("");
  const [filterAggro, setFilterAggro] = useState("");
  const [filterLevelMin, setFilterLevelMin] = useState("");
  const [filterLevelMax, setFilterLevelMax] = useState("");
  const [attackLines, setAttackLines] = useState<AttackLine[]>([]);
  const npcsRef = useRef<NpcAdminDto[] | null>(null);
  useEffect(() => {
    npcsRef.current = npcs;
  }, [npcs]);

  const load = () => {
    api.npcs
      .list()
      .then(setNpcs)
      .catch((e) => setError(String(e)));
  };

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    const ws = new WebSocket(`${proto}//${location.host}/api/admin/ws/npcs`);
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data as string);
        setNpcs((prev) => {
          if (!prev) return prev;
          switch (msg.type) {
            case "npcUpdate":
              return prev.map((n) =>
                n.id === msg.id
                  ? {
                      ...n,
                      x: msg.x,
                      y: msg.y,
                      z: msg.z,
                      yaw: msg.yaw,
                      currentHp: msg.currentHp,
                      maxHp: msg.maxHp,
                      isDead: msg.isDead,
                    }
                  : n,
              );
            case "npcSpawned":
              if (prev.some((n) => n.id === msg.id)) {
                return prev.map((n) =>
                  n.id === msg.id
                    ? {
                        ...n,
                        x: msg.x,
                        y: msg.y,
                        z: msg.z,
                        yaw: msg.yaw,
                        currentHp: msg.currentHp,
                        maxHp: msg.maxHp,
                        isDead: msg.isDead,
                      }
                    : n,
                );
              }
              return [
                ...prev,
                {
                  id: msg.id,
                  name: msg.name,
                  type: msg.npcType,
                  level: 1,
                  gender: null,
                  currentHp: msg.currentHp,
                  maxHp: msg.maxHp,
                  isDead: msg.isDead,
                  aggroMode: "NEUTRAL",
                  tier: "NORMAL",
                  x: msg.x,
                  y: msg.y,
                  z: msg.z,
                  yaw: msg.yaw,
                  zone: "?",
                  parentIds: [],
                  skills: [],
                  ageGameDays: null,
                  hunger: null,
                  gestationRemainingDays: null,
                  lastReproductionDay: null,
                  motherLevel: null,
                },
              ];
            case "npcDespawned":
              return prev.filter((n) => n.id !== msg.id);
            case "healthUpdate": {
              if (msg.attackerId) {
                const current = npcsRef.current ?? prev;
                const target = current.find((n) => n.id === msg.id);
                const attacker = current.find((n) => n.id === msg.attackerId);
                if (target && attacker) {
                  setAttackLines((lines) => [
                    ...lines.filter((l) => Date.now() - l.ts < ATTACK_LINE_TTL),
                    {
                      id: `${msg.attackerId}-${msg.id}-${Date.now()}`,
                      ax: attacker.x,
                      az: attacker.z,
                      bx: target.x,
                      bz: target.z,
                      ts: Date.now(),
                    },
                  ]);
                }
              }
              return prev.map((n) =>
                n.id === msg.id ? { ...n, currentHp: msg.currentHp, maxHp: msg.maxHp, isDead: msg.isDead } : n,
              );
            }
            default:
              return prev;
          }
        });
      } catch {
        // ignore parse errors
      }
    };
    return () => ws.close();
  }, []);

  const types = useMemo(() => [...new Set((npcs ?? []).map((n) => n.type))].sort(), [npcs]);
  const aggroModes = useMemo(() => [...new Set((npcs ?? []).map((n) => n.aggroMode))].sort(), [npcs]);
  const genders = useMemo(
    () => [...new Set((npcs ?? []).map((n) => n.gender).filter(Boolean) as string[])].sort(),
    [npcs],
  );

  const filtered = (npcs ?? []).filter((n) => {
    if (filter) {
      const q = filter.toLowerCase();
      if (!n.name.toLowerCase().includes(q) && !n.type.toLowerCase().includes(q) && !n.zone.includes(q)) return false;
    }
    if (filterType && n.type !== filterType) return false;
    if (filterGender) {
      if (filterGender === "__NONE__") {
        if (n.gender !== null) return false;
      } else if (n.gender !== filterGender) return false;
    }
    if (filterAggro && n.aggroMode !== filterAggro) return false;
    if (filterLevelMin !== "") {
      const v = parseInt(filterLevelMin);
      if (!isNaN(v) && n.level < v) return false;
    }
    if (filterLevelMax !== "") {
      const v = parseInt(filterLevelMax);
      if (!isNaN(v) && n.level > v) return false;
    }
    return true;
  });

  const alive = (npcs ?? []).filter((n) => !n.isDead).length;

  return (
    <div className="flex gap-4 items-start">
      <div className="flex-1 min-w-0 space-y-4">
        {/* Sticky toolbar */}
        <div className="sticky top-0 z-10 bg-[#111827] pt-1 pb-3 space-y-2">
          {/* Row 1: search + actions */}
          <div className="flex items-center gap-3">
            <input
              type="text"
              placeholder="Name, type or zone…"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              className="flex-1 max-w-xs bg-[#1C2434] border border-[#2E3A4E] rounded-lg px-3 py-1.5 text-sm text-white placeholder-[#8A99AF] outline-none focus:border-[#3C50E0]"
            />
            <button
              onClick={load}
              className="px-3 py-1.5 rounded-lg text-sm font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors shrink-0"
            >
              Refresh
            </button>
            {npcs && (
              <span className="text-xs text-[#8A99AF] shrink-0">
                {filtered.length}/{npcs.length} · {alive} alive
              </span>
            )}
          </div>
          {/* Row 2: facet filters */}
          {npcs && (
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
              {/* Type */}
              <div className="flex items-center gap-1.5">
                <span className="text-[10px] uppercase tracking-widest text-[#8A99AF]">Type</span>
                <select
                  value={filterType}
                  onChange={(e) => setFilterType(e.target.value)}
                  className="bg-[#1C2434] border border-[#2E3A4E] rounded px-2 py-0.5 text-xs text-white outline-none focus:border-[#3C50E0]"
                >
                  <option value="">All</option>
                  {types.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </div>
              {/* Gender */}
              {genders.length > 0 && (
                <div className="flex items-center gap-1">
                  <span className="text-[10px] uppercase tracking-widest text-[#8A99AF] mr-0.5">Gender</span>
                  {[["", "All"], ...genders.map((g) => [g, g]), ["__NONE__", "—"]].map(([val, label]) => (
                    <button
                      key={val}
                      onClick={() => setFilterGender(val === filterGender ? "" : val)}
                      className={`px-2 py-0.5 rounded text-[11px] transition-colors ${filterGender === val ? "bg-[#3C50E0] text-white" : "bg-[#1C2434] text-[#8A99AF] hover:text-white"}`}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              )}
              {/* Level range */}
              <div className="flex items-center gap-1.5">
                <span className="text-[10px] uppercase tracking-widest text-[#8A99AF]">Lv</span>
                <input
                  type="number"
                  min={1}
                  placeholder="min"
                  value={filterLevelMin}
                  onChange={(e) => setFilterLevelMin(e.target.value)}
                  className="w-14 bg-[#1C2434] border border-[#2E3A4E] rounded px-2 py-0.5 text-xs text-white outline-none focus:border-[#3C50E0] [appearance:textfield]"
                />
                <span className="text-[#8A99AF] text-xs">–</span>
                <input
                  type="number"
                  min={1}
                  placeholder="max"
                  value={filterLevelMax}
                  onChange={(e) => setFilterLevelMax(e.target.value)}
                  className="w-14 bg-[#1C2434] border border-[#2E3A4E] rounded px-2 py-0.5 text-xs text-white outline-none focus:border-[#3C50E0] [appearance:textfield]"
                />
              </div>
              {/* Aggro */}
              {aggroModes.length > 0 && (
                <div className="flex items-center gap-1">
                  <span className="text-[10px] uppercase tracking-widest text-[#8A99AF] mr-0.5">Aggro</span>
                  {aggroModes.map((a) => (
                    <button
                      key={a}
                      onClick={() => setFilterAggro(a === filterAggro ? "" : a)}
                      className={`px-2 py-0.5 rounded text-[11px] transition-colors ${filterAggro === a ? "bg-[#3C50E0] text-white" : "bg-[#1C2434] text-[#8A99AF] hover:text-white"}`}
                    >
                      {a.replace("_", " ").replace("_COOPERATIVE", "")}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        {error && <p className="text-red-400 text-sm">{error}</p>}

        {!npcs && !error && <p className="text-[#8A99AF] text-sm">Loading…</p>}

        {npcs && (
          <div className="rounded-xl overflow-hidden border border-[#2E3A4E]">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="bg-[#1C2434] text-[#8A99AF] text-xs uppercase tracking-widest">
                  <th className="text-left px-4 py-3 font-semibold">Name</th>
                  <th className="text-left px-4 py-3 font-semibold">Type</th>
                  <th className="text-left px-4 py-3 font-semibold">Lv</th>
                  <th className="text-left px-4 py-3 font-semibold">Gender</th>
                  <th className="text-left px-4 py-3 font-semibold">Tier</th>
                  <th className="text-left px-4 py-3 font-semibold">Aggro</th>
                  <th className="text-left px-4 py-3 font-semibold">HP</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={8} className="text-center text-[#8A99AF] py-8">
                      No NPCs match
                    </td>
                  </tr>
                )}
                {filtered.map((npc) => {
                  const expanded = expandedId === npc.id;
                  return (
                    <>
                      <tr
                        key={npc.id}
                        onClick={() => setExpandedId(expanded ? null : npc.id)}
                        className={`border-t border-[#2E3A4E] cursor-pointer transition-colors ${
                          npc.isDead ? "opacity-40" : ""
                        } ${expanded ? "bg-[#1C2434]" : "hover:bg-[#1C2434]/60"}`}
                      >
                        <td className="px-4 py-2.5 font-medium text-white">
                          {npc.isDead && <span className="text-red-500 mr-1">✕</span>}
                          {npc.name}
                        </td>
                        <td className="px-4 py-2.5 text-[#8A99AF]">{npc.type}</td>
                        <td className="px-4 py-2.5 text-[#8A99AF]">{npc.level}</td>
                        <td className="px-4 py-2.5 text-[#8A99AF]">{npc.gender ?? "—"}</td>
                        <td className="px-4 py-2.5">
                          <Badge label={npc.tier} color={tierColor(npc.tier)} />
                        </td>
                        <td className="px-4 py-2.5">
                          <Badge label={npc.aggroMode} color={aggroColor(npc.aggroMode)} />
                        </td>
                        <td className="px-4 py-2.5">
                          <HpBar current={npc.currentHp} max={npc.maxHp} />
                        </td>
                        <td className="px-4 py-2.5 text-[#8A99AF] text-right">
                          <span className="text-xs">{expanded ? "▲" : "▼"}</span>
                        </td>
                      </tr>
                      {expanded && (
                        <tr key={npc.id + "-detail"} className="border-t border-[#2E3A4E]">
                          <td colSpan={8} className="p-0">
                            <Detail npc={npc} />
                          </td>
                        </tr>
                      )}
                    </>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <div className="w-80 shrink-0 sticky top-4">
        <NpcMiniMap npcs={npcs ?? []} selectedId={expandedId} attackLines={attackLines} />
      </div>
    </div>
  );
}
