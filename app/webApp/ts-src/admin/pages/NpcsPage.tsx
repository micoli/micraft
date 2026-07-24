import { useEffect, useState } from "react";
import { api, NpcAdminDto } from "../api";

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
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] mb-1">Animal</p>
          <p className="text-xs text-white">Age: {npc.ageGameDays.toFixed(1)} game days</p>
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

  const load = () => {
    api.npcs
      .list()
      .then(setNpcs)
      .catch((e) => setError(String(e)));
  };

  useEffect(() => {
    load();
  }, []);

  const filtered = (npcs ?? []).filter((n) => {
    if (!filter) return true;
    const q = filter.toLowerCase();
    return n.name.toLowerCase().includes(q) || n.type.toLowerCase().includes(q) || n.zone.includes(q);
  });

  const alive = (npcs ?? []).filter((n) => !n.isDead).length;

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex items-center gap-3">
        <input
          type="text"
          placeholder="Filter by name, type or zone…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          className="flex-1 max-w-xs bg-[#1C2434] border border-[#2E3A4E] rounded-lg px-3 py-1.5 text-sm text-white placeholder-[#8A99AF] outline-none focus:border-[#3C50E0]"
        />
        <button
          onClick={load}
          className="px-3 py-1.5 rounded-lg text-sm font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors"
        >
          Refresh
        </button>
        {npcs && (
          <span className="text-xs text-[#8A99AF]">
            {alive} alive · {npcs.length} total
          </span>
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
  );
}
