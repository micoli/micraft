import { useEffect, useState } from "react";
import { api, StatusSnapshot } from "../../api";
import { useT, type TranslationKey } from "../../i18n";
import { GameTimeSetter } from "./GameTimeSetter";
import { pad2 } from "./utils";
import { StatCard } from "./StatCard";
import { HeapBar } from "./HeapBar";
import { Card } from "./Card";
import { Row } from "./Row";
import { Svg } from "./Svg";

// ── Helpers ───────────────────────────────────────────────────────────────────
function kb(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1_048_576) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1_048_576).toFixed(1)} MB`;
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

export function ticksToTime(ticks: number, ticksPerDay: number): { h: number; m: number } {
  const day = ticks % ticksPerDay;
  const h = Math.floor((day * 24) / ticksPerDay);
  const m = Math.floor(((day * 24 * 60) / ticksPerDay) % 60);
  return { h, m };
}

// ── Page ─────────────────────────────────────────────────────────────────────
export function StatusPage() {
  const t = useT();
  const [snap, setSnap] = useState<StatusSnapshot | null>(null);
  const [restarting, setRestarting] = useState(false);
  const [errorKey, setErrorKey] = useState<TranslationKey | null>(null);

  useEffect(() => {
    let alive = true;
    const poll = async () => {
      try {
        const s = await api.status.get();
        if (alive) {
          setSnap(s);
          setErrorKey(null);
        }
      } catch {
        if (alive) setErrorKey("status.unreachable");
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
    if (!confirm(t("status.confirmRestart"))) return;
    setRestarting(true);
    try {
      await api.status.restart();
    } catch {
      /* empty */
    }
    setTimeout(() => setRestarting(false), 4000);
  };

  if (errorKey) return <p className="text-red-400 text-sm">{t(errorKey)}</p>;
  if (!snap) return <p className="text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</p>;

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
          label={t("status.connectedPlayers")}
          value={snap.connectedPlayers}
          sub={snap.playerNames.join(", ") || t("status.noPlayers")}
          icon={I.users}
          color="bg-blue-500/20 text-blue-400"
        />
        <StatCard
          label={t("status.npcsAlive")}
          value={snap.npcTotal}
          sub={t("status.estimated", kb(snap.npcEstBytes))}
          icon={I.npc}
          color="bg-violet-500/20 text-violet-400"
        />
        <StatCard
          label={t("status.loadedChunks")}
          value={snap.loadedChunks}
          icon={I.chunk}
          color="bg-emerald-500/20 text-emerald-400"
        />
        <StatCard
          label={t("status.gameTime")}
          value={`${pad2(h)}:${pad2(m)}`}
          sub={t("status.tickValue", snap.gameTicks.toLocaleString())}
          icon={I.tick}
          color="bg-amber-500/20 text-amber-400"
        />
      </div>

      {/* Middle row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* NPC breakdown */}
        <Card title={t("status.npcsByType")}>
          {topNpcs.length === 0 ? (
            <p className="text-[#8A99AF] text-sm">{t("status.noneCapitalised")}</p>
          ) : (
            topNpcs.map(([type, count]) => <Row key={type} label={type} value={count} />)
          )}
        </Card>

        {/* World */}
        <Card title={t("status.world")}>
          <Row label={t("status.groundItems")} value={snap.worldItems} />
          <Row label={t("status.activeLiquids")} value={snap.activeLiquids} />
          <Row label={t("status.pendingLiquidTicks")} value={snap.pendingLiquidTicks} />
          <Row label={t("status.growingVegetation")} value={snap.activeVegetation} />
          <div className="pt-3">
            <p className="text-[10px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-1">
              {t("status.setGameTime")}
            </p>
            <GameTimeSetter snap={snap} t={t} />
          </div>
        </Card>

        {/* Network */}
        <Card title={t("status.network")}>
          <Row label={t("status.received")} value={kb(snap.networkBytesIn)} />
          <Row label={t("status.sent")} value={kb(snap.networkBytesOut)} />
        </Card>

        {/* Processor */}
        <Card title={t("status.processor")}>
          <HeapBar used={snap.heapUsedMb} max={snap.heapMaxMb} label={t("status.heap")} />
          <div className="mt-3 space-y-0.5">
            <Row label={t("status.nonHeap")} value={`${snap.nonHeapUsedMb} MB`} />
            <Row label={t("status.processors")} value={snap.processors} />
          </div>
        </Card>
      </div>

      {/* Footer row */}
      <div className="flex items-center justify-between text-[11px] text-[#8A99AF]">
        <span>{t("status.autoRefresh")}</span>
        <button
          onClick={restart}
          disabled={restarting}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2E3A4E] hover:bg-[#2E3A4E] text-[#8A99AF] hover:text-white transition-colors disabled:opacity-50"
        >
          <Svg d={I.restart} size={13} />
          {restarting ? t("status.restarting") : t("status.restartServer")}
        </button>
      </div>
    </div>
  );
}
