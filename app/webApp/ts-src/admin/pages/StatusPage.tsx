import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { api, StatusSnapshot } from "../api";

// ── Helpers ───────────────────────────────────────────────────────────────────
function kb(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1_048_576) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1_048_576).toFixed(1)} MB`;
}

// ── Icons ─────────────────────────────────────────────────────────────────────
function Svg({ d, size = 22 }: { d: string; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={d} />
    </svg>
  );
}

// ── Stat card ────────────────────────────────────────────────────────────────
function StatCard({
  label,
  value,
  sub,
  icon,
  color,
}: {
  label: string;
  value: string | number;
  sub?: string;
  icon: string;
  color: string; // tailwind bg class
}) {
  return (
    <div className="bg-[#1A222C] rounded-xl border border-[#2E3A4E] p-5 flex items-start justify-between">
      <div>
        <p className="text-[11px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-1">{label}</p>
        <p className="text-3xl font-bold text-white tabular-nums leading-none">{value}</p>
        {sub && <p className="text-xs text-[#8A99AF] mt-1.5">{sub}</p>}
      </div>
      <div className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${color}`}>
        <Svg d={icon} size={20} />
      </div>
    </div>
  );
}

// ── Section card ─────────────────────────────────────────────────────────────
function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-[#1A222C] rounded-xl border border-[#2E3A4E] p-5">
      <h3 className="text-[11px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-4">{title}</h3>
      {children}
    </div>
  );
}

function Row({ label, value, accent }: { label: string; value: string | number; accent?: boolean }) {
  return (
    <div className="flex justify-between items-center py-1.5 border-b border-[#2E3A4E] last:border-0 text-sm">
      <span className="text-[#8A99AF]">{label}</span>
      <span className={accent ? "text-[#3C50E0] font-semibold" : "text-white tabular-nums"}>{value}</span>
    </div>
  );
}

// ── Heap bar ──────────────────────────────────────────────────────────────────
function HeapBar({ used, max }: { used: number; max: number }) {
  const pct = max > 0 ? Math.round((used / max) * 100) : 0;
  const color = pct > 85 ? "bg-red-500" : pct > 65 ? "bg-amber-400" : "bg-[#3C50E0]";
  return (
    <div>
      <div className="flex justify-between text-xs text-[#8A99AF] mb-2">
        <span>Heap</span>
        <span>
          {used} MB / {max} MB ({pct}%)
        </span>
      </div>
      <div className="w-full bg-[#2E3A4E] rounded-full h-2">
        <div className={`h-2 rounded-full transition-all ${color}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

// ── Icon paths ────────────────────────────────────────────────────────────────
const I = {
  users: "M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zm8 2l2 2 4-4",
  npc: "M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18",
  chunk: "M3 7l9-4 9 4M3 7v10l9 4m-9-14l9 4m9-4v10l-9 4m0-14v14",
  tick: "M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z",
  net: "M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4",
  cpu: "M9 3H5a2 2 0 00-2 2v4m0 0h18M3 9v10a2 2 0 002 2h4m0 0h6m0 0h4a2 2 0 002-2V9",
  drop: "M14.5 10c-.83 0-1.5-.67-1.5-1.5v-5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5v5c0 .83-.67 1.5-1.5 1.5zm2.5 4.5c0 .83-.67 1.5-1.5 1.5S14 15.33 14 14.5V12h-1v2.5c0 .83-.67 1.5-1.5 1.5S10 15.33 10 14.5v-5C10 8.67 10.67 8 11.5 8S13 8.67 13 9.5V11h1V9.5C14 8.67 14.67 8 15.5 8S17 8.67 17 9.5v5z",
  restart:
    "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15",
};

function ticksToTime(ticks: number, ticksPerDay: number): { h: number; m: number } {
  const day = ticks % ticksPerDay;
  const h = Math.floor((day * 24) / ticksPerDay);
  const m = Math.floor(((day * 24 * 60) / ticksPerDay) % 60);
  return { h, m };
}

function pad2(n: number) {
  return String(n).padStart(2, "0");
}

// ── Game Time setter ──────────────────────────────────────────────────────────
function GameTimeSetter({ snap }: { snap: StatusSnapshot }) {
  const { h, m } = ticksToTime(snap.gameTicks, snap.ticksPerDay || 72000);
  const [time, setTime] = useState(`${pad2(h)}:${pad2(m)}`);
  const [saving, setSaving] = useState(false);
  const prevRef = useRef(`${pad2(h)}:${pad2(m)}`);

  const newHM = ticksToTime(snap.gameTicks, snap.ticksPerDay || 72000);
  const newStr = `${pad2(newHM.h)}:${pad2(newHM.m)}`;
  useLayoutEffect(() => {
    if (newStr !== prevRef.current) {
      prevRef.current = newStr;
      setTime(newStr);
    }
  });

  const save = async () => {
    const [hh, mm] = time.split(":").map(Number);
    if (isNaN(hh) || isNaN(mm)) return;
    setSaving(true);
    try {
      await api.status.setGameTime(hh, mm);
    } catch { /* empty */ }
    setSaving(false);
  };

  return (
    <div className="flex items-center gap-2 mt-2">
      <input
        type="time"
        value={time}
        onChange={(e) => setTime(e.target.value)}
        className="bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-2 py-1 text-sm text-white focus:outline-none focus:border-[#3C50E0] transition-colors tabular-nums"
      />
      <button
        onClick={save}
        disabled={saving}
        className="px-3 py-1 rounded-lg text-xs font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors disabled:opacity-50"
      >
        {saving ? "…" : "Set"}
      </button>
    </div>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────
export function StatusPage() {
  const [snap, setSnap] = useState<StatusSnapshot | null>(null);
  const [restarting, setRestarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    const poll = async () => {
      try {
        const s = await api.status.get();
        if (alive) {
          setSnap(s);
          setError(null);
        }
      } catch {
        if (alive) setError("Server unreachable");
      }
    };
    poll();
    const id = setInterval(poll, 5000);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, []);

  const restart = async () => {
    if (!confirm("Restart server?")) return;
    setRestarting(true);
    try {
      await api.status.restart();
    } catch { /* empty */ }
    setTimeout(() => setRestarting(false), 4000);
  };

  if (error) return <p className="text-red-400 text-sm">{error}</p>;
  if (!snap) return <p className="text-[#8A99AF] text-sm animate-pulse">Loading…</p>;

  const topNpcs = Object.entries(snap.npcByType)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6);
  const tpd = snap.ticksPerDay || 72000;
  const { h, m } = ticksToTime(snap.gameTicks, tpd);

  return (
    <div className="space-y-6">
      {/* Top stat row */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          label="Connected players"
          value={snap.connectedPlayers}
          sub={snap.playerNames.join(", ") || "none"}
          icon={I.users}
          color="bg-blue-500/20 text-blue-400"
        />
        <StatCard
          label="NPCs alive"
          value={snap.npcTotal}
          sub={`est. ${kb(snap.npcEstBytes)}`}
          icon={I.npc}
          color="bg-violet-500/20 text-violet-400"
        />
        <StatCard
          label="Loaded chunks"
          value={snap.loadedChunks}
          icon={I.chunk}
          color="bg-emerald-500/20 text-emerald-400"
        />
        <StatCard
          label="Game time"
          value={`${pad2(h)}:${pad2(m)}`}
          sub={`tick ${snap.gameTicks.toLocaleString()}`}
          icon={I.tick}
          color="bg-amber-500/20 text-amber-400"
        />
      </div>

      {/* Middle row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* NPC breakdown */}
        <Card title="NPCs by type">
          {topNpcs.length === 0 ? (
            <p className="text-[#8A99AF] text-sm">None</p>
          ) : (
            topNpcs.map(([type, count]) => <Row key={type} label={type} value={count} />)
          )}
        </Card>

        {/* World */}
        <Card title="World">
          <Row label="Ground items" value={snap.worldItems} />
          <Row label="Active liquids" value={snap.activeLiquids} />
          <Row label="Pending liquid ticks" value={snap.pendingLiquidTicks} />
          <Row label="Growing vegetation" value={snap.activeVegetation} />
          <div className="pt-3">
            <p className="text-[10px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-1">Set game time</p>
            <GameTimeSetter snap={snap} />
          </div>
        </Card>

        {/* Network */}
        <Card title="Network">
          <Row label="↓ Received" value={kb(snap.networkBytesIn)} />
          <Row label="↑ Sent" value={kb(snap.networkBytesOut)} />
          <div className="mt-4">
            <HeapBar used={snap.heapUsedMb} max={snap.heapMaxMb} />
            <div className="mt-3 space-y-0.5">
              <Row label="Non-heap" value={`${snap.nonHeapUsedMb} MB`} />
              <Row label="Processors" value={snap.processors} />
            </div>
          </div>
        </Card>
      </div>

      {/* Footer row */}
      <div className="flex items-center justify-between text-[11px] text-[#8A99AF]">
        <span>Auto-refreshes every 5 s</span>
        <button
          onClick={restart}
          disabled={restarting}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2E3A4E] hover:bg-[#2E3A4E] text-[#8A99AF] hover:text-white transition-colors disabled:opacity-50"
        >
          <Svg d={I.restart} size={13} />
          {restarting ? "Restarting…" : "Restart server"}
        </button>
      </div>
    </div>
  );
}
