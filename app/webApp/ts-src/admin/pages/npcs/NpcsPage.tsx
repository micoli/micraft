import { useEffect, useMemo, useRef, useState } from "react";
import { getApiAdminNpcs } from "../../../generated/api/requests";
import { NpcAdminDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { NpcDetail } from "./NpcDetail";
import { Badge } from "../../../primitives/Badge";
import { ATTACK_LINE_TTL, NpcMiniMap } from "./NpcMiniMap";
import { NpcHPBar } from "./NpcHPBar";

export interface PlayerAdminDto {
  id: string;
  name: string;
  x: number;
  y: number;
  z: number;
  yaw: number;
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

export function aggroColor(mode: string) {
  switch (mode) {
    case "AGGRESSIVE":
      return "bg-red-900/40 text-red-400";
    case "PASSIVE_COOPERATIVE":
      return "bg-orange-900/40 text-orange-400";
    default:
      return "bg-[#1C2434] text-[#8A99AF]";
  }
}
export interface AttackLine {
  id: string;
  ax: number;
  az: number;
  bx: number;
  bz: number;
  ts: number;
}

export function NpcsPage() {
  const t = useT();
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
  const [players, setPlayers] = useState<PlayerAdminDto[]>([]);
  const npcsRef = useRef<NpcAdminDto[] | null>(null);
  useEffect(() => {
    npcsRef.current = npcs;
  }, [npcs]);

  const load = () => {
    getApiAdminNpcs({ throwOnError: true })
      .then((r) => setNpcs(r.data))
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
        if (msg.type === "playerJoined") {
          setPlayers((prev) => {
            if (prev.some((p) => p.id === msg.id)) return prev;
            return [...prev, { id: msg.id, name: msg.name, x: msg.x, y: msg.y, z: msg.z, yaw: msg.yaw }];
          });
          return;
        }
        if (msg.type === "playerMoved") {
          setPlayers((prev) =>
            prev.map((p) => (p.id === msg.id ? { ...p, x: msg.x, y: msg.y, z: msg.z, yaw: msg.yaw } : p)),
          );
          return;
        }
        if (msg.type === "playerLeft") {
          setPlayers((prev) => prev.filter((p) => p.id !== msg.id));
          return;
        }
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
                  xp: 0,
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
                  animalStats: null,
                },
              ];
            case "npcDespawned":
              return prev.filter((n) => n.id !== msg.id);
            case "npcXpUpdate":
              return prev.map((n) => (n.id === msg.id ? { ...n, xp: msg.xp, level: msg.level } : n));
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
              placeholder={t("npcs.searchPlaceholder")}
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              className="flex-1 max-w-xs bg-[#1C2434] border border-[#2E3A4E] rounded-lg px-3 py-1.5 text-sm text-white placeholder-[#8A99AF] outline-none focus:border-[#3C50E0]"
            />
            <button
              onClick={load}
              className="px-3 py-1.5 rounded-lg text-sm font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors shrink-0"
            >
              {t("common.refresh")}
            </button>
            {npcs && (
              <span className="text-xs text-[#8A99AF] shrink-0">
                {t("npcs.countSummary", filtered.length, npcs.length, alive)}
              </span>
            )}
          </div>
          {/* Row 2: facet filters */}
          {npcs && (
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
              {/* Type */}
              <div className="flex items-center gap-1.5">
                <span className="text-[10px] uppercase tracking-widest text-[#8A99AF]">{t("npcs.type")}</span>
                <select
                  value={filterType}
                  onChange={(e) => setFilterType(e.target.value)}
                  className="bg-[#1C2434] border border-[#2E3A4E] rounded px-2 py-0.5 text-xs text-white outline-none focus:border-[#3C50E0]"
                >
                  <option value="">{t("npcs.all")}</option>
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
                  <span className="text-[10px] uppercase tracking-widest text-[#8A99AF] mr-0.5">
                    {t("npcs.gender")}
                  </span>
                  {[["", t("npcs.all")], ...genders.map((g) => [g, g]), ["__NONE__", "—"]].map(([val, label]) => (
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
                <span className="text-[10px] uppercase tracking-widest text-[#8A99AF]">{t("npcs.levelShort")}</span>
                <input
                  type="number"
                  min={1}
                  placeholder={t("npcs.min")}
                  value={filterLevelMin}
                  onChange={(e) => setFilterLevelMin(e.target.value)}
                  className="w-14 bg-[#1C2434] border border-[#2E3A4E] rounded px-2 py-0.5 text-xs text-white outline-none focus:border-[#3C50E0] [appearance:textfield]"
                />
                <span className="text-[#8A99AF] text-xs">–</span>
                <input
                  type="number"
                  min={1}
                  placeholder={t("npcs.max")}
                  value={filterLevelMax}
                  onChange={(e) => setFilterLevelMax(e.target.value)}
                  className="w-14 bg-[#1C2434] border border-[#2E3A4E] rounded px-2 py-0.5 text-xs text-white outline-none focus:border-[#3C50E0] [appearance:textfield]"
                />
              </div>
              {/* Aggro */}
              {aggroModes.length > 0 && (
                <div className="flex items-center gap-1">
                  <span className="text-[10px] uppercase tracking-widest text-[#8A99AF] mr-0.5">{t("npcs.aggro")}</span>
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

        {!npcs && !error && <p className="text-[#8A99AF] text-sm">{t("common.loading")}</p>}

        {npcs && (
          <div className="rounded-xl overflow-hidden border border-[#2E3A4E]">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="bg-[#1C2434] text-[#8A99AF] text-xs uppercase tracking-widest">
                  <th className="text-left px-4 py-3 font-semibold">{t("npcs.colName")}</th>
                  <th className="text-left px-4 py-3 font-semibold">{t("npcs.type")}</th>
                  <th className="text-left px-4 py-3 font-semibold">{t("npcs.levelShort")}</th>
                  <th className="text-left px-4 py-3 font-semibold">{t("npcs.colXp")}</th>
                  <th className="text-left px-4 py-3 font-semibold">{t("npcs.gender")}</th>
                  <th className="text-left px-4 py-3 font-semibold">{t("npcs.colTier")}</th>
                  <th className="text-left px-4 py-3 font-semibold">{t("npcs.aggro")}</th>
                  <th className="text-left px-4 py-3 font-semibold">{t("npcs.colHp")}</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={9} className="text-center text-[#8A99AF] py-8">
                      {t("npcs.noMatch")}
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
                        <td className="px-4 py-2.5 text-[#8A99AF] font-mono">{npc.xp}</td>
                        <td className="px-4 py-2.5 text-[#8A99AF]">{npc.gender ?? "—"}</td>
                        <td className="px-4 py-2.5">
                          <Badge color={tierColor(npc.tier)}>{npc.tier}</Badge>
                        </td>
                        <td className="px-4 py-2.5">
                          <Badge color={aggroColor(npc.aggroMode)}>{npc.aggroMode}</Badge>
                        </td>
                        <td className="px-4 py-2.5">
                          <NpcHPBar current={npc.currentHp} max={npc.maxHp} />
                        </td>
                        <td className="px-4 py-2.5 text-[#8A99AF] text-right">
                          <span className="text-xs">{expanded ? "▲" : "▼"}</span>
                        </td>
                      </tr>
                      {expanded && (
                        <tr key={npc.id + "-detail"} className="border-t border-[#2E3A4E]">
                          <td colSpan={9} className="p-0">
                            <NpcDetail npc={npc} t={t} />
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
        <NpcMiniMap npcs={npcs ?? []} players={players} selectedId={expandedId} attackLines={attackLines} />
      </div>
    </div>
  );
}
